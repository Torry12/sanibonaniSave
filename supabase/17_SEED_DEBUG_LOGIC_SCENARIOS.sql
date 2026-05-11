-- -----------------------------------------------------------------------------
-- SanibonaniSave - DEBUG LOGIC SEED (Deterministic Scenario Pack)
-- Purpose: seed focused data for business rules + programmatic flow debugging.
-- Idempotent: re-running replaces only DBG-* groups and DBG_* audit markers.
-- -----------------------------------------------------------------------------

DO $$
DECLARE
    v_admin_id UUID;
    v_group_id UUID;
    v_member_id UUID;
    v_loan_id UUID;
    v_beneficiary_id UUID;
    v_group_index INT;
    v_member_index INT;

    v_group_name TEXT;
    v_group_type TEXT;
    v_group_province TEXT;
    v_group_city TEXT;
    v_group_township TEXT;
    v_fee_status TEXT;
    v_payout_status_constraint TEXT := '';
    v_status_group_approved TEXT := 'group_approved';
    v_status_processing TEXT := 'processing';
    v_status_failed TEXT := 'failed';
    v_platform_fee_status_constraint TEXT := '';
    v_monthly_warning_status TEXT := 'warning';
    v_monthly_overdue_status TEXT := 'overdue';
BEGIN
    SELECT id
    INTO v_admin_id
    FROM auth.users
    WHERE lower(email) = lower('torrymsimango@gmail.com')
    LIMIT 1;

    IF v_admin_id IS NULL THEN
        RAISE EXCEPTION 'Platform admin user not found. Run 03_PLATFORM_ADMIN_SETUP.sql first.';
    END IF;

    -- Handle schema drift in payouts status CHECK constraint across environments.
    SELECT COALESCE(pg_get_constraintdef(c.oid), '')
    INTO v_payout_status_constraint
    FROM pg_constraint c
    WHERE c.conrelid = 'public.payouts'::regclass
      AND c.conname = 'payouts_status_check'
    LIMIT 1;

    IF position('group_approved' IN v_payout_status_constraint) = 0 THEN
        IF position('pending' IN v_payout_status_constraint) > 0 THEN
            v_status_group_approved := 'pending';
        ELSE
            v_status_group_approved := 'processing';
        END IF;
    END IF;

    IF position('processing' IN v_payout_status_constraint) = 0 THEN
        v_status_processing := v_status_group_approved;
    END IF;

    IF position('failed' IN v_payout_status_constraint) = 0 THEN
        IF position('cancelled' IN v_payout_status_constraint) > 0 THEN
            v_status_failed := 'cancelled';
        ELSE
            v_status_failed := v_status_processing;
        END IF;
    END IF;

    -- Handle schema drift in platform_fees status CHECK constraint across environments.
    SELECT COALESCE(pg_get_constraintdef(c.oid), '')
    INTO v_platform_fee_status_constraint
    FROM pg_constraint c
    WHERE c.conrelid = 'public.platform_fees'::regclass
      AND c.conname = 'platform_fees_status_check'
    LIMIT 1;

    IF position('warning' IN v_platform_fee_status_constraint) = 0 THEN
        IF position('due' IN v_platform_fee_status_constraint) > 0 THEN
            v_monthly_warning_status := 'due';
        ELSE
            v_monthly_warning_status := 'paid';
        END IF;
    END IF;

    IF position('overdue' IN v_platform_fee_status_constraint) = 0 THEN
        IF position('warning' IN v_platform_fee_status_constraint) > 0 THEN
            v_monthly_overdue_status := 'warning';
        ELSIF position('due' IN v_platform_fee_status_constraint) > 0 THEN
            v_monthly_overdue_status := 'due';
        ELSIF position('suspended' IN v_platform_fee_status_constraint) > 0 THEN
            v_monthly_overdue_status := 'suspended';
        ELSE
            v_monthly_overdue_status := 'paid';
        END IF;
    END IF;

    -- Clean old debug-only records.
    DELETE FROM public.audit_logs WHERE action LIKE 'DBG_%';
    DELETE FROM public.platform_ledger WHERE description LIKE 'DBG %';
    DELETE FROM public.groups WHERE name LIKE 'DBG-%';

    -- Four deterministic groups aligned to real debugging scenarios.
    FOR v_group_index IN 1..4 LOOP
        IF v_group_index = 1 THEN
            v_group_name := 'DBG-G01 Healthy Flow';
            v_group_type := 'stokvel';
            v_group_province := 'Gauteng';
            v_group_city := 'Johannesburg';
            v_group_township := 'Soweto';
            v_fee_status := 'paid';
        ELSIF v_group_index = 2 THEN
            v_group_name := 'DBG-G02 Loan Stress';
            v_group_type := 'investment_club';
            v_group_province := 'Western Cape';
            v_group_city := 'Cape Town';
            v_group_township := 'Khayelitsha';
            v_fee_status := 'paid';
        ELSIF v_group_index = 3 THEN
            v_group_name := 'DBG-G03 Burial Escalation';
            v_group_type := 'burial_society';
            v_group_province := 'KwaZulu-Natal';
            v_group_city := 'Durban';
            v_group_township := 'Umlazi';
            v_fee_status := 'warning';
        ELSE
            v_group_name := 'DBG-G04 Suspension Edge';
            v_group_type := 'community_savings';
            v_group_province := 'Limpopo';
            v_group_city := 'Polokwane';
            v_group_township := 'Seshego';
            v_fee_status := 'suspended';
        END IF;

        INSERT INTO public.groups (
            name,
            type,
            province,
            city,
            township,
            description,
            logo_emoji,
            joining_fee,
            monthly_contribution,
            late_fee,
            late_fee_grace_days,
            probation_months,
            payment_due_day,
            max_members,
            is_public,
            allow_partial_payment,
            auto_suspend_after,
            bank_name,
            account_number,
            branch_code,
            account_type,
            balance,
            admin_user_id,
            fee_status,
            registration_paid,
            is_platform_suspended,
            constitution_status,
            latitude,
            longitude,
            max_beneficiaries,
            beneficiary_increase_pct,
            goal_amount,
            period_months,
            loan_interest_rate,
            loan_max_amount,
            loan_max_months
        ) VALUES (
            v_group_name,
            v_group_type,
            v_group_province,
            v_group_city,
            v_group_township,
            'DBG scenario group for deterministic app debugging.',
            CASE WHEN v_group_type = 'burial_society' THEN '🕊️' ELSE '🧪' END,
            150.00,
            300.00,
            50.00,
            5,
            3,
            28,
            80,
            TRUE,
            TRUE,
            2,
            'FNB',
            format('6200999%03s', v_group_index),
            '250655',
            'Savings',
            CASE WHEN v_group_index = 4 THEN 1200.00 ELSE 8500.00 - (v_group_index * 500) END,
            v_admin_id,
            v_fee_status,
            TRUE,
            (v_group_index = 4),
            'verified',
            -29.0 - (v_group_index * 0.35),
            24.0 + (v_group_index * 0.45),
            CASE WHEN v_group_type = 'burial_society' THEN 8 ELSE 0 END,
            CASE WHEN v_group_type = 'burial_society' THEN 10 ELSE 0 END,
            80000.00,
            24,
            12.50,
            15000.00,
            12
        ) RETURNING id INTO v_group_id;

        INSERT INTO public.group_actuarial_metrics (
            group_id,
            pure_premium,
            gross_premium,
            reserve_adequacy_pct,
            solvency_margin_pct,
            loss_ratio_pct,
            contribution_sufficiency_pct,
            break_even_members,
            actuarial_present_value,
            funding_ratio_pct,
            payment_rate_pct,
            composite_risk_score,
            insolvency_months,
            expected_annual_claims
        ) VALUES (
            v_group_id,
            110.0 + (v_group_index * 5),
            140.0 + (v_group_index * 6),
            CASE WHEN v_group_index = 4 THEN 45 ELSE 82 END,
            CASE WHEN v_group_index = 4 THEN 8 ELSE 26 END,
            CASE WHEN v_group_index = 2 THEN 62 ELSE 35 END,
            CASE WHEN v_group_index = 4 THEN 58 ELSE 90 END,
            24,
            70000.0 + (v_group_index * 2500),
            CASE WHEN v_group_index = 4 THEN 68 ELSE 95 END,
            CASE WHEN v_group_index = 2 THEN 72 ELSE 88 END,
            CASE WHEN v_group_index = 4 THEN 78 WHEN v_group_index = 2 THEN 62 ELSE 34 END,
            CASE WHEN v_group_index = 4 THEN 4 ELSE 14 END,
            22000.0
        );

        INSERT INTO public.platform_fees (group_id, fee_type, amount, status, due_date, paid_at, transaction_id)
        VALUES
            (v_group_id, 'registration', 700.00, 'paid', to_char(current_date - 20, 'YYYY-MM-DD'), now() - interval '20 days', format('dbg_reg_%s', v_group_index)),
            (
                v_group_id,
                'monthly',
                160.00,
                CASE
                    WHEN v_group_index = 4 THEN v_monthly_overdue_status
                    WHEN v_group_index = 3 THEN v_monthly_warning_status
                    ELSE 'paid'
                END,
                to_char(current_date + 10, 'YYYY-MM-DD'),
                CASE WHEN v_group_index IN (1, 2) THEN now() - interval '2 days' ELSE NULL END,
                CASE WHEN v_group_index IN (1, 2) THEN format('dbg_monthly_%s', v_group_index) ELSE NULL END
            );

        -- Six members per group with deterministic statuses.
        FOR v_member_index IN 1..6 LOOP
            INSERT INTO public.members (
                group_id,
                user_id,
                full_name,
                id_number,
                phone,
                email,
                street,
                suburb,
                city,
                province,
                notification_pref,
                status,
                joined_at,
                probation_end_at,
                beneficiary_count,
                beneficiary_over_65_count,
                total_contributions,
                total_paid,
                member_key
            ) VALUES (
                v_group_id,
                NULL,
                format('Debug Member %s-%s', lpad(v_group_index::text, 2, '0'), lpad(v_member_index::text, 2, '0')),
                lpad((9100000000000 + (v_group_index * 100) + v_member_index)::text, 13, '0'),
                format('07%s', lpad((v_group_index * 100 + v_member_index)::text, 8, '0')),
                format('debug.member.%s.%s@example.com', lpad(v_group_index::text, 2, '0'), lpad(v_member_index::text, 2, '0')),
                format('%s Debug Street', v_member_index),
                v_group_township,
                v_group_city,
                v_group_province,
                CASE WHEN v_member_index % 2 = 0 THEN 'both' ELSE 'whatsapp' END,
                CASE
                    WHEN v_member_index <= 4 THEN 'active'
                    WHEN v_member_index = 5 THEN 'probation'
                    ELSE 'pending_payment'
                END,
                now() - ((v_member_index + 10) || ' days')::interval,
                now() + interval '45 days',
                CASE WHEN v_group_type = 'burial_society' THEN 2 ELSE 0 END,
                CASE WHEN v_group_type = 'burial_society' AND v_member_index = 4 THEN 1 ELSE 0 END,
                0,
                0,
                format('DBG_KEY_%s_%s', lpad(v_group_index::text, 2, '0'), lpad(v_member_index::text, 2, '0'))
            ) RETURNING id INTO v_member_id;

            -- Contribution states: paid, due, overdue, partial for logic testing.
            INSERT INTO public.contributions (
                member_id,
                group_id,
                amount,
                type,
                due_date,
                paid_at,
                payment_method,
                yoco_transaction_id,
                status,
                late_fees_applied
            ) VALUES
            (
                v_member_id,
                v_group_id,
                300.00,
                'contribution',
                current_date - 30,
                now() - interval '28 days',
                'yoco',
                format('dbg_tx_paid_%s_%s', v_group_index, v_member_index),
                'paid',
                FALSE
            ),
            (
                v_member_id,
                v_group_id,
                300.00,
                'contribution',
                current_date,
                NULL,
                'bank',
                NULL,
                CASE
                    WHEN v_member_index IN (2, 5) THEN 'due'
                    WHEN v_member_index IN (3, 6) THEN 'overdue'
                    ELSE 'partial'
                END,
                (v_member_index IN (3, 6))
            );

            INSERT INTO public.payments (
                member_id,
                group_id,
                amount,
                payment_type,
                payment_method,
                transaction_id,
                status,
                processed_at
            ) VALUES (
                v_member_id,
                v_group_id,
                CASE WHEN v_member_index IN (1, 4) THEN 150.00 ELSE 300.00 END,
                'contribution',
                'yoco',
                format('dbg_pay_%s_%s', v_group_index, v_member_index),
                'completed',
                now() - interval '5 days'
            );

            -- Loan scenarios concentrated in DBG-G02 and DBG-G04.
            IF v_group_index = 2 AND v_member_index <= 5 THEN
                INSERT INTO public.loans (
                    member_id,
                    group_id,
                    amount,
                    interest_rate,
                    total_to_repay,
                    total_repaid,
                    monthly_repayment,
                    start_date,
                    end_date,
                    next_payment_date,
                    status,
                    purpose,
                    reviewed_by,
                    reviewed_at,
                    admin_notes,
                    rejection_reason
                ) VALUES (
                    v_member_id,
                    v_group_id,
                    CASE v_member_index WHEN 1 THEN 4500 WHEN 2 THEN 5000 WHEN 3 THEN 3800 WHEN 4 THEN 6200 ELSE 2400 END,
                    12.0,
                    CASE v_member_index WHEN 4 THEN 7440 ELSE 5600 END,
                    CASE v_member_index WHEN 3 THEN 2100 WHEN 4 THEN 1200 ELSE 0 END,
                    620.0,
                    current_date - interval '60 days',
                    current_date + interval '240 days',
                    current_date + interval '25 days',
                    CASE
                        WHEN v_member_index = 1 THEN 'pending'
                        WHEN v_member_index = 2 THEN 'approved'
                        WHEN v_member_index = 3 THEN 'active'
                        WHEN v_member_index = 4 THEN 'overdue'
                        ELSE 'rejected'
                    END,
                    CASE
                        WHEN v_member_index = 4 THEN 'Medical emergency'
                        ELSE 'Working capital'
                    END,
                    v_admin_id,
                    now() - interval '3 days',
                    CASE WHEN v_member_index IN (1, 2) THEN 'Awaiting platform verification' ELSE 'DBG loan seeded' END,
                    CASE WHEN v_member_index = 5 THEN 'Debt-to-income too high' ELSE NULL END
                ) RETURNING id INTO v_loan_id;

                IF v_member_index IN (3, 4) THEN
                    INSERT INTO public.loan_repayments (loan_id, member_id, group_id, amount, payment_method, transaction_id)
                    VALUES
                        (v_loan_id, v_member_id, v_group_id, 600.00, 'yoco', format('dbg_lr_%s_%s_1', v_group_index, v_member_index)),
                        (v_loan_id, v_member_id, v_group_id, 600.00, 'bank', format('dbg_lr_%s_%s_2', v_group_index, v_member_index));
                END IF;
            ELSIF v_group_index = 4 AND v_member_index IN (1, 2) THEN
                INSERT INTO public.loans (
                    member_id,
                    group_id,
                    amount,
                    interest_rate,
                    total_to_repay,
                    total_repaid,
                    monthly_repayment,
                    start_date,
                    end_date,
                    next_payment_date,
                    status,
                    purpose,
                    reviewed_by,
                    reviewed_at,
                    admin_notes
                ) VALUES (
                    v_member_id,
                    v_group_id,
                    3000.00,
                    15.0,
                    3450.00,
                    CASE WHEN v_member_index = 1 THEN 0 ELSE 300 END,
                    575.00,
                    current_date - interval '20 days',
                    current_date + interval '160 days',
                    current_date + interval '10 days',
                    CASE WHEN v_member_index = 1 THEN 'pending' ELSE 'active' END,
                    'Household recovery',
                    v_admin_id,
                    now() - interval '1 day',
                    'Group fee risk scenario'
                );
            END IF;

            -- Burial claim scenarios for DBG-G03.
            IF v_group_index = 3 AND v_member_index <= 3 THEN
                INSERT INTO public.beneficiaries (
                    group_id,
                    member_id,
                    full_name,
                    id_number,
                    relationship,
                    date_of_birth,
                    is_over_65,
                    document_status
                ) VALUES (
                    v_group_id,
                    v_member_id,
                    format('Debug Beneficiary %s-%s', lpad(v_group_index::text, 2, '0'), lpad(v_member_index::text, 2, '0')),
                    lpad((7600000000000 + (v_group_index * 100) + v_member_index)::text, 13, '0'),
                    'Sibling',
                    current_date - interval '42 years',
                    FALSE,
                    'verified'
                ) RETURNING id INTO v_beneficiary_id;

                INSERT INTO public.beneficiary_payout_claims (
                    group_id,
                    member_id,
                    beneficiary_id,
                    beneficiary_name,
                    cause_of_death,
                    date_of_death,
                    claim_amount,
                    bank_name,
                    account_no,
                    branch_code,
                    account_holder,
                    notes,
                    status,
                    reviewed_by,
                    reviewed_at,
                    admin_notes,
                    rejection_reason
                ) VALUES (
                    v_group_id,
                    v_member_id,
                    v_beneficiary_id,
                    format('Debug Beneficiary %s-%s', lpad(v_group_index::text, 2, '0'), lpad(v_member_index::text, 2, '0')),
                    'Natural causes',
                    current_date - 4,
                    CASE WHEN v_member_index = 2 THEN 9500.00 ELSE 12000.00 END,
                    'FNB',
                    format('6200888%03s', v_member_index),
                    '250655',
                    format('Debug Member %s-%s', lpad(v_group_index::text, 2, '0'), lpad(v_member_index::text, 2, '0')),
                    'DBG burial claim scenario',
                    CASE
                        WHEN v_member_index = 1 THEN 'escalated'
                        WHEN v_member_index = 2 THEN 'approved'
                        ELSE 'rejected'
                    END,
                    CASE WHEN v_member_index IN (2, 3) THEN v_admin_id ELSE NULL END,
                    CASE WHEN v_member_index IN (2, 3) THEN now() - interval '1 day' ELSE NULL END,
                    CASE WHEN v_member_index = 2 THEN 'Approved during debug run' ELSE NULL END,
                    CASE WHEN v_member_index = 3 THEN 'Missing supporting documents' ELSE NULL END
                );
            END IF;
        END LOOP;

        -- Payout scenario coverage.
        INSERT INTO public.payouts (
            group_id,
            amount,
            bank_name,
            account_no,
            branch_code,
            status,
            processed_by,
            processed_at,
            yoco_payout_id
        ) VALUES (
            v_group_id,
            CASE WHEN v_group_index = 4 THEN 2200.00 ELSE 4500.00 END,
            'Standard Bank',
            format('12344456%02s', v_group_index),
            '051001',
            CASE
                WHEN v_group_index = 1 THEN v_status_group_approved
                WHEN v_group_index = 2 THEN v_status_processing
                WHEN v_group_index = 3 THEN v_status_failed
                ELSE v_status_group_approved
            END,
            v_admin_id,
            now() - interval '1 day',
            format('dbg_payout_%s', v_group_index)
        );

        INSERT INTO public.group_ledger (group_id, amount, balance_after, description, category)
        VALUES (
            v_group_id,
            0.00,
            CASE WHEN v_group_index = 4 THEN 1200.00 ELSE 8200.00 END,
            format('DBG opening balance for group scenario %s', v_group_index),
            'opening_balance'
        );

        INSERT INTO public.platform_ledger (amount, balance_after, description, category)
        VALUES (
            CASE WHEN v_group_index = 4 THEN -300.00 ELSE 700.00 END,
            12000.00 + (v_group_index * 500),
            format('DBG platform fee snapshot for %s', v_group_name),
            'debug_seed'
        );

        INSERT INTO public.audit_logs (actor_id, target_group_id, action, details)
        VALUES (
            v_admin_id,
            v_group_id,
            'DBG_GROUP_SCENARIO_CREATED',
            jsonb_build_object(
                'seed', true,
                'scenario_group', v_group_name,
                'group_index', v_group_index,
                'members', 6
            )
        );
    END LOOP;

    -- Explicit high-risk marker for admin workflows.
    INSERT INTO public.audit_logs (actor_id, action, details)
    VALUES (
        v_admin_id,
        'DBG_HIGH_RISK_PORTAL_NOTE',
        jsonb_build_object(
            'message', 'DBG-G02 and DBG-G04 contain elevated/high loan risk signals for platform triage.'
        )
    );

    RAISE NOTICE 'Debug seed complete: 4 groups, 24 members, deterministic payout/loan/claim edge scenarios.';
END $$;

