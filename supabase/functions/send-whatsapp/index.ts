/**
 * send-whatsapp — Supabase Edge Function
 *
 * Proxies WhatsApp Business API calls server-side so that WHATSAPP_TOKEN
 * never leaves the secure Supabase environment.
 *
 * Required Supabase secrets (set via `supabase secrets set`):
 *   WHATSAPP_TOKEN           — Meta Graph API permanent / long-lived token
 *   WHATSAPP_PHONE_NUMBER_ID — Phone-Number ID from Meta Business Suite
 *
 * Expected JSON body:
 * {
 *   "to": "27821234567",          // E.164 without '+', SA numbers start with 27
 *   "message": "Hello!",          // Free-text body (used in text messages)
 *   "template": {                 // Optional: use a Meta-approved template instead
 *     "name": "general_notification",
 *     "language": { "code": "en_US" },
 *     "components": [             // Optional
 *       { "type": "body", "parameters": [{ "type": "text", "text": "Hello!" }] }
 *     ]
 *   }
 * }
 */

import { serve } from "https://deno.land/std@0.168.0/http/server.ts";

const GRAPH_API_VERSION = "v21.0";
const CORS_HEADERS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type, Authorization",
};

serve(async (req) => {
  // ── CORS pre-flight ─────────────────────────────────────────────────────
  if (req.method === "OPTIONS") {
    return new Response(null, { status: 204, headers: CORS_HEADERS });
  }

  // ── Auth guard — only authenticated Supabase users may invoke this ───────
  const authHeader = req.headers.get("Authorization");
  if (!authHeader?.startsWith("Bearer ")) {
    return json({ error: "Unauthorized" }, 401);
  }

  // ── Secrets ─────────────────────────────────────────────────────────────
  const token = Deno.env.get("WHATSAPP_TOKEN");
  const phoneNumberId = Deno.env.get("WHATSAPP_PHONE_NUMBER_ID");

  if (!token || !phoneNumberId) {
    console.error("Missing WHATSAPP_TOKEN or WHATSAPP_PHONE_NUMBER_ID secrets.");
    return json({ error: "WhatsApp credentials not configured on the server." }, 500);
  }

  // ── Parse request body ──────────────────────────────────────────────────
  let body: {
    to: string;
    message?: string;
    template?: {
      name: string;
      language: { code: string };
      components?: Array<{
        type: string;
        parameters: Array<{ type: string; text: string }>;
      }>;
    };
  };

  try {
    body = await req.json();
  } catch {
    return json({ error: "Invalid JSON body." }, 400);
  }

  if (!body.to) {
    return json({ error: "Missing required field: to" }, 400);
  }

  // ── Normalise phone number to E.164 (digits only, SA starts with 27) ────
  const normalised = body.to
    .replace(/\D/g, "")
    .replace(/^0/, "27"); // 0821234567 → 27821234567

  // ── Build Meta Graph API payload ────────────────────────────────────────
  let messagePayload: Record<string, unknown>;

  if (body.template) {
    // Template message (required for business-initiated conversations)
    messagePayload = {
      messaging_product: "whatsapp",
      to: normalised,
      type: "template",
      template: body.template,
    };
  } else if (body.message) {
    // Plain text message (only works within the 24-hour customer service window)
    messagePayload = {
      messaging_product: "whatsapp",
      to: normalised,
      type: "text",
      text: { body: body.message },
    };
  } else {
    return json({ error: "Either 'message' or 'template' must be provided." }, 400);
  }

  // ── Call Meta Graph API ─────────────────────────────────────────────────
  const metaUrl = `https://graph.facebook.com/${GRAPH_API_VERSION}/${phoneNumberId}/messages`;

  console.log(`Sending WhatsApp to ${normalised} via ${phoneNumberId}`);

  try {
    const metaResponse = await fetch(metaUrl, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${token}`,
      },
      body: JSON.stringify(messagePayload),
    });

    const responseData = await metaResponse.json();

    if (!metaResponse.ok) {
      console.error("Meta API error:", JSON.stringify(responseData));
      const errorMsg =
        responseData?.error?.message ??
        `Meta API responded with HTTP ${metaResponse.status}`;
      return json({ error: errorMsg }, metaResponse.status);
    }

    console.log("WhatsApp sent successfully:", JSON.stringify(responseData));
    return json({ success: true, data: responseData }, 200);
  } catch (err) {
    console.error("Failed to call Meta API:", err);
    return json({ error: "Failed to reach WhatsApp API. Please try again." }, 500);
  }
});

// ── Helper ──────────────────────────────────────────────────────────────────
function json(data: unknown, status = 200): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: { ...CORS_HEADERS, "Content-Type": "application/json" },
  });
}

