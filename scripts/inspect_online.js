const { createClient } = require('@supabase/supabase-js');

const supabaseUrl = 'https://aszmodkqpafwtnfufmow.supabase.co';
const supabaseKey = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImFzem1vZGtxcGFmd3RuZnVmbW93Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3Nzc2NDA2MzksImV4cCI6MjA5MzIxNjYzOX0.xmjdSHDGO8sm1cq_574yUxADfCWljtBKf94U-ReTxy8';
const supabase = createClient(supabaseUrl, supabaseKey);

async function inspect() {
    console.log("Inspecting ONLINE Supabase...");

    const { data: groups, error: gErr } = await supabase.from('groups').select('id, name').limit(5);
    if (gErr) console.error("Groups error:", gErr);
    else console.log("Groups found:", groups);

    const { data: members, error: mErr } = await supabase.from('members').select('id, full_name, status').limit(5);
    if (mErr) console.error("Members error:", mErr);
    else console.log("Members found:", members);
}

inspect().catch(console.error);
