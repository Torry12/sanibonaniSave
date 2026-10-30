import { serve } from "https://deno.land/std@0.224.0/http/server.ts"
import { createClient } from "https://esm.sh/@supabase/supabase-js@2"

const SUPABASE_URL = Deno.env.get("SUPABASE_URL") ?? ""
const SUPABASE_ANON_KEY = Deno.env.get("SUPABASE_ANON_KEY") ?? ""
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? ""

// The Android app lists and deletes objects in this bucket directly, so the dump has
// to land at the root of the bucket as one object.
const BUCKET = "backups"
const PAGE_SIZE = 1000
const MAX_ROWS_PER_TABLE = 250_000

// `debug_log` and `simulation_results` are regenerable diagnostics and would
// dominate the payload, so a backup covers the business tables only.
// The order columns are the primary keys: pagination without a stable order can
// silently skip rows.
const TABLES: Array<{ name: string; order: string[] }> = [
  { name: "profiles", order: ["id"] },
  { name: "financial_providers", order: ["id"] },
  { name: "groups", order: ["id"] },
  { name: "members", order: ["id"] },
  { name: "member_documents", order: ["id"] },
  { name: "contributions", order: ["id"] },
  { name: "payments", order: ["id"] },
  { name: "loans", order: ["id"] },
  { name: "loan_repayments", order: ["id"] },
  { name: "beneficiaries", order: ["id"] },
  { name: "beneficiary_payout_claims", order: ["id"] },
  { name: "payouts", order: ["id"] },
  { name: "rosca_rotation_executions", order: ["group_id", "member_id", "period"] },
  { name: "group_ledger", order: ["id"] },
  { name: "ledger_checkpoints", order: ["id"] },
  { name: "platform_ledger", order: ["id"] },
  { name: "platform_settings", order: ["key"] },
  { name: "platform_fees", order: ["id"] },
  { name: "platform_fee_config", order: ["group_type", "rosca_cycle"] },
  { name: "bank_transactions", order: ["id"] },
  { name: "bank_connections", order: ["id"] },
  { name: "group_polls", order: ["id"] },
  { name: "group_poll_options", order: ["id"] },
  { name: "group_poll_votes", order: ["id"] },
  { name: "settlements", order: ["id"] },
  { name: "reconciliations", order: ["id"] },
  { name: "group_actuarial_metrics", order: ["group_id"] },
  { name: "group_health_scores", order: ["group_id"] },
  { name: "member_behavior_track", order: ["member_id"] },
  { name: "fraud_detection_events", order: ["id"] },
  { name: "behavior_analytics_summary", order: ["group_id"] },
  { name: "platform_audit_reports", order: ["id"] },
  { name: "audit_reports", order: ["id"] },
  { name: "pending_operations", order: ["id"] },
  { name: "notifications", order: ["id"] },
  { name: "audit_logs", order: ["id"] },
]

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
    return { response: json({ error: "Only a platform admin can trigger a cloud backup." }, 403) }
  }

  return { user: { id: authData.user.id, email: authData.user.email ?? undefined }, client: serviceClient }
}

async function dumpTable(client: AdminClient, table: { name: string; order: string[] }) {
  const rows: unknown[] = []
  for (let from = 0; ; from += PAGE_SIZE) {
    let query = client.from(table.name).select("*")
    for (const column of table.order) query = query.order(column, { ascending: true })
    const { data, error } = await query.range(from, from + PAGE_SIZE - 1)
    if (error) throw new Error(`Failed to read ${table.name}: ${error.message}`)
    if (!data || data.length === 0) break
    rows.push(...data)
    if (data.length < PAGE_SIZE) break
    if (rows.length >= MAX_ROWS_PER_TABLE) {
      throw new Error(`${table.name} exceeds ${MAX_ROWS_PER_TABLE} rows; narrow the backup scope.`)
    }
  }
  return rows
}

// The storage bucket is capped at 50 MiB locally, and a raw JSON dump of the money
// tables clears that quickly, so the object is gzipped.
async function gzip(text: string): Promise<Uint8Array> {
  const compressed = new Response(text).body!.pipeThrough(new CompressionStream("gzip"))
  return new Uint8Array(await new Response(compressed).arrayBuffer())
}

serve(async (req) => {
  if (req.method === "OPTIONS") return json({ ok: true })

  try {
    const auth = await requirePlatformAdmin(req)
    if ("response" in auth) return auth.response

    const startedAt = Date.now()
    const tables: Record<string, unknown[]> = {}
    const rowCounts: Record<string, number> = {}
    for (const table of TABLES) {
      const rows = await dumpTable(auth.client, table)
      tables[table.name] = rows
      rowCounts[table.name] = rows.length
    }

    const document = {
      format: "sanibonani-save/backup-v1",
      generated_at: new Date().toISOString(),
      generated_by: auth.user.email ?? auth.user.id,
      row_counts: rowCounts,
      tables,
    }
    const serialized = JSON.stringify(document)
    const payload = await gzip(serialized)

    const objectName = `backup_${new Date().toISOString().replace(/[:.]/g, "-")}.json.gz`
    const { error: uploadError } = await auth.client.storage
      .from(BUCKET)
      .upload(objectName, payload, { contentType: "application/gzip", upsert: false })
    if (uploadError) throw new Error(`Upload to ${BUCKET}/${objectName} failed: ${uploadError.message}`)

    return json({
      ok: true,
      object: objectName,
      bytes: payload.byteLength,
      uncompressed_bytes: new TextEncoder().encode(serialized).byteLength,
      row_counts: rowCounts,
      duration_ms: Date.now() - startedAt,
    })
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error)
    console.error("trigger-backup failed:", message)
    return json({ error: message }, 500)
  }
})
