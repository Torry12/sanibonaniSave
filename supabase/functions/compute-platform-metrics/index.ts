import { serve } from "https://deno.land/std@0.224.0/http/server.ts"
import { createClient } from "https://esm.sh/@supabase/supabase-js@2"

const SUPABASE_URL = Deno.env.get("SUPABASE_URL") ?? ""
const SUPABASE_ANON_KEY = Deno.env.get("SUPABASE_ANON_KEY") ?? ""
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? ""

const PAGE_SIZE = 1000
const UPSERT_CHUNK = 500
const MAX_TRACK_ROWS = 200_000

const TRACK_COLUMNS = [
  "group_id",
  "behavior_score",
  "fraud_score",
  "fraud_risk_level",
  "member_status",
  "is_suspended",
  "is_flagged_for_review",
  "total_contributions",
  "on_time_contributions",
  "total_loans_approved",
  "loan_default_count",
].join(", ")

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "Content-Type": "application/json",
      "Access-Control-Allow-Origin": "*",
      "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
    },
  })
}

type AdminClient = ReturnType<typeof newServiceClient>

function newServiceClient() {
  return createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY, {
    auth: { persistSession: false, autoRefreshToken: false },
  })
}

async function requirePlatformAdmin(
  req: Request,
): Promise<{ user: { id: string; email?: string }; client: AdminClient } | { response: Response }> {
  const authHeader = req.headers.get("Authorization") ?? ""
  if (!authHeader.startsWith("Bearer ")) {
    return { response: json({ error: "Missing bearer token." }, 401) }
  }

  const userClient = createClient(SUPABASE_URL, SUPABASE_ANON_KEY, {
    global: { headers: { Authorization: authHeader } },
    auth: { persistSession: false, autoRefreshToken: false },
  })
  const { data: authData, error: authError } = await userClient.auth.getUser()
  if (authError || !authData.user) {
    return { response: json({ error: "Your session is no longer valid. Please sign in again." }, 401) }
  }

  const serviceClient = newServiceClient()

  // Same rule as public.is_platform_admin(): the JWT role claim or the profiles row.
  const claimRole = (authData.user.app_metadata ?? {}).role
  let isAdmin = claimRole === "platform_admin"
  if (!isAdmin) {
    const { data: profile } = await serviceClient
      .from("profiles")
      .select("role")
      .eq("id", authData.user.id)
      .maybeSingle()
    isAdmin = profile?.role === "platform_admin"
  }
  if (!isAdmin) {
    return { response: json({ error: "Only a platform admin can recompute platform metrics." }, 403) }
  }

  return { user: { id: authData.user.id, email: authData.user.email ?? undefined }, client: serviceClient }
}

interface TrackRow {
  group_id: string
  behavior_score: number | string | null
  fraud_score: number | string | null
  fraud_risk_level: string | null
  member_status: string | null
  is_suspended: boolean | null
  is_flagged_for_review: boolean | null
  total_contributions: number | null
  on_time_contributions: number | null
  total_loans_approved: number | null
  loan_default_count: number | null
}

const number = (value: number | string | null | undefined): number => {
  if (value === null || value === undefined) return 0
  const parsed = typeof value === "number" ? value : Number(value)
  return Number.isFinite(parsed) ? parsed : 0
}

const round2 = (value: number): number => Math.round(value * 100) / 100

// Mirrors BehaviorScoringUtils.determineBehaviorStatus so the summary buckets agree
// with what the app shows for the same scores.
function behaviorStatus(score: number, fraudScore: number, isSuspended: boolean): string {
  if (isSuspended) return "suspended"
  if (score >= 85 && fraudScore <= 20) return "excellent"
  if (score >= 70 && fraudScore <= 35) return "good"
  if (score >= 50 && fraudScore <= 50) return "fair"
  return "poor"
}

interface GroupTally {
  total: number
  excellent: number
  good: number
  fair: number
  poor: number
  suspended: number
  highFraud: number
  flagged: number
  scoreSum: number
  fraudSum: number
  contributions: number
  onTime: number
  approved: number
  defaults: number
}

function emptyTally(): GroupTally {
  return {
    total: 0,
    excellent: 0,
    good: 0,
    fair: 0,
    poor: 0,
    suspended: 0,
    highFraud: 0,
    flagged: 0,
    scoreSum: 0,
    fraudSum: 0,
    contributions: 0,
    onTime: 0,
    approved: 0,
    defaults: 0,
  }
}

async function readTracks(client: AdminClient): Promise<TrackRow[]> {
  const rows: TrackRow[] = []
  for (let from = 0; ; from += PAGE_SIZE) {
    const { data, error } = await client
      .from("member_behavior_track")
      .select(TRACK_COLUMNS)
      .order("member_id", { ascending: true })
      .range(from, from + PAGE_SIZE - 1)
    if (error) throw new Error(`Failed to read member_behavior_track: ${error.message}`)
    if (!data || data.length === 0) break
    rows.push(...(data as unknown as TrackRow[]))
    if (data.length < PAGE_SIZE) break
    if (rows.length >= MAX_TRACK_ROWS) {
      throw new Error(`member_behavior_track exceeds ${MAX_TRACK_ROWS} rows; split the recompute.`)
    }
  }
  return rows
}

serve(async (req) => {
  if (req.method === "OPTIONS") return json({ ok: true })

  try {
    const auth = await requirePlatformAdmin(req)
    if ("response" in auth) return auth.response

    const startedAt = Date.now()
    const tracks = await readTracks(auth.client)

    const tallies = new Map<string, GroupTally>()
    for (const track of tracks) {
      const groupId = track.group_id
      if (!groupId) continue
      const tally = tallies.get(groupId) ?? emptyTally()

      const score = number(track.behavior_score)
      const fraudScore = number(track.fraud_score)
      const status = behaviorStatus(score, fraudScore, track.is_suspended === true)

      tally.total += 1
      if (status === "excellent") tally.excellent += 1
      else if (status === "good") tally.good += 1
      else if (status === "fair") tally.fair += 1
      else if (status === "poor") tally.poor += 1
      else tally.suspended += 1

      const risk = (track.fraud_risk_level ?? "").toLowerCase()
      if (risk === "high" || risk === "critical") tally.highFraud += 1
      if (track.is_flagged_for_review === true) tally.flagged += 1

      tally.scoreSum += score
      tally.fraudSum += fraudScore
      tally.contributions += number(track.total_contributions)
      tally.onTime += number(track.on_time_contributions)
      tally.approved += number(track.total_loans_approved)
      tally.defaults += number(track.loan_default_count)

      tallies.set(groupId, tally)
    }

    const calculatedAt = new Date().toISOString()
    const summaries = [...tallies.entries()].map(([groupId, tally]) => ({
      group_id: groupId,
      total_members_tracked: tally.total,
      excellent_members: tally.excellent,
      good_members: tally.good,
      fair_members: tally.fair,
      poor_members: tally.poor,
      suspended_members: tally.suspended,
      high_fraud_risk_count: tally.highFraud,
      flagged_members_count: tally.flagged,
      average_behavior_score: round2(tally.scoreSum / tally.total),
      average_fraud_score: round2(tally.fraudSum / tally.total),
      on_time_payment_rate: tally.contributions > 0 ? round2((tally.onTime / tally.contributions) * 100) : 0,
      loan_default_rate: tally.approved > 0 ? round2((tally.defaults / tally.approved) * 100) : 0,
      calculated_at: calculatedAt,
    }))

    for (let index = 0; index < summaries.length; index += UPSERT_CHUNK) {
      const chunk = summaries.slice(index, index + UPSERT_CHUNK)
      const { error } = await auth.client
        .from("behavior_analytics_summary")
        .upsert(chunk, { onConflict: "group_id" })
      if (error) throw new Error(`Failed to write behavior_analytics_summary: ${error.message}`)
    }

    return json({
      ok: true,
      groups: summaries.length,
      members: tracks.length,
      calculated_at: calculatedAt,
      duration_ms: Date.now() - startedAt,
    })
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error)
    console.error("compute-platform-metrics failed:", message)
    return json({ error: message }, 500)
  }
})
