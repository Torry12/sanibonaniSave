-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — LIVE BACKEND SIMULATION & INTEGRITY DEBUG
-- Version: 1.0 (June 4, 2026)
-- ─────────────────────────────────────────────────────────────────────────────

DO $$
DECLARE
    v_admin_id UUID;
    v_group_id UUID;
    v_member_id UUID;
    v_loan_id UUID;
    v_claim_id UUID;
    v_idx INT;
    v_month INT;
    v_contrib_amt NUMERIC := 500.00;
    v_group_name TEXT := 'SIM-DEBUG-CIRCLE-' || floor(random() * 1000);
BEGIN
    RAISE NOTICE '🚀 Starting SQL-based Live Simulation for %', v_group_name;

    -- 1. Get Platform Admin
    SELECT id INTO v_admin_id FROM auth.users WHERE email = 'torrymsimango@gmail.com' LIMIT 1;
    IF v_admin_id IS NULL THEN
        RAISE EXCEPTION 'Platform admin not found. Run admin setup first.';
    END IF;

    -- 2. Create Temporary Group
    INSERT INTO public.groups (
        name, type, province, city, township, description, joining_fee, monthly_contribution,
        balance, admin_user_id, fee_status, registration_paid, created_at
    ) VALUES (
        v_group_name, 'stokvel', 'Gauteng', 'Johannesburg', 'Sim-Zone',
        'SIMULATION_DEBUG: Automated stress test', 150.00, v_contrib_amt,
        0.00, v_admin_id, 'paid', TRUE, now() - interval '90 days'
    ) RETURNING id INTO v_group_id;

    -- 3. Add 10 Members
    FOR v_idx IN 1..10 LOOP
        INSERT INTO public.members (
            group_id, full_name, email, phone, status, joined_at, member_key
        ) VALUES (
            v_group_id,
            'Sim Member ' || v_idx,
            'sim.' || v_idx || '@debug.example.com',
            '071000' || lpad(v_idx::text, 4, '0'),
            'active',
            now() - interval '90 days',
            'SIM-KEY-' || v_group_id || '-' || v_idx
        ) RETURNING id INTO v_member_id;

        -- 4. Simulate Month 1: 100% Collection
        PERFORM public.record_contribution_v1(
            v_member_id, v_group_id, v_contrib_amt,
            (now() - interval '60 days')::date,
            now() - interval '58 days',
            'paid',
            'sim-tx-m1-' || v_member_id,
            'contribution'
        );

        -- 5. Simulate Month 2: 50% Default
        IF v_idx % 2 = 0 THEN
            PERFORM public.record_contribution_v1(
                v_member_id, v_group_id, v_contrib_amt,
                (now() - interval '30 days')::date,
                now() - interval '28 days',
                'paid',
                'sim-tx-m2-' || v_member_id,
                'contribution'
            );
        END IF;

        -- 6. Simulate Loans for even members
        IF v_idx = 2 THEN
            v_loan_id := gen_random_uuid();
            INSERT INTO public.loans (
                id, member_id, group_id, amount, interest_rate, total_to_repay, total_repaid,
                monthly_repayment, start_date, status, purpose
            ) VALUES (
                v_loan_id, v_member_id, v_group_id, 1000.00, 10.0, 1100.00, 0.00,
                550.00, (now() - interval '15 days')::date, 'active', 'SIM_DEBUG_LOAN'
            );

            -- Repay once
            PERFORM public.record_loan_repayment_v1(v_loan_id, v_member_id, v_group_id, 550.00, 'bank');
        END IF;
    END LOOP;

    -- 7. Run Repair and Verify
    RAISE NOTICE '--- VERIFYING INTEGRITY ---';
    PERFORM public.repair_group_data_v1(v_group_id);

    -- Checks
    IF (SELECT current_members FROM public.groups WHERE id = v_group_id) != 10 THEN
        RAISE WARNING '❌ INTEGRITY ERROR: current_members mismatch';
    ELSE
        RAISE NOTICE '✅ current_members verified (10)';
    END IF;

    IF (SELECT count(*) FROM public.contributions WHERE group_id = v_group_id AND status = 'paid') != 15 THEN
        RAISE WARNING '❌ INTEGRITY ERROR: contribution count mismatch';
    ELSE
        RAISE NOTICE '✅ Paid contributions verified (15)';
    END IF;

    IF (SELECT balance FROM public.groups WHERE id = v_group_id) <= 0 THEN
        RAISE WARNING '❌ INTEGRITY ERROR: Balance should be positive';
    ELSE
        RAISE NOTICE '✅ Balance verified: %', (SELECT balance FROM public.groups WHERE id = v_group_id);
    END IF;

    RAISE NOTICE '🚀 SIMULATION COMPLETE. Test results logged to audit trail.';
END $$;
