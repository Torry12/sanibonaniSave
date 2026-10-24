const { createClient } = require('@supabase/supabase-js');

const { requireValue } = require('./lib/supabase-config');
const supabaseUrl = requireValue('SUPABASE_URL_ONLINE');
const supabaseKey = requireValue('SUPABASE_ANON_KEY_ONLINE');
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
