DO $$
DECLARE
    v_member RECORD;
    v_loan_id UUID;
    v_admin_id UUID;
    v_success_count INT := 0;
    v_reject_count INT := 0;
    v_bug_count INT := 0;
    v_iteration INT := 0;
BEGIN
    FOR v_member IN
        SELECT m.id as member_id, m.group_id, g.type as group_type, g.balance, g.monthly_contribution, g.admin_user_id
        FROM public.members m
        JOIN public.groups g ON m.group_id = g.id
        WHERE m.status = 'active' AND g.type NOT IN ('rosca', 'investment_club')
        LIMIT 20
    LOOP
        v_iteration := v_iteration + 1;

        IF v_iteration <= 10 THEN
            BEGIN
                INSERT INTO public.loans (
                    id, member_id, group_id, amount, interest_rate, total_to_repay,
                    total_repaid, monthly_repayment, start_date, end_date, status, purpose
                ) VALUES (
                    gen_random_uuid(), v_member.member_id, v_member.group_id, 100.00, 5.0, 105.00,
                    0, 105.00, CURRENT_DATE, CURRENT_DATE + interval '30 days', 'pending', 'Standard Approved Loan'
                ) RETURNING id INTO v_loan_id;

                PERFORM public.approve_and_disburse_loan_v1(v_loan_id, v_member.admin_user_id);
                v_success_count := v_success_count + 1;
            EXCEPTION WHEN OTHERS THEN
                v_bug_count := v_bug_count + 1;
            END;

        ELSIF v_iteration <= 15 THEN
            BEGIN
                INSERT INTO public.loans (
                    id, member_id, group_id, amount, interest_rate, total_to_repay,
                    total_repaid, monthly_repayment, start_date, end_date, status, purpose
                ) VALUES (
                    gen_random_uuid(), v_member.member_id, v_member.group_id, 200.00, 5.0, 210.00,
                    0, 210.00, CURRENT_DATE, CURRENT_DATE + interval '30 days', 'pending', 'Rejected Loan'
                ) RETURNING id INTO v_loan_id;

                UPDATE public.loans SET status = 'rejected' WHERE id = v_loan_id;
                v_success_count := v_success_count + 1;
            EXCEPTION WHEN OTHERS THEN
                v_bug_count := v_bug_count + 1;
            END;

        ELSE
            BEGIN
                INSERT INTO public.loans (
                    id, member_id, group_id, amount, interest_rate, total_to_repay,
                    total_repaid, monthly_repayment, start_date, end_date, status, purpose
                ) VALUES (
                    gen_random_uuid(), v_member.member_id, v_member.group_id, 9999999.00, 5.0, 10000000.00,
                    0, 1000000.00, CURRENT_DATE, CURRENT_DATE + interval '30 days', 'pending', 'Excessive Loan'
                ) RETURNING id INTO v_loan_id;

                BEGIN
                    PERFORM public.approve_and_disburse_loan_v1(v_loan_id, v_member.admin_user_id);
                    v_bug_count := v_bug_count + 1;
                EXCEPTION WHEN OTHERS THEN
                    v_reject_count := v_reject_count + 1;
                END;
            EXCEPTION WHEN OTHERS THEN
                v_bug_count := v_bug_count + 1;
            END;
        END IF;
    END LOOP;

    INSERT INTO public.simulation_results (test_name, success_count, total_tests)
    VALUES ('LOAN_SIMULATION_PERSIST', v_success_count, v_iteration);

END;
$$;
