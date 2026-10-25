-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — REAL-LIFE INTEGRITY SEEDS (Extreme Testing Scenarios)
-- Version: 2.0 (Money-Precise, Audit & Forensic Hardening - August 2026)
-- ─────────────────────────────────────────────────────────────────────────────

DO $$
DECLARE
    v_stokvel_id UUID;
    v_burial_id UUID;
    v_emergency_id UUID;
    v_member_id UUID;
    v_user_id UUID;
    v_loan_id UUID;
    v_now TIMESTAMPTZ := NOW();
    v_qa_password TEXT := 'Pass123!';

BEGIN
    RAISE NOTICE '🔥 Injecting Real-Life Integrity Scenarios (Money-Precise)...';

    -- Identify Target Groups
    SELECT id INTO v_stokvel_id FROM public.groups WHERE name = 'LIGHT-Stokvel' LIMIT 1;
    SELECT id INTO v_burial_id FROM public.groups WHERE name = 'LIGHT-Burial Society' LIMIT 1;
    SELECT id INTO v_emergency_id FROM public.groups WHERE name = 'LIGHT-Emergency Fund' LIMIT 1;

    IF v_stokvel_id IS NULL OR v_burial_id IS NULL OR v_emergency_id IS NULL THEN
        RAISE EXCEPTION 'Run MASTER_SEED_DATA.sql first; required LIGHT demo groups are missing.';
    END IF;

    -- Make this scenario seed repeatable without touching non-seed members.
    DELETE FROM public.members
    WHERE email IN ('risky.payer@scen.com', 'loan.defaulter@scen.com', 'claimant@scen.com');
    DELETE FROM auth.users
    WHERE email IN ('risky.payer@scen.com', 'loan.defaulter@scen.com', 'claimant@scen.com');
    DELETE FROM public.audit_logs
    WHERE action = 'INTEGRITY_DRIFT_INJECTED' AND target_group_id = v_stokvel_id;

    -- 1. SCENARIO: The "Risky Payer" (LIGHT-Stokvel)
    -- Purpose: Test behavior score tracking and "High Risk" flags in Audit.
    v_user_id := gen_random_uuid();
    INSERT INTO auth.users (id, instance_id, aud, role, email, encrypted_password, email_confirmed_at, raw_app_meta_data, raw_user_meta_data, created_at, updated_at)
    VALUES (v_user_id, '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated', 'risky.payer@scen.com', extensions.crypt(v_qa_password, extensions.gen_salt('bf')), now(), '{"provider":"email","providers":["email"],"role":"member"}', '{"full_name":"Sibusiso Risk","role":"member"}', now(), now());
    INSERT INTO public.profiles (id, full_name, email, role) VALUES (v_user_id, 'Sibusiso Risk', 'risky.payer@scen.com', 'member');

    INSERT INTO public.members (group_id, user_id, full_name, email, status, joined_at, member_key)
    VALUES (v_stokvel_id, v_user_id, 'Sibusiso Risk', 'risky.payer@scen.com', 'active', v_now - interval '6 months', 'SCEN-RISK-1')
    RETURNING id INTO v_member_id;

    INSERT INTO public.member_behavior_track (member_id, group_id, joined_at, last_activity_at)
    VALUES (v_member_id, v_stokvel_id, v_now - interval '6 months', v_now)
    ON CONFLICT (member_id) DO NOTHING;

    -- Seed 3 Overdue contributions using Money-precise amounts
    INSERT INTO public.contributions (member_id, group_id, amount, due_date, status, type)
    VALUES
        (v_member_id, v_stokvel_id, 500.00, (v_now - interval '90 days')::date, 'overdue', 'contribution'),
        (v_member_id, v_stokvel_id, 500.00, (v_now - interval '60 days')::date, 'overdue', 'contribution'),
        (v_member_id, v_stokvel_id, 500.00, (v_now - interval '30 days')::date, 'overdue', 'contribution');

    -- Manually force behavior score down (Trigger normally handles this on update, but we ensure it here)
    UPDATE public.member_behavior_track
    SET behavior_score = 30.0,
        fraud_risk_level = 'high',
        overdue_count = 3,
        review_notes = 'Member has missed 3 consecutive payments. Flagged for audit.'
    WHERE member_id = v_member_id;


    -- 2. SCENARIO: The "Defaulted Loan" (LIGHT-Emergency Fund)
    -- Purpose: Test loan status transitions and outstanding debt reporting.
    v_user_id := gen_random_uuid();
    INSERT INTO auth.users (id, instance_id, aud, role, email, encrypted_password, email_confirmed_at, raw_app_meta_data, raw_user_meta_data, created_at, updated_at)
    VALUES (v_user_id, '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated', 'loan.defaulter@scen.com', extensions.crypt(v_qa_password, extensions.gen_salt('bf')), now(), '{"provider":"email","providers":["email"],"role":"member"}', '{"full_name":"Lindiwe Debt","role":"member"}', now(), now());
    INSERT INTO public.profiles (id, full_name, email, role) VALUES (v_user_id, 'Lindiwe Debt', 'loan.defaulter@scen.com', 'member');

    INSERT INTO public.members (group_id, user_id, full_name, email, status, joined_at, member_key)
    VALUES (v_emergency_id, v_user_id, 'Lindiwe Debt', 'loan.defaulter@scen.com', 'active', v_now - interval '1 year', 'SCEN-DEBT-1')
    RETURNING id INTO v_member_id;

    INSERT INTO public.member_behavior_track (member_id, group_id, joined_at, last_activity_at)
    VALUES (v_member_id, v_emergency_id, v_now - interval '1 year', v_now)
    ON CONFLICT (member_id) DO NOTHING;

    -- 🛡️ Boost Group Liquidity (Institutional injection to satisfy 50% withdrawal rule)
    IF NOT EXISTS (
        SELECT 1 FROM public.group_ledger
        WHERE group_id = v_emergency_id
          AND (transaction_id = 'SCEN-EMERGENCY-GRANT'
               OR description = 'Seed Liquidity - Institutional Grant')
    ) THEN
        PERFORM public.increment_group_balance(
            v_emergency_id, 
            10000.00, 
            'Seed Liquidity - Institutional Grant',
            'adjustment',
            'SCEN-EMERGENCY-GRANT',
            v_member_id
        );
    END IF;

    INSERT INTO public.loans (member_id, group_id, amount, interest_rate, total_to_repay, total_repaid, monthly_repayment, start_date, end_date, status, purpose)
    VALUES (v_member_id, v_emergency_id, 5000.00, 15.00, 5750.00, 0.00, 479.17, (v_now - interval '4 months')::date, (v_now + interval '8 months')::date, 'overdue', 'Emergency Repairs')
    RETURNING id INTO v_loan_id;


    -- 3. SCENARIO: The "Escalated Claim" (LIGHT-Burial Society)
    -- Purpose: Test Platform Admin intervention workflow and claim limits.
    v_user_id := gen_random_uuid();
    INSERT INTO auth.users (id, instance_id, aud, role, email, encrypted_password, email_confirmed_at, raw_app_meta_data, raw_user_meta_data, created_at, updated_at)
    VALUES (v_user_id, '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated', 'claimant@scen.com', extensions.crypt(v_qa_password, extensions.gen_salt('bf')), now(), '{"provider":"email","providers":["email"],"role":"member"}', '{"full_name":"John Claimant","role":"member"}', now(), now());
    INSERT INTO public.profiles (id, full_name, email, role) VALUES (v_user_id, 'John Claimant', 'claimant@scen.com', 'member');

    INSERT INTO public.members (group_id, user_id, full_name, email, status, joined_at, member_key)
    VALUES (v_burial_id, v_user_id, 'John Claimant', 'claimant@scen.com', 'active', v_now - interval '2 years', 'SCEN-CLAIM-1')
    RETURNING id INTO v_member_id;

    INSERT INTO public.member_behavior_track (member_id, group_id, joined_at, last_activity_at)
    VALUES (v_member_id, v_burial_id, v_now - interval '2 years', v_now)
    ON CONFLICT (member_id) DO NOTHING;

    INSERT INTO public.beneficiary_payout_claims (
        group_id, member_id, beneficiary_id, beneficiary_name, claim_amount,
        cause_of_death, date_of_death, status, bank_name, account_no, branch_code, account_holder, notes
    ) VALUES (
        v_burial_id, v_member_id, gen_random_uuid(), 'Late Relative', 25000.00,
        'Accident', (v_now - interval '5 days')::date, 'escalated', 'Capitec', '55667788', '470010', 'J Claimant', 'High-value claim requires platform review.'
    );


    -- 4. SCENARIO: "Financial Drift" Detection
    -- Purpose: Create a discrepancy between ledger and table balance for Audit testing.
    -- We bypass the normal increment function to create "drift".
    UPDATE public.groups AS g
    SET balance = COALESCE((
        SELECT SUM(l.amount) FROM public.group_ledger AS l WHERE l.group_id = g.id
    ), 0) + 1234.56
    WHERE g.id = v_stokvel_id;

    INSERT INTO public.audit_logs (actor_id, target_group_id, action, details)
    VALUES ('system', v_stokvel_id, 'INTEGRITY_DRIFT_INJECTED', '{"amount": 1234.56, "reason": "Test forensic detection of un-ledgered balance changes."}');

    -- Reconcile the other scenarios. Keep the stokvel mismatch visible for audit testing.
    PERFORM public.recalculate_all_group_financials_v3(v_burial_id);
    PERFORM public.recalculate_all_group_financials_v3(v_emergency_id);

    RAISE NOTICE '🏁 Integrity Scenarios Injected. Forensic Audit now has active leads.';
END $$;