-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — COMPREHENSIVE DASHBOARD SEED (Robust Version)
-- Version: 2.3 (May 2026)
--
-- PREREQUISITE: Run 36_DASHBOARD_FULL_REBUILD.sql (v18.0) first.
-- ─────────────────────────────────────────────────────────────────────────────

DO $$
DECLARE
    v_admin_id UUID;
    v_group_id UUID;
    v_member_id UUID;
    v_poll_id UUID;
    v_group_index INT;
    v_member_index INT;
    v_group_type TEXT;
    v_group_name TEXT;
    v_group_city TEXT;
    v_group_province TEXT;
    v_member_ids UUID[];
    v_option_ids UUID[];

    v_group_types TEXT[] := ARRAY['burial_society','stokvel','rosca','investment_club','emergency_fund','community_savings','tontine','other'];
    v_provinces TEXT[] := ARRAY['Gauteng','Western Cape','KwaZulu-Natal','Eastern Cape','Free State','Limpopo','Mpumalanga','North West'];
    v_cities TEXT[] := ARRAY['Johannesburg','Cape Town','Durban','Gqeberha','Bloemfontein','Polokwane','Mbombela','Mahikeng'];
    v_rotation_methods TEXT[] := ARRAY['fixed', 'random_draw', 'need_based', 'auction'];
BEGIN
    -- 1. VERIFY CORE TABLES EXIST
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'members') THEN
        RAISE EXCEPTION 'Table public.members does not exist. Please run 36_DASHBOARD_FULL_REBUILD.sql first.';
    END IF;

    -- 2. GET ADMIN ID
    SELECT id INTO v_admin_id FROM auth.users WHERE lower(email) = 'torrymsimango@gmail.com' LIMIT 1;
    IF v_admin_id IS NULL THEN RAISE EXCEPTION 'Platform admin account (torrymsimango@gmail.com) not found in auth.users.'; END IF;

    -- 3. CLEANUP PREVIOUS SEED DATA
    DELETE FROM public.group_ledger WHERE description LIKE 'SEED%';
    DELETE FROM public.contributions WHERE transaction_id LIKE 'tx-seed%';
    DELETE FROM public.members WHERE member_key LIKE 'MK-SEED%';
    DELETE FROM public.groups WHERE name LIKE 'SEED-%';

    RAISE NOTICE 'Starting Dashboard Seed for 8 Groups...';

    -- 4. LOOP TO CREATE 8 GROUPS
    FOR v_group_index IN 1..8 LOOP
        v_group_type := v_group_types[v_group_index];
        v_group_province := v_provinces[v_group_index];
        v_group_city := v_cities[v_group_index];
        v_group_name := format('SEED-%s %s', v_group_index, replace(initcap(replace(v_group_type, '_', ' ')), '  ', ' '));

        INSERT INTO public.groups (
            name, type, province, city, township, description, joining_fee, monthly_contribution,
            balance, admin_user_id, fee_status, registration_paid, is_public,
            loan_interest_rate, loan_max_amount, period_months,
            rosca_rotation_method, payment_due_day
        ) VALUES (
            v_group_name, v_group_type, v_group_province, v_group_city, 'Seed Township',
            format('A high-fidelity %s group for E2E testing.', replace(v_group_type, '_', ' ')),
            150.00, 250.00, 0, v_admin_id, 'paid', true, true,
            12.00, 5000.00, 12,
            v_rotation_methods[1 + (v_group_index % 4)],
            1 + (v_group_index * 3 % 28)
        ) RETURNING id INTO v_group_id;

        -- Initial Capital Injection
        PERFORM public.increment_group_balance(
            v_group_id,
            5000.00,
            'SEED: Initial Group Capital',
            'system'
        );

        v_member_ids := ARRAY[]::UUID[];

        -- 5. LOOP TO CREATE 8 MEMBERS PER GROUP
        FOR v_member_index IN 1..8 LOOP
            INSERT INTO public.members (
                group_id, user_id, full_name, email, phone, status, member_key,
                joined_at, total_contributions, total_paid
            ) VALUES (
                v_group_id,
                CASE WHEN v_member_index = 1 THEN v_admin_id ELSE NULL END,
                format('Seed Member %s-%s', v_group_index, v_member_index),
                format('seed.m%s.g%s@example.com', v_member_index, v_group_index),
                '07' || lpad((v_group_index * 10 + v_member_index)::text, 8, '0'),
                CASE
                    WHEN v_member_index = 8 THEN 'suspended'
                    WHEN v_member_index = 7 THEN 'pending_payment'
                    WHEN v_member_index >= 5 THEN 'probation'
                    ELSE 'active'
                END,
                format('MK-SEED-%s-%s', v_group_index, v_member_index),
                NOW() - interval '6 months',
                0, 0
            ) RETURNING id INTO v_member_id;

            v_member_ids := array_append(v_member_ids, v_member_id);

            -- 6. SEED CONTRIBUTIONS (Historical)
            PERFORM public.record_contribution_v1(
                v_member_id, v_group_id, 250.00, (CURRENT_DATE - interval '60 days')::DATE,
                (NOW() - interval '58 days')::TIMESTAMPTZ, 'paid',
                format('tx-seed-%s-%s-1', v_group_index, v_member_index)
            );
            PERFORM public.record_contribution_v1(
                v_member_id, v_group_id, 250.00, (CURRENT_DATE - interval '30 days')::DATE,
                (NOW() - interval '29 days')::TIMESTAMPTZ, 'paid',
                format('tx-seed-%s-%s-2', v_group_index, v_member_index)
            );

            -- Current Month
            IF v_member_index <= 4 THEN
                PERFORM public.record_contribution_v1(
                    v_member_id, v_group_id, 250.00, (CURRENT_DATE - interval '1 day')::DATE,
                    NOW(), 'paid', format('tx-seed-%s-%s-3', v_group_index, v_member_index)
                );
            ELSIF v_member_index <= 6 THEN
                INSERT INTO public.contributions (member_id, group_id, amount, due_date, status, payment_method)
                VALUES (v_member_id, v_group_id, 250.00, CURRENT_DATE + interval '5 days', 'due', 'bank');
            ELSE
                INSERT INTO public.contributions (member_id, group_id, amount, due_date, status, payment_method)
                VALUES (v_member_id, v_group_id, 250.00, CURRENT_DATE - interval '5 days', 'overdue', 'bank');
            END IF;
        END LOOP;

        -- 7. SEED GROUP-SPECIFIC FINANCIAL ENTITIES
        INSERT INTO public.payouts (group_id, amount, bank_name, account_no, branch_code, status)
        VALUES (v_group_id, 1200.00, 'Standard Bank', '123456789', '000123', 'pending');

        -- 8. VOTING (Polls)
        INSERT INTO public.group_polls (group_id, created_by_member_id, title, description, status)
        VALUES (v_group_id, v_member_ids[1], 'December Festive Payout Date', 'When should we do the big payout?', 'open')
        RETURNING id INTO v_poll_id;

        INSERT INTO public.group_poll_options (poll_id, label, position) VALUES (v_poll_id, '10th December', 1);
        INSERT INTO public.group_poll_options (poll_id, label, position) VALUES (v_poll_id, '15th December', 2);
        INSERT INTO public.group_poll_options (poll_id, label, position) VALUES (v_poll_id, '20th December', 3);

        -- 9. SEED ACTUARIAL SNAPSHOT
        INSERT INTO public.group_actuarial_metrics (
            group_id, pure_premium, gross_premium, solvency_margin_pct, reserve_adequacy_pct,
            solvency_ratio, capital_adequacy_pct, expected_annual_claims, expected_annual_claims_count
        ) VALUES (
            v_group_id, 180.00, 250.00, 45.00, 110.00,
            1.45, 18.5, 2.4, 2.4
        );

        RAISE NOTICE '✓ Created group: %', v_group_name;
    END LOOP;

    -- 10. SYSTEM REVENUE SEED
    INSERT INTO public.platform_ledger (transaction_id, amount, balance_after, description, category)
    VALUES (gen_random_uuid(), 700.00, 700.00, 'SEED: Initial Registration Fee', 'registration');

    RAISE NOTICE 'Dashboard Seed Complete.';
END $$;
