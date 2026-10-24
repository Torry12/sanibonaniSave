import { serve } from "https://deno.land/std@0.177.0/http/server.ts";

serve(async (req) => {
    try {
        const payload = await req.json();
        const action = payload.action;

        if (action === 'initiate') {
            const txId = `sandbox_tx_${Math.floor(Math.random() * 1000000000)}`;
            return new Response(
                JSON.stringify({
                    transactionId: txId,
                    checkoutUrl: `sanibonani://bank/sandbox?tx=${txId}&amount=${payload.amount}`
                }),
                { headers: { "Content-Type": "application/json" } }
            );
        } else if (action === 'verify') {
            return new Response(
                JSON.stringify({
                    status: 'success',
                    amount: '100.00', // Typically in sandbox we might echo back or hardcode
                    transactionId: payload.transactionId
                }),
                { headers: { "Content-Type": "application/json" } }
            );
        }

        return new Response(
            JSON.stringify({ error: "Invalid action" }),
            { status: 400, headers: { "Content-Type": "application/json" } }
        );
    } catch (err) {
        return new Response(
            JSON.stringify({ error: err.message }),
            { status: 500, headers: { "Content-Type": "application/json" } }
        );
    }
});
