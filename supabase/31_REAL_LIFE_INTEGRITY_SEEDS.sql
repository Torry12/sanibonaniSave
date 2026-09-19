-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — REAL-LIFE INTEGRITY SEED DATA
-- Version: 1.0 (Scenario-Based Extreme Testing)
-- ─────────────────────────────────────────────────────────────────────────────
-- This script creates 5 specific "Story Scenarios" to test every architectural
-- corner of the app: logic, RLS, ledger, and behavior tracking.

DO $$
DECLARE
    -- Actors
    v_admin_id UUID;
    v_member_id UUID;
    v_group_id UUID;
    v_loan_id UUID;
    v_claim_id UUID;

    -- Constants
    v_now TIMESTAMPTZ := NOW();
    v_sys_admin_email TEXT := 'torrymsimango@gmail.com';

    -- Scenario Names
    v_scen_1 TEXT := 'SCENARIO-The-Perfect-Stokvel';
    v_scen_2 TEXT := 'SCENARIO-The-Struggling-Burial';
    v_scen_3 TEXT := 'SCENARIO-The-Active-ROSCA';
    v_scen_4 TEXT := 'SCENARIO-The-High-Risk-Club';
    v_scen_5 TEXT := 'SCENARIO-The-New-Start';

BEGIN
    -- 0. IDENTIFY PLATFORM ADMIN
    SELECT id INTO v_admin_id FROM auth.users WHERE email = v_sys_admin_email LIMIT 1;
    IF v_admin_id IS NULL THEN
        SELECT id INTO v_admin_id FROM auth.users LIMIT 1;
    END IF;

    -- 1. CLEANUP (Targeted Scenario Cleanup)
    DELETE FROM public.audit_logs WHERE details->>'scenario' IS NOT NULL;
    DELETE FROM public.notifications WHERE message LIKE '[SCENARIO]%';
    DELETE FROM public.groups WHERE description LIKE '[SCENARIO]%';

    -- ─────────────────────────────────────────────────────────────────────────
    -- SCENARIO 1: THE PERFECT STOKVEL
    -- Goal: Test "Happy Path", dashboard totals, and perfect reconciliation.
    -- ─────────────────────────────────────────────────────────────────────────
    INSERT INTO public.groups (
        name, type, province, city, joining_fee, monthly_contribution,
        balance, admin_user_id, fee_status, registration_paid, description
    ) VALUES (
        'The Golden Circle', 'stokvel', 'Gauteng', 'Johannesburg', 100.00, 500.00,
        0.00, v_admin_id, 'paid', TRUE, '[SCENARIO] 1: All members active and on-time.'
    ) RETURNING id INTO v_group_id;

    -- Seed 10 perfect members
    FOR i IN 1..10 LOOP
        INSERT INTO public.members (group_id, full_name, email, status, joined_at, member_key)
        VALUES (v_group_id, 'Perfect Member ' || i, 'perfect.' || i || '@test.com', 'active', v_now - interval '6 months', 'SCEN-1-' || i)
        RETURNING id INTO v_member_id;

        -- Record 6 months of on-time payments
        FOR m IN 0..5 LOOP
            PERFORM public.record_contribution_v1(
                v_member_id, v_group_id, 500.00, (v_now - (m * 30 || ' days')::interval)::date,
                v_now - (m * 30 || ' days')::interval, 'paid', 'SCEN-1-TX-' || i || '-' || m
            );
        END LOOP;
    END LOOP;

    -- ─────────────────────────────────────────────────────────────────────────
    -- SCENARIO 2: THE STRUGGLING BURIAL SOCIETY
    -- Goal: Test claims processing, waiting periods, and platform fee warnings.
    -- ─────────────────────────────────────────────────────────────────────────
    INSERT INTO public.groups (
        name, type, province, city, joining_fee, monthly_contribution,
        balance, admin_user_id, fee_status, registration_paid, description,
        max_beneficiaries, probation_months
    ) VALUES (
        'Unity Burial Society', 'burial_society', 'KwaZulu-Natal', 'Durban', 150.00, 200.00,
        5000.00, v_admin_id, 'warning', TRUE, '[SCENARIO] 2: High claims, late fees, and platform debt.'
    ) RETURNING id INTO v_group_id;

    -- Member with an APPROVED claim
    INSERT INTO public.members (group_id, full_name, email, status, joined_at, member_key)
    VALUES (v_group_id, 'Bereaved Member', 'bereaved@test.com', 'active', v_now - interval '1 year', 'SCEN-2-1')
    RETURNING id INTO v_member_id;

    INSERT INTO public.beneficiary_payout_claims (
        group_id, member_id, beneficiary_id, beneficiary_name, claim_amount, status, created_at
    ) VALUES (
        v_group_id, v_member_id, gen_random_uuid(), 'Late Parent', 10000.00, 'approved', v_now - interval '2 days'
    );

    -- Member with an ESCALATED claim (needs Platform Admin attention)
    INSERT INTO public.members (group_id, full_name, email, status, joined_at, member_key)
    VALUES (v_group_id, 'Escalated Member', 'escalated@test.com', 'active', v_now - interval '1 year', 'SCEN-2-2')
    RETURNING id INTO v_member_id;

    INSERT INTO public.beneficiary_payout_claims (
        group_id, member_id, beneficiary_id, beneficiary_name, claim_amount, status, created_at, notes
    ) VALUES (
        v_group_id, v_member_id, gen_random_uuid(), 'Contested Beneficiary', 15000.00, 'escalated', v_now - interval '5 days', 'Identity documents look forged.'
    );

    -- ─────────────────────────────────────────────────────────────────────────
    -- SCENARIO 3: THE ACTIVE ROSCA
    -- Goal: Test rotation logic, positions, and payout tracking.
    -- ─────────────────────────────────────────────────────────────────────────
    INSERT INTO public.groups (
        name, type, province, city, joining_fee, monthly_contribution,
        balance, admin_user_id, fee_status, registration_paid, description,
        rosca_rotation_method, period_months
    ) VALUES (
        'Fast Lane ROSCA', 'rosca', 'Western Cape', 'Cape Town', 0.00, 1000.00,
        1000.00, v_admin_id, 'paid', TRUE, '[SCENARIO] 3: Mid-cycle rotation with historical payouts.'
    ) RETURNING id INTO v_group_id;

    FOR i IN 1..6 LOOP
        INSERT INTO public.members (group_id, full_name, email, status, joined_at, rosca_position, member_key)
        VALUES (v_group_id, 'ROSCA Member ' || i, 'rosca.' || i || '@test.com', 'active', v_now - interval '6 months', i, 'SCEN-3-' || i)
        RETURNING id INTO v_member_id;

        -- Position 1 already got their payout
        IF i = 1 THEN
            INSERT INTO public.payouts (group_id, member_id, amount, status, bank_name, account_no, branch_code, processed_at, payout_reference)
            VALUES (v_group_id, v_member_id, 6000.00, 'completed', 'FNB', '987654321', '250655', v_now - interval '2 months', 'SCEN-3-PAY-1');

            PERFORM public.increment_group_balance(v_group_id, -6000.00, 'ROSCA Payout Position 1', 'payout', 'SCEN-3-PAY-1', v_member_id);
        END IF;
    END LOOP;

    -- ─────────────────────────────────────────────────────────────────────────
    -- SCENARIO 4: THE HIGH-RISK CLUB (Fraud & Defaults)
    -- Goal: Test behavior tracking, fraud events, and forced settlements.
    -- ─────────────────────────────────────────────────────────────────────────
    INSERT INTO public.groups (
        name, type, province, city, joining_fee, monthly_contribution,
        balance, admin_user_id, fee_status, registration_paid, description
    ) VALUES (
        'Risk & Reward Investment', 'investment_club', 'Gauteng', 'Pretoria', 500.00, 2000.00,
        50000.00, v_admin_id, 'paid', TRUE, '[SCENARIO] 4: Fraud detection and defaulted loans.'
    ) RETURNING id INTO v_group_id;

    -- The "Fraudulent" Member
    INSERT INTO public.members (group_id, full_name, email, status, joined_at, member_key)
    VALUES (v_group_id, 'Shady Sam', 'shady@test.com', 'suspended', v_now - interval '3 months', 'SCEN-4-1')
    RETURNING id INTO v_member_id;

    INSERT INTO public.fraud_detection_events (member_id, group_id, event_type, severity, details_json)
    VALUES (v_member_id, v_group_id, 'VELOCITY_SPIKE', 'CRITICAL', '{"reason": "Attempted 10 withdrawals in 1 hour", "scenario": "true"}');

    -- The "Defaulting" Member
    INSERT INTO public.members (group_id, full_name, email, status, joined_at, member_key)
    VALUES (v_group_id, 'Defaulting Debbie', 'deb@test.com', 'active', v_now - interval '6 months', 'SCEN-4-2')
    RETURNING id INTO v_member_id;

    INSERT INTO public.loans (
        member_id, group_id, amount, interest_rate, total_to_repay, total_repaid,
        monthly_repayment, status, start_date, purpose
    ) VALUES (
        v_member_id, v_group_id, 5000.00, 10.0, 5500.00, 0.00,
        1000.00, 'overdue', (v_now - interval '90 days')::date, '[SCENARIO] Defaulted loan'
    ) RETURNING id INTO v_loan_id;

    -- Record 10 on-time contributions to Sam's behavior track (simulated)
    INSERT INTO public.member_behavior_track (
        member_id, group_id, behavior_score, fraud_score, fraud_risk_level,
        total_contributions, overdue_count, is_flagged_for_review
    ) VALUES (v_member_id, v_group_id, 15.5, 95.0, 'CRITICAL', 2, 8, TRUE);

    -- ─────────────────────────────────────────────────────────────────────────
    -- SCENARIO 5: THE NEW START
    -- Goal: Test "Probation" logic and empty-state handling.
    -- ─────────────────────────────────────────────────────────────────────────
    INSERT INTO public.groups (
        name, type, province, city, joining_fee, monthly_contribution,
        balance, admin_user_id, fee_status, registration_paid, description,
        probation_months
    ) VALUES (
        'Morning Star Fund', 'emergency_fund', 'Eastern Cape', 'Mthatha', 50.00, 100.00,
        0.00, v_admin_id, 'paid', TRUE, '[SCENARIO] 5: Brand new group, everyone in probation.'
    ) RETURNING id INTO v_group_id;

    FOR i IN 1..5 LOOP
        INSERT INTO public.members (group_id, full_name, email, status, joined_at, member_key, probation_end_at)
        VALUES (v_group_id, 'Newbie ' || i, 'newbie.' || i || '@test.com', 'probation', v_now - interval '5 days', 'SCEN-5-' || i, v_now + interval '3 months');
    END LOOP;

    -- ─────────────────────────────────────────────────────────────────────────
    -- AUDIT LOGS FOR SCENARIOS
    -- ─────────────────────────────────────────────────────────────────────────
    INSERT INTO public.audit_logs (actor_id, action, details)
    VALUES
        (v_admin_id, 'SCENARIO_SETUP_COMPLETE', '{"scenario": "true", "timestamp": "' || v_now::text || '"}'),
        (v_admin_id, 'FRAUD_INVESTIGATION_STARTED', '{"scenario": "true", "target_member": "Shady Sam"}');

    -- ─────────────────────────────────────────────────────────────────────────
    -- NOTIFICATIONS
    -- ─────────────────────────────────────────────────────────────────────────
    INSERT INTO public.notifications (group_id, message, trigger_event)
    SELECT id, '[SCENARIO] Welcome to your new savings group!', 'custom'
    FROM public.groups WHERE description LIKE '[SCENARIO]%';

    RAISE NOTICE 'Extreme Integrity Seed Data Successfully Generated.';
END $$;
