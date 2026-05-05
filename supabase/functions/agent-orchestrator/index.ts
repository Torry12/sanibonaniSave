import { serve } from "https://deno.land/std@0.224.0/http/server.ts"
import { createClient } from "https://esm.sh/@supabase/supabase-js@2"

const SUPABASE_URL = Deno.env.get("SUPABASE_URL") ?? ""
const SUPABASE_ANON_KEY = Deno.env.get("SUPABASE_ANON_KEY") ?? ""
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? ""

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

async function getTask(serviceClient: ReturnType<typeof createClient>, taskId: string) {
  const { data, error } = await serviceClient
    .from("agent_tasks")
    .select("id, requester_id, task_type, payload, status, output, created_at, updated_at")
    .eq("id", taskId)
    .maybeSingle()

  if (error) throw error
  return data
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
    const payload = await req.json()
    const action = payload?.action as string | undefined
    const taskId = (payload?.task_id as string | undefined)?.trim()

    switch (action) {
      case "submit": {
        const submittedTaskId = taskId || crypto.randomUUID()
        const taskType = (payload?.type as string | undefined)?.trim() || "generic"
        const taskPayload = (payload?.payload as string | undefined)?.trim() || ""

        const { error } = await serviceClient
          .from("agent_tasks")
          .upsert({
            id: submittedTaskId,
            requester_id: caller.id,
            task_type: taskType,
            payload: { raw: taskPayload },
            status: "queued",
            output: null,
          }, { onConflict: "id" })

        if (error) throw error

        const task = await getTask(serviceClient, submittedTaskId)
        return json({
          task_id: task?.id ?? submittedTaskId,
          status: task?.status ?? "queued",
          output: task?.output ?? "",
          created_at: task?.created_at ?? new Date().toISOString(),
        })
      }

      case "status":
      case "result": {
        if (!taskId) return json({ error: "task_id is required." }, 400)

        const task = await getTask(serviceClient, taskId)
        if (!task || task.requester_id !== caller.id) {
          return json({ error: "Task not found." }, 404)
        }

        if (task.status === "queued") {
          const output = `Agent processed ${task.task_type} for ${caller.email ?? caller.id}`
          const { error } = await serviceClient
            .from("agent_tasks")
            .update({
              status: "completed",
              output,
              updated_at: new Date().toISOString(),
            })
            .eq("id", taskId)
          if (error) throw error

          return json({
            task_id: taskId,
            status: "completed",
            output,
            created_at: task.created_at,
          })
        }

        return json({
          task_id: task.id,
          status: task.status,
          output: task.output ?? "",
          created_at: task.created_at,
        })
      }

      default:
        return json({ error: "Unsupported action." }, 400)
    }
  } catch (error) {
    console.error("agent-orchestrator error", error)
    return json({ error: error instanceof Error ? error.message : "Unknown server error." }, 500)
  }
})
