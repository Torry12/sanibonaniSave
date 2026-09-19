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

    const { data: authData, error: authError } = await userClient.auth.getUser()
    if (authError || !authData.user) {
      return json({ error: "Your session is no longer valid. Please sign in again." }, 401)
    }

    const payload = await req.json()
    const { operation, providerId, amount, groupId, metadata } = payload

    console.log(`Financial Orchestrator: ${operation} for provider ${providerId} (Group: ${groupId})`)

    // 1. Fetch provider configuration from database
    const serviceClient = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY)
    const { data: provider, error: providerError } = await serviceClient
      .from("financial_providers")
      .select("*")
      .eq("id", providerId)
      .single()

    if (providerError || !provider) {
      return json({ error: `Provider ${providerId} not found or inactive.` }, 404)
    }

    // 2. Determine which adapter to use based on provider configuration
    const adapterType = provider.adapter_type || providerId

    // 3. Route to the appropriate adapter logic
    let result;
    switch (adapterType) {
      case "nedbank":
        result = await handleNedbank(operation, payload, provider)
        break
      case "fnb":
        result = await handleFNB(operation, payload, provider)
        break
      case "yoco":
        result = await handleYoCo(operation, payload, provider)
        break
      case "sim_bank":
        result = await handleSimBank(operation, payload, provider)
        break
      default:
        return json({ error: `No adapter found for provider type: ${adapterType}` }, 400)
    }

    return json(result)

  } catch (error) {
    console.error("financial-orchestrator error", error)
    return json({ error: error instanceof Error ? error.message : "Unknown server error." }, 500)
  }
})

// --- Adapter Handlers (Mock Implementations) ---

async function handleNedbank(op: string, payload: any, config: any) {
  // Logic to call Nedbank API using config.api_key etc.
  return { success: true, message: `Nedbank operation ${op} simulated successfully.` }
}

async function handleFNB(op: string, payload: any, config: any) {
  return { success: true, message: `FNB operation ${op} simulated successfully.` }
}

async function handleYoCo(op: string, payload: any, config: any) {
  if (op === "initiate_checkout") {
    return {
      success: true,
      transactionId: `yoco_${Math.random().toString(36).substring(7)}`,
      data: { checkoutUrl: "https://pay.yoco.com/sim-checkout" }
    }
  }
  return { success: false, error: "Unsupported YoCo operation" }
}

async function handleSimBank(op: string, payload: any, config: any) {
  switch (op) {
    case "get_balance":
      return { success: true, data: { balance: "25000.00", currency: "ZAR" } }
    case "get_transactions":
      return { success: true, data: { count: "0" } }
    default:
      return { success: true, message: "Simulated bank operation successful" }
  }
}
