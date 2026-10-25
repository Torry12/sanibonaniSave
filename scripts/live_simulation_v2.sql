-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — LIVE SIMULATION V2
-- Version: 2.7 (High-Fidelity Financial stress Test - Refactored Equity Logic)
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

    IF v_admin_id IS NULL THEN
        RAISE EXCEPTION 'No users found in auth.users. Run seed data first.';
    END IF;

    -- 1. SETUP: Create a unique test group for this run
    INSERT INTO public.groups (
        name, type, province, city, township,
        monthly_contribution, joining_fee, late_fee, late_fee_grace_days,
        admin_user_id, description, latitude, longitude
    )
    VALUES (
        v_group_name, 'burial_society', 'Gauteng', 'Johannesburg', 'Soweto',
        200.00, 500.00, 50.00, 5,
        v_admin_id, 'SIMULATION_DEBUG: Fresh Isolated Stress Test Group',
        -26.2361, 27.8583
    )
    RETURNING id INTO v_group_id;

    RAISE NOTICE 'Using Group ID: % (%)', v_group_id, v_group_name;

    -- 2. SETUP: Create a test member
    INSERT INTO public.members (group_id, full_name, id_number, phone, status, joined_at, member_key)
    VALUES (v_group_id, 'Simulated Stress Member', '8001015000081', '0710000001', 'active', (NOW() - INTERVAL '3 months')::DATE, 'STRESS-MEMBER-' || v_group_id)
    RETURNING id INTO v_member_id;

    RAISE NOTICE 'Using Member ID: %', v_member_id;

    -- 3. SETUP: Create an active loan
    INSERT INTO public.loans (member_id, group_id, amount, interest_rate, total_to_repay, monthly_repayment, status, start_date)
    VALUES (v_member_id, v_group_id, 1000.00, 10.0, 1100.00, 366.67, 'pending', (NOW() - INTERVAL '1 month')::DATE)
    RETURNING id INTO v_loan_id;

    -- 🛡️ Hardening: Ensure group has enough liquidity for the test loan (R1000)
    PERFORM public.increment_group_balance(v_group_id, 2000.00, 'SIMULATION_DEBUG: Initial Injection', 'injection', 'STRESS-INIT-' || v_group_id, v_member_id);

    -- 🛡️ Hardening: Use the RPC to disburse the loan correctly
    -- This RPC now uses p_loan_id::text as the transaction_id in group_ledger
    PERFORM public.approve_and_disburse_loan_v2(v_loan_id, v_admin_id, 'bank');

    v_initial_balance := (SELECT COALESCE(balance, 0) FROM public.groups WHERE id = v_group_id);
    v_member_paid_initial := (SELECT COALESCE(total_paid, 0) FROM public.members WHERE id = v_member_id);

    RAISE NOTICE '--- STARTING STRESS TEST ---';
    RAISE NOTICE 'Initial Group Balance: %', v_initial_balance;
    RAISE NOTICE 'Initial Member Paid: %', v_member_paid_initial;

    -- 4. ACTION: Process a Complex Payment
    -- Allocation:
    -- 2x member_fee (excluded from member.total_paid)
    -- 1x late_fee (excluded from member.total_paid)
    -- 1x loan_repayment (excluded from member.total_paid - NEW ARCHITECTURE)
    -- 1x contribution (included in member.total_paid)
    RAISE NOTICE 'Executing Complex Payment: R1000.00';
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

    -- 5. ACTION: Record a raw bank transaction for reconciliation
    RAISE NOTICE 'Recording Bank Transaction for matching...';
    INSERT INTO public.bank_transactions (group_id, bank_reference, amount, transaction_date, status)
    VALUES (v_group_id, 'RECON_TEST_REF', 1000.00, NOW()::DATE, 'unreconciled');

    -- 6. VERIFICATION: Check Ledger Entries
    RAISE NOTICE 'Listing Ledger Entries:';
    FOR v_ledger_entry IN SELECT category, amount, balance_after, description FROM public.group_ledger WHERE group_id = v_group_id LOOP
        RAISE NOTICE '  - %: R% (Balance After: R%) - %', v_ledger_entry.category, v_ledger_entry.amount, v_ledger_entry.balance_after, v_ledger_entry.description;
    END LOOP;

    IF (SELECT COUNT(*) FROM public.group_ledger WHERE member_id = v_member_id) >= 3 THEN
        v_success_count := v_success_count + 1;
        RAISE NOTICE 'SUCCESS: Ledger entries partitioned correctly.';
    ELSE
        RAISE NOTICE 'Ledger count: %', (SELECT COUNT(*) FROM public.group_ledger WHERE member_id = v_member_id);
        RAISE EXCEPTION 'FAILURE: Ledger partitioning failed.';
    END IF;

    -- 7. VERIFICATION: Check Member Totals (via Trigger)
    v_member_paid_final := (SELECT total_paid FROM public.members WHERE id = v_member_id);
    -- Expected: initial + 563.33 (contribution)
    -- Loan repayments (366.67) and fees (70.00) are excluded from total_paid to reflect core equity.
    IF ABS(v_member_paid_final - (v_member_paid_initial + 563.33)) < 0.01 THEN
        v_success_count := v_success_count + 1;
        RAISE NOTICE 'SUCCESS: Member total_paid synchronized correctly (Investment principal only).';
    ELSE
        RAISE NOTICE 'Member final: %, expected: %', v_member_paid_final, v_member_paid_initial + 563.33;
        RAISE EXCEPTION 'FAILURE: Member total_paid mismatch.';
    END IF;

    -- 8. VERIFICATION: Check Loan Status (via Trigger)
    IF ABS((SELECT total_repaid FROM public.loans WHERE id = v_loan_id) - 366.67) < 0.01 THEN
        v_success_count := v_success_count + 1;
        RAISE NOTICE 'SUCCESS: Loan total_repaid synchronized correctly via trigger.';
    ELSE
        RAISE NOTICE 'Loan repaid: %, expected: 366.67', (SELECT total_repaid FROM public.loans WHERE id = v_loan_id);
        RAISE EXCEPTION 'FAILURE: Loan total_repaid mismatch.';
    END IF;

    -- 9. VERIFICATION: Check Group Balance
    v_final_balance := (SELECT balance FROM public.groups WHERE id = v_group_id);
    -- Group balance should increase by EVERYTHING EXCEPT platform fees (R20)
    -- Increase: 50.00 (late_fee) + 366.67 (loan) + 563.33 (contribution) = 980.00
    IF ABS(v_final_balance - (v_initial_balance + 980.00)) < 0.01 THEN
        v_success_count := v_success_count + 1;
        RAISE NOTICE 'SUCCESS: Group balance updated correctly (Excluding platform revenue).';
    ELSE
        RAISE NOTICE 'Final balance: %, expected: %', v_final_balance, v_initial_balance + 980.00;
        RAISE EXCEPTION 'FAILURE: Group balance drift detected.';
    END IF;

    -- 10. REPAIR TEST: Run Global Recalculation
    RAISE NOTICE 'Running Global Financial Repair...';
    PERFORM public.recalculate_all_group_financials_v1(v_group_id);

    -- Verify balance still matches after repair
    IF ABS((SELECT balance FROM public.groups WHERE id = v_group_id) - v_final_balance) < 0.01 THEN
        v_success_count := v_success_count + 1;
        RAISE NOTICE 'SUCCESS: Post-repair consistency verified.';
    ELSE
        RAISE NOTICE 'Balance after repair: %, expected: %', (SELECT balance FROM public.groups WHERE id = v_group_id), v_final_balance;
        RAISE EXCEPTION 'FAILURE: Data repair introduced drift.';
    END IF;

    RAISE NOTICE '--- STRESS TEST COMPLETE ---';
    RAISE NOTICE 'Final Success Count: %/5', v_success_count;

    -- Cleanup stress test data
    DELETE FROM public.loan_repayments WHERE loan_id = v_loan_id;
    DELETE FROM public.loans WHERE id = v_loan_id;
    DELETE FROM public.contributions WHERE member_id = v_member_id;
    DELETE FROM public.group_ledger WHERE member_id = v_member_id;
    DELETE FROM public.members WHERE id = v_member_id;
    DELETE FROM public.bank_transactions WHERE group_id = v_group_id;
    DELETE FROM public.groups WHERE id = v_group_id;

END;
$$;
