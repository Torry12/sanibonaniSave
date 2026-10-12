-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — LIVE SIMULATION V2 (PERSIST FOR DEBUG)
-- Version: 2.8 (August 4, 2026)
-- ─────────────────────────────────────────────────────────────────────────────

DO $$
DECLARE
    v_group_id UUID;
    v_member_id UUID;
    v_admin_id UUID;
    v_loan_id UUID;
    v_res JSONB;
    v_initial_balance NUMERIC;
    v_final_balance NUMERIC;
    v_member_paid_initial NUMERIC;
    v_member_paid_final NUMERIC;
    v_success_count INT := 0;
    v_ledger_entry RECORD;
    v_group_name TEXT := 'SIM_STRESS_TEST_' || floor(random()*1000000);
BEGIN
    -- 0. SETUP: Identify a valid admin user ID from the database
    SELECT id INTO v_admin_id FROM auth.users WHERE email = 'torrymsimango@gmail.com' LIMIT 1;
    IF v_admin_id IS NULL THEN SELECT id INTO v_admin_id FROM auth.users LIMIT 1; END IF;

    -- 1. SETUP: Create a test group
    INSERT INTO public.groups (name, type, monthly_contribution, joining_fee, late_fee, late_fee_grace_days, admin_user_id, description)
    VALUES (v_group_name, 'burial_society', 200.00, 500.00, 50.00, 5, v_admin_id, 'SIMULATION_DEBUG: Fresh Stress Test Group')
    RETURNING id INTO v_group_id;

    RAISE NOTICE 'Using Group ID: % (%)', v_group_id, v_group_name;

    -- 2. SETUP: Create a test member
    INSERT INTO public.members (group_id, full_name, id_number, phone, status, joined_at, member_key)
    VALUES (v_group_id, 'Simulated Stress Member', '8001015000081', '0710000001', 'active', (NOW() - INTERVAL '3 months')::DATE, 'STRESS-MEMBER-' || v_group_id)
    RETURNING id INTO v_member_id;

    -- 3. SETUP: Create an active loan
    INSERT INTO public.loans (member_id, group_id, amount, interest_rate, total_to_repay, monthly_repayment, status, start_date)
    VALUES (v_member_id, v_group_id, 1000.00, 10.0, 1100.00, 366.67, 'pending', (NOW() - INTERVAL '1 month')::DATE)
    RETURNING id INTO v_loan_id;

    -- 🛡️ Hardening: Ensure group has enough liquidity for the test loan (R1000)
    PERFORM public.increment_group_balance(v_group_id, 2000.00, 'SIMULATION_DEBUG: Initial Injection', 'injection', 'STRESS-INIT-' || v_group_id, v_member_id);

    -- 🛡️ Hardening: Use the RPC to disburse the loan correctly
    PERFORM public.approve_and_disburse_loan_v1(v_loan_id, v_admin_id);

    v_initial_balance := (SELECT COALESCE(balance, 0) FROM public.groups WHERE id = v_group_id);
    v_member_paid_initial := (SELECT COALESCE(total_paid, 0) FROM public.members WHERE id = v_member_id);

    RAISE NOTICE '--- STARTING STRESS TEST ---';

    -- 4. ACTION: Process a Complex Payment
    SELECT public.process_complex_payment_v4(
        p_group_id := v_group_id,
        p_member_id := v_member_id,
        p_total_amount := 1000.00,
        p_tx_id := 'stress_tx_' || floor(random()*1000000),
        p_allocations := (
            SELECT jsonb_agg(x) FROM (
                SELECT 'member_fee_ledger' as type, 10.00 as amount, '2026-04-01'::date as due_date, NULL::uuid as loan_id, 'System Fee' as description
                UNION ALL SELECT 'member_fee_ledger', 10.00, '2026-05-01', NULL, 'System Fee'
                UNION ALL SELECT 'late_fee', 50.00, CURRENT_DATE, NULL, 'Late Fee'
                UNION ALL SELECT 'loan_repayment', 366.67, CURRENT_DATE, v_loan_id, 'Loan Repayment'
                UNION ALL SELECT 'contribution', 563.33, '2026-06-01', NULL, 'Monthly Contribution'
            ) x
        ),
        p_method := 'bank',
        p_payment_type := 'contribution',
        p_destination := 'group'
    ) INTO v_res;

    v_final_balance := (SELECT balance FROM public.groups WHERE id = v_group_id);
    RAISE NOTICE 'Balance before repair: %', v_final_balance;

    -- 10. REPAIR TEST: Run Global Recalculation
    RAISE NOTICE 'Running Global Financial Repair...';
    PERFORM public.recalculate_all_group_financials_v1(v_group_id);

    RAISE NOTICE 'Balance after repair: %', (SELECT balance FROM public.groups WHERE id = v_group_id);

    RAISE NOTICE '--- STRESS TEST COMPLETE ---';
END;
$$;
