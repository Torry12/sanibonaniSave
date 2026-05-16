-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — TEST SEED (10 Groups / 100 Members)
-- Purpose: realistic business-logic test dataset for app flows.
-- Idempotent: re-running replaces only prior SEED-G* test groups.
-- ─────────────────────────────────────────────────────────────────────────────

DO $$
DECLARE
    v_admin_id UUID;
    v_group_id UUID;
    v_member_id UUID;
    v_loan_id UUID;
    v_beneficiary_id UUID;

    v_group_type TEXT;
    v_province TEXT;
    v_city TEXT;
    v_township TEXT;
    v_group_name TEXT;

    v_status TEXT;
    v_amount NUMERIC(10,2);

    v_types TEXT[] := ARRAY[
        'stokvel',
        'burial_society',
        'investment_club',
        'rosca',
        'community_savings',
        'emergency_fund',
        'tontine',
        'stokvel',
        'burial_society',
        'other'
    ];
    v_provinces TEXT[] := ARRAY[
        'Gauteng',
        'KwaZulu-Natal',
        'Western Cape',
        'Eastern Cape',
        'Limpopo',
        'Mpumalanga',
        'North West',
        'Free State',
        'Northern Cape',
        'Gauteng'
    ];
    v_cities TEXT[] := ARRAY[
        'Johannesburg',
        'Durban',
        'Cape Town',
        'Gqeberha',
        'Polokwane',
        'Mbombela',
        'Mahikeng',
        'Bloemfontein',
        'Kimberley',
        'Pretoria'
    ];
    v_townships TEXT[] := ARRAY[
        'Soweto',
        'Umlazi',
        'Khayelitsha',
        'Motherwell',
        'Seshego',
        'Kanyamazane',
        'Mmabatho',
        'Mangaung',
        'Galeshewe',
        'Mamelodi'
    ];
BEGIN
    -- Use an existing admin user for group ownership and admin workflows.
    SELECT id
    INTO v_admin_id
    FROM auth.users
    WHERE email = 'torrymsimango@gmail.com'
    LIMIT 1;

    IF v_admin_id IS NULL THEN
        RAISE EXCEPTION 'Platform admin user not found. Run 03_PLATFORM_ADMIN_SETUP.sql first.';
    END IF;

    -- Cleanup old generated seed dataset only (safe for non-seed records).
    DELETE FROM public.audit_logs WHERE action LIKE 'SEED_%';
    DELETE FROM public.platform_ledger WHERE description LIKE 'SEED %';
    DELETE FROM public.groups WHERE name LIKE 'SEED-G%';

    -- Generate 10 groups, each with 10 members (total 100 members).
    FOR g IN 1..10 LOOP
        v_group_type := v_types[g];
        v_province := v_provinces[g];
        v_city := v_cities[g];
        v_township := v_townships[g];
        v_group_name := format('SEED-G%02s %s', g, initcap(replace(v_group_type, '_', ' ')));

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
            v_province,
            v_city,
            v_township,
            'Seed data group used for QA and business-logic testing.',
            CASE WHEN v_group_type = 'burial_society' THEN '🕊️' ELSE '🤝' END,
            150.00,
            250.00 + (g * 20),
            50.00,
            5,
            3,
            28,
            120,
            TRUE,
            (g % 2 = 0),
            2,
            'FNB',
            '6200000' || lpad(g::text, 3, '0'),
            '250655',
            'Savings',
            5000.00 + (g * 1500),
            v_admin_id,
            CASE WHEN g IN (3, 7) THEN 'warning' ELSE 'paid' END,
            TRUE,
            FALSE,
            'verified',
            -34.0 + (g * 0.8),
            18.0 + (g * 1.2),
            CASE WHEN v_group_type = 'burial_society' THEN 8 ELSE 0 END,
            CASE WHEN v_group_type = 'burial_society' THEN 10 ELSE 0 END,
            100000.00,
            24,
            12.50,
            12000.00,
            12
        )
        RETURNING id INTO v_group_id;

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
            120.00 + g,
            155.00 + g,
            78.00 + (g * 1.5),
            20.00 + g,
            35.00 + (g * 1.1),
            85.00 + (g * 0.8),
            22 + g,
            85000.00 + (g * 3000),
            92.00 + (g * 0.5),
            87.00 + (g * 0.6),
            30 + (g * 3),
            GREATEST(0, 18 - g),
            18000.00 + (g * 1400)
        );

        INSERT INTO public.platform_fees (group_id, fee_type, amount, status, due_date, paid_at, transaction_id)
        VALUES (v_group_id, 'registration', 700.00, 'paid', to_char(current_date - 20, 'YYYY-MM-DD'), now() - interval '20 days', format('seed_reg_%s', g));

        INSERT INTO public.platform_fees (group_id, fee_type, amount, status, due_date)
        VALUES (
            v_group_id,
            'monthly',
            10.00 * 10,
            CASE WHEN g IN (3, 7) THEN 'warning' ELSE 'paid' END,
            to_char(current_date + 10, 'YYYY-MM-DD')
        );

        IF g % 3 = 0 THEN
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
                3200.00,
                'Standard Bank',
                format('12345678%02s', g),
                '051001',
                'processing',
                v_admin_id,
                now() - interval '1 day',
                format('seed_payout_%s', g)
            );
        END IF;

        -- Group admin is always a member by default.
        INSERT INTO public.members (
            group_id,
            user_id,
            full_name,
            phone,
            email,
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
            v_admin_id,
            format('Seed Admin %s', lpad(g::text, 2, '0')),
            format('07888%s', lpad(g::text, 5, '0')),
            format('seed.admin.%s@example.com', lpad(g::text, 2, '0')),
            'both',
            'active',
            now() - interval '120 days',
            now() + interval '60 days',
            CASE WHEN v_group_type = 'burial_society' THEN 2 ELSE 0 END,
            0,
            0,
            0,
            format('SEED_ADMIN_KEY_%s', lpad(g::text, 2, '0'))
        )
        ON CONFLICT (group_id, user_id) DO NOTHING;

        -- Keep deterministic total at 10 members per group (1 admin + 9 generated members).
        FOR m IN 1..9 LOOP
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
                format('Seed Member %s-%s', lpad(g::text, 2, '0'), lpad(m::text, 2, '0')),
                lpad((9000000000000 + (g * 100) + m)::text, 13, '0'),
                format('07%s', lpad((g * 100 + m)::text, 8, '0')),
                format('seed.member.%s.%s@example.com', lpad(g::text, 2, '0'), lpad(m::text, 2, '0')),
                format('%s Test Street', m),
                v_township,
                v_city,
                v_province,
                CASE WHEN m % 3 = 0 THEN 'email' WHEN m % 3 = 1 THEN 'whatsapp' ELSE 'both' END,
                CASE WHEN m <= 7 THEN 'active' WHEN m = 8 THEN 'probation' ELSE 'pending_payment' END,
                now() - ((m + g) || ' days')::interval,
                now() + interval '60 days',
                CASE WHEN v_group_type = 'burial_society' THEN 2 ELSE 0 END,
                CASE WHEN v_group_type = 'burial_society' AND m % 4 = 0 THEN 1 ELSE 0 END,
                0,
                0,
                format('SEED_KEY_%s_%s', lpad(g::text, 2, '0'), lpad(m::text, 2, '0'))
            )
            RETURNING id INTO v_member_id;

            -- Three contribution cycles per member for payment logic coverage.
            FOR c IN 0..2 LOOP
                IF c = 0 THEN
                    v_status := 'paid';
                ELSIF c = 1 THEN
                    v_status := CASE WHEN m % 2 = 0 THEN 'due' ELSE 'overdue' END;
                ELSE
                    v_status := CASE WHEN m % 5 = 0 THEN 'partial' ELSE 'paid' END;
                END IF;

                v_amount := (250.00 + (g * 20));

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
                ) VALUES (
                    v_member_id,
                    v_group_id,
                    v_amount,
                    'contribution',
                    current_date - (c * 30),
                    CASE WHEN v_status = 'paid' THEN now() - (c || ' days')::interval ELSE NULL END,
                    'yoco',
                    CASE WHEN v_status = 'paid' THEN format('seed_tx_%s_%s_%s', g, m, c) ELSE NULL END,
                    v_status,
                    (v_status = 'overdue')
                );

                IF v_status IN ('paid', 'partial') THEN
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
                        CASE WHEN v_status = 'partial' THEN (v_amount / 2) ELSE v_amount END,
                        'contribution',
                        'yoco',
                        format('seed_pay_%s_%s_%s', g, m, c),
                        'completed',
                        now() - (c || ' days')::interval
                    );
                END IF;
            END LOOP;

            IF m % 2 = 0 THEN
                INSERT INTO public.notifications (
                    group_id,
                    member_id,
                    message,
                    channel,
                    trigger_event
                ) VALUES (
                    v_group_id,
                    v_member_id,
                    format('SEED reminder: Contribution due for %s.', to_char(current_date, 'Mon YYYY')),
                    'both',
                    'payment_due'
                );
            END IF;

            IF v_group_type = 'burial_society' AND m <= 4 THEN
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
                    format('Seed Beneficiary %s-%s', lpad(g::text, 2, '0'), lpad(m::text, 2, '0')),
                    lpad((7500000000000 + (g * 100) + m)::text, 13, '0'),
                    'Sibling',
                    current_date - interval '40 years',
                    (m = 4),
                    'verified'
                ) RETURNING id INTO v_beneficiary_id;

                IF m = 1 THEN
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
                        status
                    ) VALUES (
                        v_group_id,
                        v_member_id,
                        v_beneficiary_id,
                        format('Seed Beneficiary %s-%s', lpad(g::text, 2, '0'), lpad(m::text, 2, '0')),
                        'Natural causes',
                        current_date - 7,
                        10000.00,
                        'FNB',
                        '62000099999',
                        '250655',
                        format('Seed Member %s-%s', lpad(g::text, 2, '0'), lpad(m::text, 2, '0')),
                        'SEED escalated test claim',
                        'escalated'
                    );
                END IF;
            END IF;

            IF m % 4 = 0 THEN
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
                    10.0,
                    3300.00,
                    600.00,
                    550.00,
                    current_date - 60,
                    current_date + 180,
                    current_date + 20,
                    'active',
                    'Business support',
                    v_admin_id,
                    now() - interval '2 days',
                    'SEED approved loan'
                ) RETURNING id INTO v_loan_id;

                INSERT INTO public.loan_repayments (loan_id, member_id, group_id, amount, payment_method, transaction_id)
                VALUES
                    (v_loan_id, v_member_id, v_group_id, 300.00, 'yoco', format('seed_lr_%s_%s_1', g, m)),
                    (v_loan_id, v_member_id, v_group_id, 300.00, 'bank', format('seed_lr_%s_%s_2', g, m));
            END IF;
        END LOOP;

        INSERT INTO public.group_ledger (group_id, amount, balance_after, description, category)
        VALUES (
            v_group_id,
            0.00,
            5000.00 + (g * 1500),
            format('SEED opening balance for group %s', g),
            'opening_balance'
        );

        UPDATE public.groups
        SET current_members = 10
        WHERE id = v_group_id;

        INSERT INTO public.audit_logs (actor_id, target_group_id, action, details)
        VALUES (
            v_admin_id,
            v_group_id,
            'SEED_GROUP_CREATED',
            jsonb_build_object('seed', true, 'group_number', g, 'members', 10)
        );
    END LOOP;

    RAISE NOTICE 'Seed complete: 10 groups + 100 members + linked contributions/payments/loans/claims.';
END $$;

