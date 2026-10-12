-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — LIVE SIMULATION V2 (NO CLEANUP)
-- Version: 2.7 (August 2, 2026)
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
BEGIN
    -- 0. SETUP: Identify a valid admin user ID from the database
    SELECT id INTO v_admin_id FROM auth.users WHERE email = 'torrymsimango@gmail.com' LIMIT 1;
    IF v_admin_id IS NULL THEN SELECT id INTO v_admin_id FROM auth.users LIMIT 1; END IF;

    IF v_admin_id IS NULL THEN
        RAISE EXCEPTION 'No users found in auth.users. Run seed data first.';
    END IF;

    -- 1. SETUP: Find or Create a test group
    SELECT id INTO v_group_id FROM public.groups WHERE name = 'SIM_STRESS_TEST_GROUP_NEW_4' LIMIT 1;
    IF v_group_id IS NULL THEN
        INSERT INTO public.groups (name, type, monthly_contribution, joining_fee, late_fee, late_fee_grace_days, admin_user_id, description)
        VALUES ('SIM_STRESS_TEST_GROUP_NEW_4', 'burial_society', 200.00, 500.00, 50.00, 5, v_admin_id, 'SIMULATION_DEBUG: Fresh Stress Test Group 4')
        RETURNING id INTO v_group_id;
    END IF;

    -- 2. SETUP: Create a test member
    SELECT id INTO v_member_id FROM public.members WHERE full_name = 'Simulated Stress Member' AND group_id = v_group_id LIMIT 1;
    IF v_member_id IS NULL THEN
        INSERT INTO public.members (group_id, full_name, id_number, phone, status, joined_at, member_key)
        VALUES (v_group_id, 'Simulated Stress Member', '8001015000081', '0710000001', 'active', (NOW() - INTERVAL '3 months')::DATE, 'STRESS-MEMBER-' || v_group_id)
        RETURNING id INTO v_member_id;
    END IF;

    v_initial_balance := (SELECT COALESCE(balance, 0) FROM public.groups WHERE id = v_group_id);
    v_member_paid_initial := (SELECT COALESCE(total_paid, 0) FROM public.members WHERE id = v_member_id);

    -- 🛡️ Hardening: Ensure group has enough liquidity for the test loan (R1000)
    IF v_initial_balance < 2000.00 THEN
        PERFORM public.increment_group_balance(v_group_id, 2000.00, 'SIMULATION_DEBUG: Liquidity Injection', 'injection', 'STRESS-INIT-' || v_group_id, v_member_id);
        v_initial_balance := v_initial_balance + 2000.00;
    END IF;

    -- 3. SETUP: Create an active loan
    INSERT INTO public.loans (member_id, group_id, amount, interest_rate, total_to_repay, monthly_repayment, status, start_date)
    VALUES (v_member_id, v_group_id, 1000.00, 10.0, 1100.00, 366.67, 'active', (NOW() - INTERVAL '1 month')::DATE)
    RETURNING id INTO v_loan_id;

    -- 🛡️ Hardening: Manually record the disbursement to keep ledger in sync with status
    PERFORM public.increment_group_balance(v_group_id, -1000.00, 'Loan Disbursement: Simulated Stress Member', 'loan_disbursement', 'STRESS-DISB-' || v_loan_id, v_member_id);

    RAISE NOTICE '--- STARTING STRESS TEST (PERSIST) ---';

    -- 4. ACTION: Process a Complex Payment
    SELECT public.process_complex_payment_v4(
        p_group_id := v_group_id,
        p_member_id := v_member_id,
        p_total_amount := 1000.00,
        p_tx_id := 'stress_tx_persist_' || floor(random()*1000000),
        p_allocations := (
            SELECT jsonb_agg(x) FROM (
                SELECT 'member_fee_ledger' as type, 10.00 as amount, '2026-04-01'::date as due_date, NULL::uuid as loan_id, 'Platform Fee' as description
                UNION ALL SELECT 'member_fee_ledger', 10.00, '2026-05-01', NULL, 'Platform Fee'
                UNION ALL SELECT 'late_fee', 50.00, CURRENT_DATE, NULL, 'Late Fee'
                UNION ALL SELECT 'loan_repayment', 366.67, CURRENT_DATE, v_loan_id, 'Loan Repayment'
                UNION ALL SELECT 'contribution', 563.33, '2026-06-01', NULL, 'Contribution'
            ) x
        ),
        p_method := 'bank',
        p_payment_type := 'contribution',
        p_destination := 'group'
    ) INTO v_res;

    -- 5. ACTION: Record a raw bank transaction for reconciliation
    INSERT INTO public.bank_transactions (group_id, bank_reference, amount, transaction_date, status)
    VALUES (v_group_id, 'RECON_TEST_REF_PERSIST', 1000.00, NOW()::DATE, 'unreconciled');

    -- 6. VERIFICATION (Embedded)
    IF (SELECT COUNT(*) FROM public.group_ledger WHERE member_id = v_member_id) >= 3 THEN
        v_success_count := v_success_count + 1;
        RAISE NOTICE 'SUCCESS: Ledger entries partitioned.';
    END IF;

    v_member_paid_final := (SELECT total_paid FROM public.members WHERE id = v_member_id);
    -- Expected: initial + 563.33
    IF ABS(v_member_paid_final - (v_member_paid_initial + 563.33)) < 0.01 THEN
        v_success_count := v_success_count + 1;
        RAISE NOTICE 'SUCCESS: Member total_paid synchronized.';
    END IF;

    IF ABS((SELECT total_repaid FROM public.loans WHERE id = v_loan_id) - 366.67) < 0.01 THEN
        v_success_count := v_success_count + 1;
        RAISE NOTICE 'SUCCESS: Loan total_repaid synchronized.';
    END IF;

    v_final_balance := (SELECT balance FROM public.groups WHERE id = v_group_id);
    -- Expected: initial + 980.00
    IF ABS(v_final_balance - (v_initial_balance + 980.00)) < 0.01 THEN
        v_success_count := v_success_count + 1;
        RAISE NOTICE 'SUCCESS: Group balance updated.';
    END IF;

    PERFORM public.recalculate_all_group_financials_v1(v_group_id);

    IF ABS((SELECT balance FROM public.groups WHERE id = v_group_id) - v_final_balance) < 0.01 THEN
        v_success_count := v_success_count + 1;
        RAISE NOTICE 'SUCCESS: Data repair verified.';
    END IF;

    -- LOG FINAL RESULT
    CREATE TABLE IF NOT EXISTS public.simulation_results (
        id SERIAL PRIMARY KEY,
        test_name TEXT,
        success_count INT,
        total_tests INT,
        final_balance NUMERIC,
        member_paid NUMERIC,
        created_at TIMESTAMP DEFAULT NOW()
    );

    INSERT INTO public.simulation_results (test_name, success_count, total_tests, final_balance, member_paid)
    VALUES ('LIVE_SIMULATION_V2_PERSIST', v_success_count, 5, v_final_balance, v_member_paid_final);

    RAISE NOTICE '--- STRESS TEST (PERSIST) COMPLETE ---';
    RAISE NOTICE 'Final Success Count: %/5', v_success_count;
END;
$$;
