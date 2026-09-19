-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — ATOMIC TRANSACTION SIMULATION
-- Tests complex multi-allocation payments and data integrity triggers.
-- ─────────────────────────────────────────────────────────────────────────────

DO $$
DECLARE
    v_member RECORD;
    v_loan_id UUID;
    v_success_count INT := 0;
    v_error_count INT := 0;
    v_allocations JSONB;
    v_tx_id TEXT;
    v_member_paid_before NUMERIC;
    v_member_paid_after NUMERIC;
BEGIN
    RAISE NOTICE 'Starting Atomic Simulation...';

    FOR v_member IN
        SELECT m.id as member_id, m.group_id, g.monthly_contribution, m.total_paid
        FROM public.members m
        JOIN public.groups g ON m.group_id = g.id
        WHERE m.status IN ('active', 'probation')
        LIMIT 10
    LOOP
        v_tx_id := 'sim-atomic-' || gen_random_uuid();
        v_member_paid_before := v_member.total_paid;

        -- 1. Create a loan to test repayment in complex payment
        INSERT INTO public.loans (
            id, member_id, group_id, amount, interest_rate, total_to_repay,
            total_repaid, monthly_repayment, start_date, status, purpose
        ) VALUES (
            gen_random_uuid(), v_member.member_id, v_member.group_id, 200.00, 0, 200.00,
            0, 100.00, CURRENT_DATE, 'active', 'Atomic Sim'
        ) RETURNING id INTO v_loan_id;

        -- 2. Build Complex Allocations: R10 Member Fee + R100 Loan + Remainder (Monthly Contribution)
        -- Total: 10 + 100 + monthly_contribution
        v_allocations := jsonb_build_array(
            jsonb_build_object('type', 'member_fee', 'amount', 10.0, 'due_date', CURRENT_DATE),
            jsonb_build_object('type', 'loan_repayment', 'amount', 100.0, 'loan_id', v_loan_id),
            jsonb_build_object('type', 'contribution', 'amount', v_member.monthly_contribution, 'due_date', CURRENT_DATE)
        );

        -- 3. Execute ATOMIC Payment
        BEGIN
            PERFORM public.process_complex_payment_v1(
                v_member.group_id,
                v_member.member_id,
                110.0 + v_member.monthly_contribution,
                v_tx_id,
                v_allocations
            );

            -- 4. VERIFY INTEGRITY (Trigger Check)
            SELECT total_paid INTO v_member_paid_after FROM public.members WHERE id = v_member.member_id;

            -- Loan repayment (100) + Contribution (monthly) should be added.
            -- Member fee (10) should NOT be added to total_paid (it's revenue).
            IF v_member_paid_after != v_member_paid_before + 100.0 + v_member.monthly_contribution THEN
                RAISE NOTICE 'INTEGRITY BUG|Total Paid Mismatch|Member % | Expected % | Got %',
                    v_member.member_id, v_member_paid_before + 100.0 + v_member.monthly_contribution, v_member_paid_after;
                v_error_count := v_error_count + 1;
            ELSE
                v_success_count := v_success_count + 1;
            END IF;

        EXCEPTION WHEN OTHERS THEN
            RAISE NOTICE 'SIM ERROR|%|%', SQLSTATE, SQLERRM;
            v_error_count := v_error_count + 1;
        END;
    END LOOP;

    RAISE NOTICE 'ATOMIC_SIM_DONE|Success:%|Error:%', v_success_count, v_error_count;
END;
$$;
