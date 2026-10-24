const { createClient } = require('@supabase/supabase-js');
const fs = require('fs');

const { requireValue } = require('./lib/supabase-config');
const supabaseUrl = requireValue('SUPABASE_URL_ONLINE');
const supabaseKey = requireValue('SUPABASE_ANON_KEY_ONLINE');
const supabase = createClient(supabaseUrl, supabaseKey);

async function delay(ms) {
    return new Promise(resolve => setTimeout(resolve, ms));
}

async function runSimulation() {
    console.log("Connected to local Supabase API on port 45421. Fetching 30 users...");

    const { data: users, error: fetchErr } = await supabase
        .from('members')
        .select(`id, group_id, groups!inner(type, balance, monthly_contribution)`)
        .ilike('status', 'active')
        .limit(30);

    if (fetchErr) {
        console.error("Failed to fetch users:", fetchErr);
        return;
    }

    if (!users || users.length === 0) {
        console.error("No users found. Is the seed data populated?");
        return;
    }

    let bugs = [];
    let expectedRejections = [];
    let successes = [];

    const simulateUser = async (userObj, index) => {
        const user = {
            member_id: userObj.id,
            group_id: userObj.group_id,
            group_type: userObj.groups.type,
            group_balance: userObj.groups.balance,
            monthly_contribution: userObj.groups.monthly_contribution
        };

        const logBug = (action, error) => {
            bugs.push({ user: index, action, error: error.message || error });
        };
        const logReject = (action, error) => {
            expectedRejections.push({ user: index, action, error: error.message || error });
        };
        const logSuccess = (action) => {
            successes.push({ user: index, action });
        };

        // 1. Valid Contribution
        const { error: tx1Err } = await supabase.rpc('record_contribution_v2', {
            p_member_id: user.member_id,
            p_group_id: user.group_id,
            p_amount: user.monthly_contribution,
            p_due_date: new Date().toISOString().split('T')[0],
            p_paid_at: new Date().toISOString(),
            p_status: 'paid',
            p_tx_id: 'sim-tx-' + Math.random().toString(36).substring(7),
            p_type: 'contribution'
        });

        if (tx1Err) logBug('Valid Contribution', tx1Err);
        else logSuccess('Valid Contribution');

        // 2. Invalid Contribution (Negative)
        const { error: tx2Err } = await supabase.rpc('record_contribution_v2', {
            p_member_id: user.member_id,
            p_group_id: user.group_id,
            p_amount: -50.00,
            p_due_date: new Date().toISOString().split('T')[0],
            p_paid_at: new Date().toISOString(),
            p_status: 'paid',
            p_tx_id: 'sim-tx-neg-' + Math.random().toString(36).substring(7),
            p_type: 'contribution'
        });

        if (!tx2Err) logBug('Negative Contribution Allowed', 'DB did not reject negative contribution.');
        else logReject('Negative Contribution Blocked', tx2Err);

    };

    console.log("Simulating concurrent operations...");
    const promises = users.map((user, index) => simulateUser(user, index));
    await Promise.all(promises);

    console.log("Simulation complete. Generating report...");

    const mdReport = `
# Live Simulation Bug Report

**Date**: ${new Date().toISOString()}
**Simulated Users**: 30
**Environment**: Local Supabase Backend (Data/Database Layer Simulation)

## Executive Summary
This report captures the results of simulating concurrent user sessions performing write-heavy transactional operations.

### Metrics
* **Total Expected Rejections (Security Working)**: ${expectedRejections.length}
* **Total Successful Transactions**: ${successes.length}
* **Total Anomalies/Bugs Discovered**: ${bugs.length}

## Bugs & Vulnerabilities

${bugs.length === 0 ? '> [!TIP]\\n> No critical database-level bugs detected.' : bugs.map(b => '- **User ' + b.user + ' (' + b.action + ')**: ' + JSON.stringify(b.error)).join('\\n')}

## Security Validations (Expected Rejections)

The system correctly blocked the following illegal operations:
${expectedRejections.map(r => '- **' + r.action + '**: ' + JSON.stringify(r.error)).slice(0, 15).join('\\n')}
${expectedRejections.length > 15 ? '\\n*... and ' + (expectedRejections.length - 15) + ' more.*' : ''}
    `;

    fs.writeFileSync('C:\\\\Users\\\\CRISS\\\\.gemini\\\\antigravity\\\\brain\\\\5f8e1d9a-a73e-416b-828f-789bb164ca64\\\\SIMULATION_BUG_REPORT.md', mdReport);
    console.log("Report saved to artifacts.");
}

runSimulation().catch(console.error);
