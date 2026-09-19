-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — LIVE SIMULATION V2 (NO CLEANUP)
-- Version: 2.6
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
BEGIN
    -- 0. SETUP: Identify a valid admin user ID from the database
    SELECT id INTO v_admin_id FROM auth.users LIMIT 1;

    IF v_admin_id IS NULL THEN
        RAISE EXCEPTION 'No users found in auth.users. Run seed data first.';
    END IF;

    -- 1. SETUP: Find or Create a test group
    SELECT id INTO v_group_id FROM public.groups WHERE name = 'SIM_STRESS_TEST_GROUP_PERSIST' LIMIT 1;
    IF v_group_id IS NULL THEN
        INSERT INTO public.groups (name, type, monthly_contribution, joining_fee, late_fee, late_fee_grace_days, admin_user_id)
        VALUES ('SIM_STRESS_TEST_GROUP_PERSIST', 'burial_society', 200.00, 500.00, 50.00, 5, v_admin_id)
        RETURNING id INTO v_group_id;
    END IF;

    -- 2. SETUP: Create a test member
    SELECT id INTO v_member_id FROM public.members WHERE full_name = 'Simulated Stress Member' AND group_id = v_group_id LIMIT 1;
    IF v_member_id IS NULL THEN
        INSERT INTO public.members (group_id, full_name, id_number, phone, status, joined_at)
        VALUES (v_group_id, 'Simulated Stress Member', '8001015000081', '0710000001', 'active', (NOW() - INTERVAL '3 months')::DATE)
        RETURNING id INTO v_member_id;
    END IF;

    -- 3. SETUP: Create an active loan
    INSERT INTO public.loans (member_id, group_id, amount, interest_rate, total_to_repay, monthly_repayment, status, start_date)
    VALUES (v_member_id, v_group_id, 1000.00, 10.0, 1100.00, 366.67, 'active', (NOW() - INTERVAL '1 month')::DATE)
    RETURNING id INTO v_loan_id;

    v_initial_balance := (SELECT COALESCE(balance, 0) FROM public.groups WHERE id = v_group_id);
    v_member_paid_initial := (SELECT COALESCE(total_paid, 0) FROM public.members WHERE id = v_member_id);

    RAISE NOTICE '--- STARTING STRESS TEST ---';

    -- 4. ACTION: Process a Complex Payment
    SELECT public.process_complex_payment_v1(
        p_group_id := v_group_id,
        p_member_id := v_member_id,
        p_total_amount := 1000.00,
        p_tx_id := 'stress_tx_' || floor(random()*1000000),
        p_allocations := ('[
            {"type": "member_fee", "amount": 10.00, "due_date": "2026-04-01"},
            {"type": "member_fee", "amount": 10.00, "due_date": "2026-05-01"},
            {"type": "late_fee", "amount": 50.00, "description": "Late Fee Penalty", "due_date": "' || NOW()::DATE || '"},
            {"type": "loan_repayment", "amount": 366.67, "loan_id": "' || v_loan_id || '", "due_date": "' || NOW()::DATE || '"},
            {"type": "contribution", "amount": 563.33, "due_date": "2026-06-01"}
        ]')::JSONB,
        p_method := 'bank'
    ) INTO v_res;

    -- 5. ACTION: Record a raw bank transaction for reconciliation
    INSERT INTO public.bank_transactions (group_id, bank_reference, amount, transaction_date, status)
    VALUES (v_group_id, 'RECON_TEST_REF', 1000.00, NOW()::DATE, 'unreconciled');

    -- 6. VERIFICATION (Embedded)
    IF (SELECT COUNT(*) FROM public.group_ledger WHERE member_id = v_member_id) >= 3 THEN
        v_success_count := v_success_count + 1;
    END IF;

    v_member_paid_final := (SELECT total_paid FROM public.members WHERE id = v_member_id);
    IF ABS(v_member_paid_final - (v_member_paid_initial + 930.00)) < 0.01 THEN
        v_success_count := v_success_count + 1;
    END IF;

    IF ABS((SELECT total_repaid FROM public.loans WHERE id = v_loan_id) - 366.67) < 0.01 THEN
        v_success_count := v_success_count + 1;
    END IF;

    v_final_balance := (SELECT balance FROM public.groups WHERE id = v_group_id);
    IF ABS(v_final_balance - (v_initial_balance + 980.00)) < 0.01 THEN
        v_success_count := v_success_count + 1;
    END IF;

    PERFORM public.recalculate_all_group_financials_v1(v_group_id);

    IF ABS((SELECT balance FROM public.groups WHERE id = v_group_id) - v_final_balance) < 0.01 THEN
        v_success_count := v_success_count + 1;
    END IF;

    -- LOG FINAL RESULT to a table instead of NOTICE
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

END;
$$;
