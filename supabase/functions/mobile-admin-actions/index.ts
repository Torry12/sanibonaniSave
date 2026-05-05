import { serve } from "https://deno.land/std@0.224.0/http/server.ts"
import { createClient } from "https://esm.sh/@supabase/supabase-js@2"

const SUPABASE_URL = Deno.env.get("SUPABASE_URL") ?? ""
const SUPABASE_ANON_KEY = Deno.env.get("SUPABASE_ANON_KEY") ?? ""
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? ""
const PLATFORM_ADMIN_EMAIL = (Deno.env.get("PLATFORM_ADMIN_EMAIL") ?? "torrymsimango@gmail.com").trim().toLowerCase()

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

serve(async (req) => {
  if (req.method === "OPTIONS") return json({ ok: true })

  try {
    const authHeader = req.headers.get("Authorization") ?? ""
    if (!authHeader.startsWith("Bearer ")) {
      return json({ error: "Missing bearer token." }, 401)
    }

    const userClient = createClient(SUPABASE_URL, SUPABASE_ANON_KEY, {
      global: { headers: { Authorization: authHeader } },
      auth: { persistSession: false, autoRefreshToken: false },
    })
    const serviceClient = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY, {
      auth: { persistSession: false, autoRefreshToken: false },
    })

    const { data: authData, error: authError } = await userClient.auth.getUser()
    if (authError || !authData.user) {
      return json({ error: "Your session is no longer valid. Please sign in again." }, 401)
    }

    const caller = authData.user
    const callerEmail = caller.email?.trim().toLowerCase() ?? ""
    const payload = await req.json()
    const action = payload?.action as string | undefined

    switch (action) {
      case "record_login": {
        const targetUserId = payload?.user_id as string | undefined
        if (!targetUserId || targetUserId !== caller.id) {
          return json({ error: "You can only update your own login audit details." }, 403)
        }

        const existingMeta = caller.user_metadata ?? {}
        const { error } = await serviceClient.auth.admin.updateUserById(targetUserId, {
          user_metadata: {
            ...existingMeta,
            last_login_at: payload?.last_login_at ?? new Date().toISOString(),
            login_attempts: 0,
          },
        })
        if (error) throw error
        return json({ ok: true })
      }

      case "update_user_role": {
        const targetUserId = payload?.user_id as string | undefined
        const role = payload?.role as string | undefined
        const groupId = payload?.group_id as string | undefined

        if (!targetUserId || !role) {
          return json({ error: "user_id and role are required." }, 400)
        }

        if (targetUserId !== caller.id && callerEmail != PLATFORM_ADMIN_EMAIL) {
          return json({ error: "Only the canonical platform admin can update another user's role." }, 403)
        }

        const { data: targetUserData, error: targetUserError } = await serviceClient.auth.admin.getUserById(targetUserId)
        if (targetUserError || !targetUserData.user) {
          return json({ error: "Target user was not found." }, 404)
        }

        const existingMeta = targetUserData.user.user_metadata ?? {}
        const existingGroups = Array.isArray(existingMeta.admin_for_groups) ? existingMeta.admin_for_groups : []
        const adminForGroups = role === "group_admin" && groupId
          ? Array.from(new Set([...(existingGroups as string[]), groupId]))
          : existingGroups

        const { error } = await serviceClient.auth.admin.updateUserById(targetUserId, {
          user_metadata: {
            ...existingMeta,
            role,
            admin_for_groups: adminForGroups,
          },
        })
        if (error) throw error
        return json({ ok: true })
      }

      case "admin_sign_up": {
        if (callerEmail != PLATFORM_ADMIN_EMAIL) {
          return json({ error: "Only the canonical platform admin can provision admin users." }, 403)
        }

        const email = (payload?.email as string | undefined)?.trim()
        const password = (payload?.password as string | undefined)?.trim()
        const metadata = (payload?.metadata ?? {}) as Record<string, unknown>
        const confirm = Boolean(payload?.confirm ?? true)

        if (!email || !password) {
          return json({ error: "email and password are required." }, 400)
        }

        const { data, error } = await serviceClient.auth.admin.createUser({
          email,
          password,
          email_confirm: confirm,
          user_metadata: metadata,
        })
        if (error) throw error
        return json({ user_id: data.user?.id ?? null })
      }

      default:
        return json({ error: "Unsupported action." }, 400)
    }
  } catch (error) {
    console.error("mobile-admin-actions error", error)
    return json({ error: error instanceof Error ? error.message : "Unknown server error." }, 500)
  }
})
