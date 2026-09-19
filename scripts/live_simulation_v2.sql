-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — LIVE SIMULATION V2
-- Version: 2.0 (High-Fidelity Financial stress Test)
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
    -- 1. SETUP: Find or Create a test group
    SELECT id INTO v_group_id FROM public.groups WHERE name = 'SIM_STRESS_TEST_GROUP' LIMIT 1;
    IF v_group_id IS NULL THEN
        INSERT INTO public.groups (name, type, monthly_contribution, registration_fee, late_fee, late_fee_grace_days)
        VALUES ('SIM_STRESS_TEST_GROUP', 'burial_society', 200.00, 500.00, 50.00, 5)
        RETURNING id INTO v_group_id;
    END IF;

    -- 2. SETUP: Create a test member
    SELECT id INTO v_member_id FROM public.members WHERE full_name = 'Simulated Stress Member' LIMIT 1;
    IF v_member_id IS NULL THEN
        INSERT INTO public.members (group_id, full_name, id_number, phone, status, joined_at)
        VALUES (v_group_id, 'Simulated Stress Member', '8001015000081', '0710000001', 'active', (NOW() - INTERVAL '3 months')::DATE)
        RETURNING id INTO v_member_id;
    END IF;

    -- 3. SETUP: Create an active loan
    INSERT INTO public.loans (member_id, group_id, amount, interest_rate, total_to_repay, monthly_repayment, status, start_date)
    VALUES (v_member_id, v_group_id, 1000.00, 10.0, 1100.00, 366.67, 'active', (NOW() - INTERVAL '1 month')::DATE)
    RETURNING id INTO v_loan_id;

    v_initial_balance := (SELECT balance FROM public.groups WHERE id = v_group_id);
    v_member_paid_initial := (SELECT total_paid FROM public.members WHERE id = v_member_id);

    RAISE NOTICE '--- STARTING STRESS TEST ---';
    RAISE NOTICE 'Initial Group Balance: %', v_initial_balance;
    RAISE NOTICE 'Initial Member Paid: %', v_member_paid_initial;

    -- 4. ACTION: Process a Complex Payment
    -- Scenario: Member is 3 months behind.
    -- Payment covers: 2x platform fees (R20), 1x late fee (R50), 1x loan installment (R366.67), balance to contribution.
    -- Total paid: R1000.00

    RAISE NOTICE 'Executing Complex Payment: R1000.00';
    SELECT public.process_complex_payment_v1(
        p_group_id := v_group_id,
        p_member_id := v_member_id,
        p_total_amount := 1000.00,
        p_tx_id := 'stress_tx_' || floor(random()*1000000),
        p_allocations := '[
            {"type": "member_fee", "amount": 10.00, "due_date": "2026-04-01"},
            {"type": "member_fee", "amount": 10.00, "due_date": "2026-05-01"},
            {"type": "adjustment", "amount": 50.00, "description": "Late Fee"},
            {"type": "loan_repayment", "amount": 366.67, "loan_id": "' || v_loan_id || '"},
            {"type": "contribution", "amount": 563.33, "due_date": "2026-06-01"}
        ]'::JSONB,
        p_method := 'bank'
    ) INTO v_res;

    -- 5. ACTION: Record a raw bank transaction for reconciliation
    RAISE NOTICE 'Recording Bank Transaction for matching...';
    INSERT INTO public.bank_transactions (group_id, bank_reference, amount, transaction_date, status)
    VALUES (v_group_id, 'RECON_TEST_REF', 1000.00, NOW()::DATE, 'unreconciled');

    -- 6. VERIFICATION: Check Ledger Entries
    IF (SELECT COUNT(*) FROM public.group_ledger WHERE member_id = v_member_id) >= 3 THEN
        v_success_count := v_success_count + 1;
        RAISE NOTICE 'SUCCESS: Ledger entries partitioned correctly.';
    ELSE
        RAISE EXCEPTION 'FAILURE: Ledger partitioning failed.';
    END IF;

    -- 7. VERIFICATION: Check Member Totals (via Trigger)
    v_member_paid_final := (SELECT total_paid FROM public.members WHERE id = v_member_id);
    -- Total Paid should be (1000 - 20) = 980.00 (Fees are excluded from member profile total)
    IF v_member_paid_final = v_member_paid_initial + 980.00 THEN
        v_success_count := v_success_count + 1;
        RAISE NOTICE 'SUCCESS: Member total_paid synchronized correctly (Excluding platform fees).';
    ELSE
        RAISE NOTICE 'Member final: %, expected: %', v_member_paid_final, v_member_paid_initial + 980.00;
        RAISE EXCEPTION 'FAILURE: Member total_paid mismatch.';
    END IF;

    -- 8. VERIFICATION: Check Loan Status (via Trigger)
    IF (SELECT total_repaid FROM public.loans WHERE id = v_loan_id) = 366.67 THEN
        v_success_count := v_success_count + 1;
        RAISE NOTICE 'SUCCESS: Loan total_repaid synchronized correctly via trigger.';
    ELSE
        RAISE EXCEPTION 'FAILURE: Loan total_repaid mismatch.';
    END IF;

    -- 9. VERIFICATION: Check Group Balance
    v_final_balance := (SELECT balance FROM public.groups WHERE id = v_group_id);
    -- Balance should increase by (50 + 366.67 + 563.33) = 980.00
    IF v_final_balance = v_initial_balance + 980.00 THEN
        v_success_count := v_success_count + 1;
        RAISE NOTICE 'SUCCESS: Group balance updated correctly (Excluding platform revenue).';
    ELSE
        RAISE EXCEPTION 'FAILURE: Group balance drift detected.';
    END IF;

    -- 10. REPAIR TEST: Run Global Recalculation
    RAISE NOTICE 'Running Global Financial Repair...';
    PERFORM public.recalculate_all_group_financials_v1(v_group_id);

    -- Verify balance still matches after repair
    IF (SELECT balance FROM public.groups WHERE id = v_group_id) = v_final_balance THEN
        v_success_count := v_success_count + 1;
        RAISE NOTICE 'SUCCESS: Post-repair consistency verified.';
    ELSE
        RAISE EXCEPTION 'FAILURE: Data repair introduced drift.';
    END IF;

    RAISE NOTICE '--- STRESS TEST COMPLETE ---';
    RAISE NOTICE 'Final Success Count: %/5', v_success_count;

    -- Cleanup stress test data
    DELETE FROM public.loans WHERE id = v_loan_id;
    DELETE FROM public.members WHERE id = v_member_id;
    DELETE FROM public.groups WHERE id = v_group_id;

END;
$$;
