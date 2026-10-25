import { serve } from "https://deno.land/std@0.168.0/http/server.ts"

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
}

serve(async (req) => {
  if (req.method === 'OPTIONS') {
    return new Response('ok', { headers: corsHeaders })
  }

  try {
    const { action, id_image, selfie_image } = await req.json()

    if (action === 'EXTRACT_ID') {
      // Mocking Azure AI Document Intelligence
      console.log('Extracting ID info from image...');

      return new Response(
        JSON.stringify({
          full_name: "JOHN DOE",
          id_number: "9001015000081",
          date_of_birth: "1990-01-01",
          document_type: "SA_ID_CARD",
          confidence_score: 0.98,
          is_authentic: true
        }),
        { headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      )
    }

    if (action === 'COMPARE_FACES') {
      // Mocking Azure AI Face API
      console.log('Comparing ID photo with selfie...');

      return new Response(
        JSON.stringify({
          is_match: true,
          confidence_score: 0.92,
          match_status: "SUCCESS"
        }),
        { headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      )
    }

    throw new Error('Invalid action')

  } catch (error) {
    return new Response(
      JSON.stringify({ error: error.message }),
      { status: 400, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
    )
  }
})
