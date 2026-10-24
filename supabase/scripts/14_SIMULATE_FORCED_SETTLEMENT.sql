-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — FORCED LOAN SETTLEMENT SIMULATION
-- ─────────────────────────────────────────────────────────────────────────────

DO $$
DECLARE
    v_admin_id UUID;
    v_group_id UUID;
    v_member_id UUID;
    v_loan_id UUID;
    v_initial_savings NUMERIC := 5000.00;
    v_loan_balance NUMERIC := 1500.00;
    v_group_name TEXT := 'SIM-SETTLEMENT-' || floor(random() * 1000);
BEGIN
    RAISE NOTICE '🚀 Starting Forced Settlement Simulation for %', v_group_name;

    -- 1. Setup Environment
    SELECT id INTO v_admin_id FROM auth.users LIMIT 1;

    INSERT INTO public.groups (
        name, type, balance, admin_user_id, registration_paid,
        province, city, township, latitude, longitude
    )
    VALUES (
        v_group_name, 'stokvel', 0.00, v_admin_id, TRUE,
        'Gauteng', 'Johannesburg', 'Soweto', -26.2361, 27.8583
    ) RETURNING id INTO v_group_id;

    -- Authoritative Liquidity Injection
    PERFORM public.increment_group_balance(
        v_group_id, 10000.00,
        'Initial System Liquidity',
        'adjustment',
        'INIT-LIQ-' || v_group_id
    );

    INSERT INTO public.members (group_id, full_name, status, joined_at, member_key)
    VALUES (v_group_id, 'Test Borrower', 'active', now() - interval '180 days', 'SIM-EXTRACT-' || v_group_id)
    RETURNING id INTO v_member_id;

    -- Record Initial Savings as a contribution to satisfy trigger-based total_paid
    PERFORM public.record_contribution_v2(
        v_member_id, v_group_id, v_initial_savings,
        (now() - interval '180 days')::date,
        now() - interval '179 days',
        'paid',
        'INIT-SAVINGS-' || v_member_id,
        'contribution',
        'Initial Savings Principal'
    );

    -- 2. Create Expired Loan (4 months old)
    INSERT INTO public.loans (
        member_id, group_id, amount, interest_rate, total_to_repay, total_repaid,
        monthly_repayment, start_date, status, purpose
    ) VALUES (
        v_member_id, v_group_id, 1500.00, 0.0, v_loan_balance, 0.00,
        500.00, (now() - interval '120 days')::date, 'active', 'SIM-FORCED'
    ) RETURNING id INTO v_loan_id;

    RAISE NOTICE '--- PRE-SETTLEMENT STATE ---';
    RAISE NOTICE 'Member Savings: %', (SELECT total_paid FROM public.members WHERE id = v_member_id);
    RAISE NOTICE 'Loan Balance: %', (SELECT total_to_repay - total_repaid FROM public.loans WHERE id = v_loan_id);

    -- 3. Trigger Forced Extraction (Simulating App UseCase call to RPC)
    PERFORM public.record_loan_settlement_extraction_v1(
        v_member_id,
        v_group_id,
        v_loan_balance,
        v_loan_id
    );

    RAISE NOTICE '--- POST-SETTLEMENT VERIFICATION ---';

    -- Verification 1: Member Savings Deducted
    IF (SELECT total_paid FROM public.members WHERE id = v_member_id) != (v_initial_savings - v_loan_balance) THEN
        RAISE EXCEPTION '❌ INTEGRITY FAILURE: Member savings not accurately deducted. Got %', (SELECT total_paid FROM public.members WHERE id = v_member_id);
    ELSE
        RAISE NOTICE '✅ Member savings updated correctly: %', (SELECT total_paid FROM public.members WHERE id = v_member_id);
    END IF;

    -- Verification 2: Loan Status
    IF (SELECT status FROM public.loans WHERE id = v_loan_id) != 'completed' THEN
        RAISE EXCEPTION '❌ INTEGRITY FAILURE: Loan status not completed. Got %', (SELECT status FROM public.loans WHERE id = v_loan_id);
    ELSE
        RAISE NOTICE '✅ Loan status marked as completed';
    END IF;

    -- Verification 3: Ledger Entry
    IF NOT EXISTS (SELECT 1 FROM public.group_ledger WHERE group_id = v_group_id AND category = 'loan_settlement') THEN
        RAISE EXCEPTION '❌ INTEGRITY FAILURE: No loan_settlement entry found in group_ledger';
    ELSE
        RAISE NOTICE '✅ Ledger entry created with balance: %', (SELECT balance_after FROM public.group_ledger WHERE group_id = v_group_id AND category = 'loan_settlement' LIMIT 1);
    END IF;

    RAISE NOTICE '🚀 FORCED SETTLEMENT SIMULATION SUCCESSFUL';
END $$;
