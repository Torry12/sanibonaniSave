-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — SCENARIO-BASED ENHANCEMENTS
-- Version: 1.0 (Advanced Operational Scenarios)
-- ─────────────────────────────────────────────────────────────────────────────

DO $$
DECLARE
    v_admin_id UUID;
    v_member_id UUID;
    v_group_id UUID;
    v_loan_id UUID;
    v_now TIMESTAMPTZ := NOW();
BEGIN
    -- 0. IDENTIFY ADMIN
    SELECT id INTO v_admin_id FROM auth.users WHERE email = 'torrymsimango@gmail.com' LIMIT 1;
    IF v_admin_id IS NULL THEN SELECT id INTO v_admin_id FROM auth.users LIMIT 1; END IF;

    -- 1. SCENARIO: AUTOMATED DEBT RECOVERY (Forced Settlement)
    RAISE NOTICE 'Seeding Scenario: Debt Recovery...';
    INSERT INTO public.groups (
        name, type, province, city, joining_fee, monthly_contribution,
        balance, admin_user_id, fee_status, registration_paid, description
    ) VALUES (
        'Debt Recovery Test Club', 'investment_club', 'Gauteng', 'Johannesburg', 500.00, 1000.00,
        0.00, v_admin_id, 'paid', TRUE, 'SEED-SCENARIO: Test automated extraction from savings.'
    ) RETURNING id INTO v_group_id;

    INSERT INTO public.members (
        group_id, full_name, email, phone, status, joined_at, member_key, id_number
    ) VALUES (
        v_group_id, 'Overdue Borrower', 'borrower@scen.com', '0710000002', 'active',
        v_now - interval '180 days', 'SEED-SCEN-DEBT-1', '8001015000081'
    ) RETURNING id INTO v_member_id;

    -- Add R5,000 savings
    PERFORM public.record_contribution_v1(v_member_id, v_group_id, 5000.00, (v_now - interval '150 days')::date, v_now - interval '150 days', 'paid', 'SEED-SCEN-SAVE-1', 'contribution');

    -- Create overdue loan (R3,000)
    INSERT INTO public.loans (
        member_id, group_id, amount, interest_rate, total_to_repay, total_repaid, monthly_repayment,
        status, start_date, purpose, created_at
    ) VALUES (
        v_member_id, v_group_id, 3000.00, 0.0, 3000.00, 0.00, 1000.00,
        'overdue', (v_now - interval '120 days')::date, 'SEED-Defaulted Loan', v_now - interval '125 days'
    ) RETURNING id INTO v_loan_id;

    -- Execute the actual extraction (Testing the RPC in the seed!)
    PERFORM public.record_loan_settlement_extraction_v1(v_member_id, v_group_id, 3000.00, v_loan_id);

    -- 2. SCENARIO: INVESTMENT CLUB NAV FLUCTUATION
    RAISE NOTICE 'Seeding Scenario: NAV Fluctuation...';
    INSERT INTO public.groups (
        name, type, province, city, joining_fee, monthly_contribution,
        balance, admin_user_id, fee_status, registration_paid, description
    ) VALUES (
        'Alpha Investment Club', 'investment_club', 'Western Cape', 'Cape Town', 1000.00, 2000.00,
        0.00, v_admin_id, 'paid', TRUE, 'SEED-SCENARIO: High-fidelity NAV and equity tracking.'
    ) RETURNING id INTO v_group_id;

    FOR i IN 1..5 LOOP
        INSERT INTO public.members (
            group_id, full_name, email, phone, status, joined_at, member_key, id_number
        ) VALUES (
            v_group_id, 'Investor ' || i, 'investor.' || i || '@scen.com', '072000000' || i, 'active',
            v_now - interval '1 year', 'SEED-SCEN-INV-' || i, '700101500' || i || '081'
        ) RETURNING id INTO v_member_id;

        -- Record 12 months of contributions
        FOR m IN 1..12 LOOP
            PERFORM public.record_contribution_v1(v_member_id, v_group_id, 2000.00, (v_now - (m * 30 || ' days')::interval)::date, v_now - (m * 30 || ' days')::interval, 'paid', 'SEED-SCEN-INV-TX-' || i || '-' || m, 'contribution');
        END LOOP;
    END LOOP;

    -- Add a "Windfall" to the group balance to boost NAV (e.g., successful investment return)
    PERFORM public.increment_group_balance(v_group_id, 50000.00, 'SEED-SCEN: Dividend Payout from Property Fund', 'investment_return', 'SEED-WINDFALL-1');

    -- 3. SCENARIO: BURIAL CLAIM UNDER REVIEW
    RAISE NOTICE 'Seeding Scenario: Burial Claim Review...';
    INSERT INTO public.groups (
        name, type, province, city, joining_fee, monthly_contribution,
        balance, admin_user_id, fee_status, registration_paid, description
    ) VALUES (
        'Legacy Burial Society', 'burial_society', 'KwaZulu-Natal', 'Durban', 200.00, 150.00,
        50000.00, v_admin_id, 'paid', TRUE, 'SEED-SCENARIO: Large claim requiring platform review.'
    ) RETURNING id INTO v_group_id;

    -- Add a member with a beneficiary
    INSERT INTO public.members (
        group_id, full_name, email, phone, status, joined_at, member_key, id_number
    ) VALUES (
        v_group_id, 'Legacy Member', 'legacy@scen.com', '0730000001', 'active',
        v_now - interval '2 years', 'SEED-SCEN-LEG-1', '6001015000081'
    ) RETURNING id INTO v_member_id;

    -- High-value claim (R25,000)
    INSERT INTO public.beneficiary_payout_claims (
        group_id, member_id, beneficiary_id, beneficiary_name, claim_amount, status,
        cause_of_death, date_of_death, bank_name, account_no, branch_code, account_holder, notes
    ) VALUES (
        v_group_id, v_member_id, gen_random_uuid(), 'Legacy Beneficiary', 25000.00, 'under_review',
        'Natural', (v_now - interval '2 days')::date, 'Standard Bank', '55443322', '051001', 'Claimant Z', 'SEED-SCENARIO: High-value claim (>R20k) flagged for audit.'
    );

    -- Repair all scenarios
    RAISE NOTICE 'Repairing scenario data...';
    PERFORM public.repair_group_data_v1(v_group_id);

    RAISE NOTICE '🚀 Scenario-Based Enhancements Complete.';
END $$;
