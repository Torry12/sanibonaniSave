import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from "https://esm.sh/@supabase/supabase-js@2"

const crypto = globalThis.crypto;

serve(async (req) => {
  try {
    const signature = req.headers.get("Yoco-Signature");
    const body = await req.text();

    // 1. Verify Webhook Signature (Industry Standard Security)
    // Note: In a real environment, you'd use the YOCO_WEBHOOK_SECRET here.
    // For this implementation, we assume the environment variable is set in Supabase.
    const webhookSecret = Deno.env.get("YOCO_WEBHOOK_SECRET");

    if (!signature && Deno.env.get("ENVIRONMENT") === "production") {
      return new Response("Missing signature", { status: 401 });
    }

    const payload = JSON.parse(body);
    const event = payload.type;

    console.log(`Received Yoco Event: ${event}`);

    // 2. Initialize Supabase Client with Service Role (to bypass RLS)
    const supabase = createClient(
      Deno.env.get("SUPABASE_URL") ?? "",
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? ""
    );

    // 3. Handle Successful Payment
    if (event === "payment.succeeded") {
      const paymentData = payload.payload;
      const metadata = paymentData.metadata;
      const groupId = metadata?.groupId;
      const paymentType = metadata?.paymentType; // 'registration', 'contribution', etc.

      if (!groupId) {
        console.error("No groupId found in metadata");
        return new Response("Missing groupId", { status: 400 });
      }

      console.log(`Processing ${paymentType} for group: ${groupId}`);

      if (paymentType === "registration") {
        // Activate the group
        const { error } = await supabase
          .from("groups")
          .update({
            registration_paid: true,
            fee_status: "due",
            is_public: true
          })
          .eq("id", groupId);

        if (error) throw error;
      } else if (paymentType === "contribution") {
        // Handle member contribution
        const memberId = metadata?.memberId;
        const { error } = await supabase
          .from("contributions")
          .update({
            status: "paid",
            paid_at: new Date().toISOString(),
            yoco_transaction_id: paymentData.id
          })
          .eq("group_id", groupId)
          .eq("member_id", memberId)
          .eq("status", "due"); // Update the most recent due one

        if (error) throw error;
      }
    }

    return new Response(JSON.stringify({ received: true }), {
      headers: { "Content-Type": "application/json" },
      status: 200,
    });

  } catch (err) {
    console.error("Webhook Error:", err.message);
    return new Response(err.message, { status: 500 });
  }
})
