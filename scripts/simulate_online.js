const { createClient } = require('@supabase/supabase-js');

const { requireValue } = require('./lib/supabase-config');
const supabaseUrl = requireValue('SUPABASE_URL_ONLINE');
const supabaseKey = requireValue('SUPABASE_ANON_KEY_ONLINE');
const supabase = createClient(supabaseUrl, supabaseKey);

async function runSimulation() {
    console.log("Connected to ONLINE Supabase API. Fetching members...");

    const { data: members, error: fetchErr } = await supabase
        .from('members')
        .select(`id, full_name, group_id, groups!inner(type, balance, monthly_contribution)`)
        .eq('status', 'active')
        .limit(5);

    if (fetchErr) {
        console.error("Failed to fetch members:", fetchErr);
        return;
    }

    if (!members || members.length === 0) {
        console.error("No active members found in the online database.");
        return;
    }

    console.log(`Found ${members.length} members. Running mini-simulation...`);

    for (const member of members) {
        console.log(`Simulating contribution for ${member.full_name} (${member.id})...`);

        // Use record_contribution_v2 RPC
        const { data, error } = await supabase.rpc('record_contribution_v2', {
            p_member_id: member.id,
            p_group_id: member.group_id,
            p_amount: member.groups.monthly_contribution,
            p_due_date: new Date().toISOString().split('T')[0],
            p_paid_at: new Date().toISOString(),
            p_status: 'paid',
            p_tx_id: 'sim-online-' + Math.random().toString(36).substring(7),
            p_type: 'contribution'
        });

        if (error) {
            console.error(`  [FAIL] ${member.full_name}:`, error.message);
        } else {
            console.log(`  [OK] ${member.full_name} contribution recorded.`);
        }
    }

    console.log("Online simulation complete.");
}

runSimulation().catch(console.error);
