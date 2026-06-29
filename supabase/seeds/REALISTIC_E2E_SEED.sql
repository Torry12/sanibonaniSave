-- REALISTIC_E2E_SEED.sql
-- Comprehensive seed data reflecting real-life scenarios for different group types.
-- Groups: Burial Society (Healthy), ROSCA (Mid-Rotation), Stokvel (Loan-heavy), Community Savings (New).

DO $$
DECLARE
    -- Admin IDs
    v_platform_admin_id UUID;
    v_admin_1_id UUID := '11111111-1111-1111-1111-111111111111';
    v_admin_2_id UUID := '22222222-2222-2222-2222-222222222222';
    v_admin_3_id UUID := '33333333-3333-3333-3333-333333333333';
    v_admin_4_id UUID := '44444444-4444-4444-4444-444444444444';

    -- Group IDs
    v_group_1_id UUID := '00000000-0000-0000-0000-000000000001'; -- Burial
    v_group_2_id UUID := '00000000-0000-0000-0000-000000000002'; -- ROSCA
    v_group_3_id UUID := '00000000-0000-0000-0000-000000000003'; -- Stokvel (Loans)
    v_group_4_id UUID := '00000000-0000-0000-0000-000000000004'; -- New Savings

    v_member_id UUID;
    v_loan_id UUID;
    v_claim_id UUID;
    v_payout_id UUID;
    v_idx INT;
    v_month INT;
    v_current_balance NUMERIC;
    v_member_name TEXT;
    v_id_number TEXT;
    v_password TEXT := crypt('SeedSafe@1234', gen_salt('bf'));
    v_email TEXT;
BEGIN
    -- 1. Ensure Platform Admin exists
    SELECT id INTO v_platform_admin_id FROM auth.users WHERE lower(email) = lower('torrymsimango@gmail.com') LIMIT 1;
    IF v_platform_admin_id IS NULL THEN
        RAISE EXCEPTION 'Platform admin user not found. Run platform admin setup first.';
    END IF;

    -- Ensure beneficiary_payout_claims has necessary columns (Self-healing migration)
    ALTER TABLE public.beneficiary_payout_claims ADD COLUMN IF NOT EXISTS cause_of_death TEXT;
    ALTER TABLE public.beneficiary_payout_claims ADD COLUMN IF NOT EXISTS date_of_death DATE;

    -- 2. Cleanup existing data (ordered for FK safety)
    DELETE FROM public.group_ledger WHERE description LIKE 'REALISTIC_SEED %';
    DELETE FROM public.platform_ledger WHERE description LIKE 'REALISTIC_SEED %';
    DELETE FROM public.audit_logs WHERE action LIKE 'REALISTIC_SEED_%';
    DELETE FROM public.group_health_scores WHERE group_id IN (v_group_1_id, v_group_2_id, v_group_3_id, v_group_4_id);
    DELETE FROM public.group_actuarial_metrics WHERE group_id IN (v_group_1_id, v_group_2_id, v_group_3_id, v_group_4_id);
    DELETE FROM public.member_behavior_track WHERE group_id IN (v_group_1_id, v_group_2_id, v_group_3_id, v_group_4_id);
    DELETE FROM public.beneficiary_payout_claims WHERE group_id IN (v_group_1_id, v_group_2_id, v_group_3_id, v_group_4_id);
    DELETE FROM public.payouts WHERE group_id IN (v_group_1_id, v_group_2_id, v_group_3_id, v_group_4_id);
    DELETE FROM public.loan_repayments WHERE group_id IN (v_group_1_id, v_group_2_id, v_group_3_id, v_group_4_id);
    DELETE FROM public.loans WHERE group_id IN (v_group_1_id, v_group_2_id, v_group_3_id, v_group_4_id);
    DELETE FROM public.contributions WHERE group_id IN (v_group_1_id, v_group_2_id, v_group_3_id, v_group_4_id);
    DELETE FROM public.beneficiaries WHERE group_id IN (v_group_1_id, v_group_2_id, v_group_3_id, v_group_4_id);
    DELETE FROM public.members WHERE group_id IN (v_group_1_id, v_group_2_id, v_group_3_id, v_group_4_id) OR user_id IN (v_admin_1_id, v_admin_2_id, v_admin_3_id, v_admin_4_id);
    DELETE FROM public.groups WHERE id IN (v_group_1_id, v_group_2_id, v_group_3_id, v_group_4_id);

    -- Cleanup auth users and identities for the admins (idempotent)
    DELETE FROM auth.identities WHERE user_id IN (v_admin_1_id, v_admin_2_id, v_admin_3_id, v_admin_4_id);
    DELETE FROM auth.users WHERE id IN (v_admin_1_id, v_admin_2_id, v_admin_3_id, v_admin_4_id);

    -- 3. Create Group Admins in auth.users and identities
    FOR v_idx IN 1..4 LOOP
        v_member_id := CASE v_idx
            WHEN 1 THEN v_admin_1_id WHEN 2 THEN v_admin_2_id
            WHEN 3 THEN v_admin_3_id WHEN 4 THEN v_admin_4_id
        END;
        v_email := format('group.admin.%s@example.com', v_idx);

        INSERT INTO auth.users (id, aud, role, email, encrypted_password, email_confirmed_at, raw_app_meta_data, raw_user_meta_data, created_at, updated_at, confirmation_token, recovery_token, email_change_token_new, is_super_admin)
        VALUES (
            v_member_id, 'authenticated', 'authenticated',
            v_email, v_password, now(),
            '{"provider":"email","providers":["email"],"role":"group_admin"}'::jsonb,
            jsonb_build_object('full_name', format('Admin Group %s', v_idx), 'role', 'group_admin'),
            now(), now(), '', '', '', FALSE
        );

        INSERT INTO auth.identities (id, user_id, identity_data, provider, provider_id, last_sign_in_at, created_at, updated_at)
        VALUES (
            gen_random_uuid(), v_member_id,
            jsonb_build_object('sub', v_member_id, 'email', v_email),
            'email', v_email, now(), now(), now()
        );

        INSERT INTO public.profiles (id, full_name, email, role)
        VALUES (v_member_id, format('Admin Group %s', v_idx), v_email, 'group_admin')
        ON CONFLICT (id) DO UPDATE SET role = 'group_admin';
    END LOOP;

    ---------------------------------------------------------------------------
    -- CASE 1: The Resilience Burial Society (Healthy, High Activity)
    ---------------------------------------------------------------------------
    INSERT INTO public.groups (
        id, name, type, province, city, township, description, logo_emoji,
        monthly_contribution, balance, admin_user_id, fee_status, registration_paid,
        max_beneficiaries, beneficiary_increase_pct, probation_months
    ) VALUES (
        v_group_1_id, 'The Resilience Burial Society', 'burial_society', 'Gauteng', 'Johannesburg', 'Soweto',
        'REALISTIC_SEED: A well-established burial society with excellent solvency.', '🕊️',
        250.00, 25000.00, v_admin_1_id, 'paid', TRUE, 5, 10.00, 3
    ) ON CONFLICT (id) DO UPDATE SET updated_at = now();

    INSERT INTO public.group_ledger (group_id, amount, balance_after, description, category, created_at)
    VALUES (v_group_1_id, 25000.00, 25000.00, 'REALISTIC_SEED initial balance', 'opening_balance', now() - interval '1 year');

    FOR v_idx IN 1..20 LOOP
        v_member_name := format('Resilience Member %s', v_idx);
        v_id_number := lpad((8001015000000 + v_idx)::text, 13, '0');

        INSERT INTO public.members (
            group_id, user_id, full_name, id_number, phone, email, status, joined_at,
            probation_end_at, total_contributions, total_paid
        ) VALUES (
            v_group_1_id,
            CASE WHEN v_idx = 1 THEN v_admin_1_id ELSE NULL END,
            v_member_name, v_id_number, format('071000%s', lpad(v_idx::text, 4, '0')),
            format('resilience.%s@example.com', v_idx), 'active', now() - interval '1 year',
            now() - interval '9 months', 12, 3000.00
        ) ON CONFLICT (group_id, user_id) WHERE user_id IS NOT NULL DO UPDATE SET
            full_name = EXCLUDED.full_name,
            id_number = EXCLUDED.id_number,
            phone = EXCLUDED.phone,
            email = EXCLUDED.email,
            status = EXCLUDED.status,
            joined_at = EXCLUDED.joined_at,
            probation_end_at = EXCLUDED.probation_end_at,
            total_contributions = EXCLUDED.total_contributions,
            total_paid = EXCLUDED.total_paid
        RETURNING id INTO v_member_id;

        FOR v_month IN 1..12 LOOP
            INSERT INTO public.contributions (
                member_id, group_id, amount, type, due_date, paid_at, status, payment_method, created_at
            ) VALUES (
                v_member_id, v_group_1_id, 250.00, 'contribution',
                (now() - (v_month || ' months')::interval)::date,
                now() - (v_month || ' months')::interval + interval '2 days',
                'paid', 'bank', now() - (v_month || ' months')::interval
            );

            INSERT INTO public.payments (id, member_id, group_id, amount, payment_type, payment_method, transaction_id, status, processed_at, created_at)
            VALUES (
                gen_random_uuid(), v_member_id, v_group_1_id, 250.00, 'contribution', 'bank',
                format('realistic_pay_burial_%s_%s', v_idx, v_month), 'completed',
                now() - (v_month || ' months')::interval + interval '2 days',
                now() - (v_month || ' months')::interval
            ) ON CONFLICT (id) DO NOTHING;
        END LOOP;

        INSERT INTO public.beneficiaries (group_id, member_id, full_name, relationship, is_over_65)
        VALUES
            (v_group_1_id, v_member_id, format('%s Spouse', v_member_name), 'Spouse', FALSE),
            (v_group_1_id, v_member_id, format('%s Parent', v_member_name), 'Parent', TRUE);

        -- Populate behavior track
        INSERT INTO public.member_behavior_track (
            id, member_id, member_id_number, group_id, total_contributions, on_time_contributions,
            late_contributions, behavior_score, member_status, fraud_risk_level, months_in_group
        ) VALUES (
            gen_random_uuid(), v_member_id, v_id_number, v_group_1_id, 12, 10, 2, 95.0, 'active', 'LOW', 12
        );
    END LOOP;

    -- PAID claim
    SELECT id INTO v_member_id FROM public.members WHERE group_id = v_group_1_id LIMIT 1;
    INSERT INTO public.beneficiary_payout_claims (
        group_id, member_id, beneficiary_id, beneficiary_name, cause_of_death,
        date_of_death, claim_amount, bank_name, account_no, branch_code, account_holder,
        status, reviewed_by, reviewed_at, admin_notes, created_at
    ) VALUES (
        v_group_1_id, v_member_id, gen_random_uuid(), 'Late Parent Name', 'Natural Causes',
        now()::date - 100, 15000.00, 'FNB', '1234567890', '250655', 'Survivor Name',
        'paid', v_admin_1_id, now() - interval '90 days', 'Document verified. Payment processed via EFT.', now() - interval '95 days'
    );
    UPDATE public.groups SET balance = balance - 15000.00 WHERE id = v_group_1_id RETURNING balance INTO v_current_balance;
    INSERT INTO public.group_ledger (group_id, amount, balance_after, description, category, created_at)
    VALUES (v_group_1_id, -15000.00, v_current_balance, 'REALISTIC_SEED Historical Claim Payout', 'claim', now() - interval '90 days');

    -- ESCALATED claim
    SELECT id INTO v_member_id FROM public.members WHERE group_id = v_group_1_id OFFSET 5 LIMIT 1;
    INSERT INTO public.beneficiary_payout_claims (
        group_id, member_id, beneficiary_id, beneficiary_name, cause_of_death,
        date_of_death, claim_amount, bank_name, account_no, branch_code, account_holder,
        status, created_at
    ) VALUES (
        v_group_1_id, v_member_id, gen_random_uuid(), 'Escalated Relative', 'Accident',
        now()::date - 5, 10000.00, 'Standard Bank', '9876543210', '051001', 'Survivor B',
        'escalated', now() - interval '3 days'
    );

    ---------------------------------------------------------------------------
    -- CASE 2: Sunrise ROSCA (Mid-Rotation)
    ---------------------------------------------------------------------------
    INSERT INTO public.groups (
        id, name, type, balance, admin_user_id, rosca_rotation_method, monthly_contribution,
        max_members, registration_paid
    ) VALUES (
        v_group_2_id, 'Sunrise ROSCA', 'rosca', 5000.00, v_admin_2_id, 'fixed', 1000.00, 12, TRUE
    ) ON CONFLICT (id) DO UPDATE SET updated_at = now();

    INSERT INTO public.group_ledger (group_id, amount, balance_after, description, category, created_at)
    VALUES (v_group_2_id, 5000.00, 5000.00, 'REALISTIC_SEED opening balance', 'opening_balance', now() - interval '6 months');

    FOR v_idx IN 1..12 LOOP
        INSERT INTO public.members (
            group_id, user_id, full_name, phone, status, joined_at, total_contributions
        ) VALUES (
            v_group_2_id,
            CASE WHEN v_idx = 1 THEN v_admin_2_id ELSE NULL END,
            format('ROSCA Member %s', v_idx), format('072000%s', lpad(v_idx::text, 4, '0')),
            'active', now() - interval '6 months', 6
        ) ON CONFLICT (group_id, user_id) WHERE user_id IS NOT NULL DO UPDATE SET
            full_name = EXCLUDED.full_name,
            phone = EXCLUDED.phone,
            status = EXCLUDED.status,
            joined_at = EXCLUDED.joined_at,
            total_contributions = EXCLUDED.total_contributions
        RETURNING id INTO v_member_id;

        FOR v_month IN 1..6 LOOP
            INSERT INTO public.contributions (
                member_id, group_id, amount, type, due_date, paid_at, status, created_at
            ) VALUES (
                v_member_id, v_group_2_id, 1000.00, 'contribution',
                (now() - (v_month || ' months')::interval)::date,
                now() - (v_month || ' months')::interval + interval '1 day',
                'paid', now() - (v_month || ' months')::interval
            );

            INSERT INTO public.payments (id, member_id, group_id, amount, payment_type, payment_method, transaction_id, status, processed_at, created_at)
            VALUES (
                gen_random_uuid(), v_member_id, v_group_2_id, 1000.00, 'contribution', 'bank',
                format('realistic_pay_rosca_%s_%s', v_idx, v_month), 'completed',
                now() - (v_month || ' months')::interval + interval '1 day',
                now() - (v_month || ' months')::interval
            ) ON CONFLICT (id) DO NOTHING;
        END LOOP;

        IF v_idx <= 6 THEN
            INSERT INTO public.payouts (
                group_id, amount, bank_name, account_no, branch_code, status,
                processed_by, processed_at, payout_reference, created_at
            ) VALUES (
                v_group_2_id, 12000.00, 'Capitec', format('1234567%s', lpad(v_idx::text, 3, '0')),
                '470010', 'completed', v_admin_2_id, now() - (v_idx || ' months')::interval + interval '5 days',
                format('ROSCA_P_%s', v_idx), now() - (v_idx || ' months')::interval
            );
        END IF;
    END LOOP;

    ---------------------------------------------------------------------------
    -- CASE 3: Enterprise Loan Club (Active Lending, Some Overdue)
    ---------------------------------------------------------------------------
    INSERT INTO public.groups (
        id, name, type, balance, admin_user_id, monthly_contribution,
        loan_interest_rate, loan_max_amount, loan_max_months, registration_paid
    ) VALUES (
        v_group_3_id, 'Enterprise Loan Club', 'stokvel', 45000.00, v_admin_3_id, 500.00,
        10.00, 20000.00, 12, TRUE
    ) ON CONFLICT (id) DO UPDATE SET updated_at = now();

    INSERT INTO public.group_ledger (group_id, amount, balance_after, description, category, created_at)
    VALUES (v_group_3_id, 45000.00, 45000.00, 'REALISTIC_SEED opening balance', 'opening_balance', now() - interval '1 year');

    FOR v_idx IN 1..15 LOOP
        v_id_number := lpad((7001015000000 + v_idx)::text, 13, '0');
        INSERT INTO public.members (
            group_id, user_id, full_name, id_number, phone, status, joined_at, total_contributions
        ) VALUES (
            v_group_3_id,
            CASE WHEN v_idx = 1 THEN v_admin_3_id ELSE NULL END,
            format('Enterprise Member %s', v_idx), v_id_number, format('073000%s', lpad(v_idx::text, 4, '0')),
            'active', now() - interval '1 year', 12
        ) ON CONFLICT (group_id, user_id) WHERE user_id IS NOT NULL DO UPDATE SET
            full_name = EXCLUDED.full_name,
            id_number = EXCLUDED.id_number,
            phone = EXCLUDED.phone,
            status = EXCLUDED.status,
            joined_at = EXCLUDED.joined_at,
            total_contributions = EXCLUDED.total_contributions
        RETURNING id INTO v_member_id;

        FOR v_month IN 1..12 LOOP
            INSERT INTO public.contributions (
                member_id, group_id, amount, type, due_date, paid_at, status, created_at
            ) VALUES (
                v_member_id, v_group_3_id, 500.00, 'contribution',
                (now() - (v_month || ' months')::interval)::date,
                now() - (v_month || ' months')::interval + interval '1 day',
                'paid', now() - (v_month || ' months')::interval
            );

            INSERT INTO public.payments (id, member_id, group_id, amount, payment_type, payment_method, transaction_id, status, processed_at, created_at)
            VALUES (
                gen_random_uuid(), v_member_id, v_group_3_id, 500.00, 'contribution', 'bank',
                format('realistic_pay_loanclub_%s_%s', v_idx, v_month), 'completed',
                now() - (v_month || ' months')::interval + interval '1 day',
                now() - (v_month || ' months')::interval
            ) ON CONFLICT (id) DO NOTHING;
        END LOOP;

        IF v_idx <= 5 THEN
            INSERT INTO public.loans (
                id, member_id, group_id, amount, interest_rate, total_to_repay,
                total_repaid, monthly_repayment, start_date, status, purpose, created_at
            ) VALUES (
                gen_random_uuid(), v_member_id, v_group_3_id, 5000.00, 10.00, 5500.00,
                CASE WHEN v_idx = 1 THEN 5500.00 WHEN v_idx = 2 THEN 5500.00 ELSE 2000.00 END,
                550.00, (now() - interval '6 months')::date,
                CASE WHEN v_idx <= 2 THEN 'completed' WHEN v_idx = 5 THEN 'overdue' ELSE 'active' END,
                'Business Expansion', now() - interval '6 months'
            ) RETURNING id INTO v_loan_id;

            IF v_idx <= 2 THEN
                INSERT INTO public.loan_repayments (loan_id, member_id, group_id, amount, paid_at)
                VALUES (v_loan_id, v_member_id, v_group_3_id, 5500.00, now() - interval '1 month');
            ELSE
                INSERT INTO public.loan_repayments (loan_id, member_id, group_id, amount, paid_at)
                VALUES (v_loan_id, v_member_id, v_group_3_id, 2000.00, now() - interval '1 month');
            END IF;
        END IF;

        -- Behavior track for Group 3 (Riskier)
        INSERT INTO public.member_behavior_track (
            id, member_id, member_id_number, group_id, total_contributions, on_time_contributions,
            late_contributions, overdue_loans, behavior_score, member_status, fraud_risk_level, months_in_group
        ) VALUES (
            gen_random_uuid(), v_member_id, v_id_number, v_group_3_id, 12, 8, 4,
            CASE WHEN v_idx = 5 THEN 1 ELSE 0 END,
            CASE WHEN v_idx = 5 THEN 45.0 ELSE 75.0 END,
            'active', CASE WHEN v_idx = 5 THEN 'HIGH' ELSE 'LOW' END, 12
        );
    END LOOP;

    ---------------------------------------------------------------------------
    -- CASE 4: New Beginnings Savings (Recently Registered)
    ---------------------------------------------------------------------------
    INSERT INTO public.groups (
        id, name, type, balance, admin_user_id, monthly_contribution, registration_paid,
        fee_status, created_at
    ) VALUES (
        v_group_4_id, 'New Beginnings Savings', 'community_savings', 1500.00, v_admin_4_id, 150.00,
        TRUE, 'paid', now() - interval '1 month'
    ) ON CONFLICT (id) DO UPDATE SET updated_at = now();

    INSERT INTO public.group_ledger (group_id, amount, balance_after, description, category, created_at)
    VALUES (v_group_4_id, 1500.00, 1500.00, 'REALISTIC_SEED opening balance', 'opening_balance', now() - interval '1 month');

    FOR v_idx IN 1..10 LOOP
        INSERT INTO public.members (
            group_id, user_id, full_name, phone, status, joined_at, probation_end_at
        ) VALUES (
            v_group_4_id,
            CASE WHEN v_idx = 1 THEN v_admin_4_id ELSE NULL END,
            format('New Member %s', v_idx), format('074000%s', lpad(v_idx::text, 4, '0')),
            'probation', now() - interval '20 days', now() + interval '70 days'
        ) ON CONFLICT (group_id, user_id) WHERE user_id IS NOT NULL DO UPDATE SET
            full_name = EXCLUDED.full_name,
            phone = EXCLUDED.phone,
            status = EXCLUDED.status,
            joined_at = EXCLUDED.joined_at,
            probation_end_at = EXCLUDED.probation_end_at
        RETURNING id INTO v_member_id;

        INSERT INTO public.contributions (
            member_id, group_id, amount, type, due_date, paid_at, status, created_at
        ) VALUES (
            v_member_id, v_group_4_id, 150.00, 'joining_fee',
            (now() - interval '20 days')::date, now() - interval '19 days',
            'paid', now() - interval '20 days'
        );

        INSERT INTO public.payments (id, member_id, group_id, amount, payment_type, payment_method, transaction_id, status, processed_at, created_at)
        VALUES (
            gen_random_uuid(), v_member_id, v_group_4_id, 150.00, 'joining_fee', 'bank',
            format('realistic_pay_new_%s', v_idx), 'completed',
            now() - interval '19 days',
            now() - interval '20 days'
        ) ON CONFLICT (id) DO NOTHING;
    END LOOP;

    ---------------------------------------------------------------------------
    -- Platform Wide Data
    ---------------------------------------------------------------------------
    INSERT INTO public.platform_fees (group_id, fee_type, amount, status, due_date, paid_at, created_at)
    VALUES
        (v_group_1_id, 'registration', 750.00, 'paid', '2025-01-01', now() - interval '1 year', now() - interval '1 year'),
        (v_group_1_id, 'monthly', 200.00, 'paid', to_char(now() - interval '1 month', 'YYYY-MM-DD'), now() - interval '25 days', now() - interval '1 month'),
        (v_group_2_id, 'registration', 750.00, 'paid', '2025-06-01', now() - interval '6 months', now() - interval '6 months'),
        (v_group_3_id, 'registration', 750.00, 'paid', '2025-01-01', now() - interval '1 year', now() - interval '1 year'),
        (v_group_4_id, 'registration', 750.00, 'paid', to_char(now() - interval '1 month', 'YYYY-MM-DD'), now() - interval '29 days', now() - interval '1 month'),
        (v_group_4_id, 'monthly', 100.00, 'due', to_char(now() + interval '5 days', 'YYYY-MM-DD'), NULL, now());

    INSERT INTO public.platform_ledger (amount, balance_after, description, category, created_at)
    VALUES
        (750.00, 750.00, 'REALISTIC_SEED Registration fee: Group 1', 'registration', now() - interval '1 year'),
        (200.00, 950.00, 'REALISTIC_SEED Monthly fee: Group 1', 'monthly', now() - interval '25 days'),
        (750.00, 1700.00, 'REALISTIC_SEED Registration fee: Group 2', 'registration', now() - interval '6 months'),
        (750.00, 2450.00, 'REALISTIC_SEED Registration fee: Group 3', 'registration', now() - interval '1 year'),
        (750.00, 3200.00, 'REALISTIC_SEED Registration fee: Group 4', 'registration', now() - interval '29 days');

    INSERT INTO public.group_actuarial_metrics (
        group_id, pure_premium, gross_premium, reserve_adequacy_pct, solvency_margin_pct, loss_ratio_pct, composite_risk_score
    ) VALUES
        (v_group_1_id, 180.00, 250.00, 120.50, 45.00, 35.00, 15),
        (v_group_3_id, 400.00, 500.00, 85.00, 15.00, 20.00, 45);

    INSERT INTO public.group_health_scores (
        id, group_id, overall_score, zone, components_json, recommendations_json, generated_at, expires_at, updated_at
    ) VALUES
        (gen_random_uuid(), v_group_1_id, 92, 'GREEN',
         '{"Solvency": 95, "Loss Ratio": 90, "Reserve": 92, "Funding": 94, "Retention": 90}'::jsonb,
         '["Maintain high reserves", "Excellent standing"]'::jsonb, now(), now() + interval '30 days', 0),
        (gen_random_uuid(), v_group_3_id, 65, 'YELLOW',
         '{"Solvency": 60, "Loss Ratio": 70, "Reserve": 65, "Funding": 62, "Retention": 68}'::jsonb,
         '["Review overdue loans", "Increase reserve buffer"]'::jsonb, now(), now() + interval '30 days', 0);

    IF (SELECT count(*) FROM public.payments WHERE group_id IN (v_group_1_id, v_group_2_id, v_group_3_id, v_group_4_id)) <
       (SELECT count(*) FROM public.contributions WHERE group_id IN (v_group_1_id, v_group_2_id, v_group_3_id, v_group_4_id) AND status = 'paid')
    THEN
        RAISE WARNING 'Seed validation: payments count mismatch — some paid contributions lack payment records';
    END IF;

    RAISE NOTICE 'Realistic E2E Seed completed successfully.';
END$$;
