DO $$
DECLARE
    v_member RECORD;
    v_loan_id UUID;
    v_success_count INT := 0;
    v_reject_count INT := 0;
    v_bug_count INT := 0;
BEGIN
    RAISE NOTICE 'Starting Simulation...';

    FOR v_member IN
        SELECT m.id as member_id, m.group_id, g.type as group_type, g.balance, g.monthly_contribution, g.admin_user_id
        FROM public.members m
        JOIN public.groups g ON m.group_id = g.id
        WHERE m.status = 'ACTIVE'
        LIMIT 30
    LOOP
        -- 1. Valid Contribution
        BEGIN
            PERFORM public.record_contribution_v2(
                v_member.member_id, v_member.group_id, v_member.monthly_contribution,
                CURRENT_DATE, CURRENT_DATE, 'paid',
                'sim-tx-' || gen_random_uuid(), 'contribution'
            );
            v_success_count := v_success_count + 1;
        EXCEPTION WHEN OTHERS THEN
            RAISE NOTICE 'BUG|Valid Contribution|%|%', SQLSTATE, SQLERRM;
            v_bug_count := v_bug_count + 1;
        END;

        -- 2. Invalid Contribution (Negative)
        BEGIN
            PERFORM public.record_contribution_v2(
                v_member.member_id, v_member.group_id, -50.00,
                CURRENT_DATE, CURRENT_DATE, 'paid',
                'sim-tx-neg-' || gen_random_uuid(), 'contribution'
            );
            RAISE NOTICE 'BUG|Negative Contribution Allowed|Allowed negative amount.';
            v_bug_count := v_bug_count + 1;
        EXCEPTION WHEN OTHERS THEN
            RAISE NOTICE 'REJECT|Negative Contribution Blocked|%|%', SQLSTATE, SQLERRM;
            v_reject_count := v_reject_count + 1;
        END;

        -- 3. Loan Logic (If eligible)
        IF v_member.group_type NOT IN ('rosca', 'investment_club') THEN
            BEGIN
                INSERT INTO public.loans (
                    id, member_id, group_id, amount, interest_rate, total_to_repay,
                    total_repaid, monthly_repayment, start_date, end_date, status, purpose
                ) VALUES (
                    gen_random_uuid(), v_member.member_id, v_member.group_id, 500.00, 5.0, 525.00,
                    0, 525.00, CURRENT_DATE, CURRENT_DATE + interval '30 days', 'pending', 'Simulated Emergency'
                ) RETURNING id INTO v_loan_id;
                v_success_count := v_success_count + 1;

                -- Admin Disburse
                PERFORM public.approve_and_disburse_loan_v1(v_loan_id, v_member.admin_user_id);
                v_success_count := v_success_count + 1;

                -- Partial Repayment
                PERFORM public.record_loan_repayment_v1(v_loan_id, v_member.member_id, v_member.group_id, 100.00, 'bank');
                v_success_count := v_success_count + 1;

                -- Over-repayment
                BEGIN
                    PERFORM public.record_loan_repayment_v1(v_loan_id, v_member.member_id, v_member.group_id, 50000.00, 'bank');
                    RAISE NOTICE 'BUG|Loan Overpayment Allowed|DB allowed overpayment.';
                    v_bug_count := v_bug_count + 1;
                EXCEPTION WHEN OTHERS THEN
                    RAISE NOTICE 'REJECT|Loan Overpayment Blocked|%|%', SQLSTATE, SQLERRM;
                    v_reject_count := v_reject_count + 1;
                END;

            EXCEPTION WHEN OTHERS THEN
                IF SQLERRM LIKE '%Insufficient%' OR SQLERRM LIKE '%exceeds%' THEN
                    RAISE NOTICE 'REJECT|Loan Action Blocked|%|%', SQLSTATE, SQLERRM;
                    v_reject_count := v_reject_count + 1;
                ELSE
                    RAISE NOTICE 'BUG|Loan Action Failed|%|%', SQLSTATE, SQLERRM;
                    v_bug_count := v_bug_count + 1;
                END IF;
            END;
        END IF;

    END LOOP;

    RAISE NOTICE 'SIM_DONE|Success:%|Reject:%|Bug:%', v_success_count, v_reject_count, v_bug_count;
END;
$$;
