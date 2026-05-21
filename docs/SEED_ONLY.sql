-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — DATABASE SEED (DATA ONLY)
-- Version: 21.0
-- PREREQUISITE: SCHEMA_ONLY.sql must be run successfully first.
-- ─────────────────────────────────────────────────────────────────────────────

-- 1. ADMIN SETUP
DO $$
DECLARE v_uid UUID;
BEGIN
    SELECT id INTO v_uid FROM auth.users WHERE lower(email) = 'torrymsimango@gmail.com' LIMIT 1;
    IF v_uid IS NULL THEN
        v_uid := gen_random_uuid();
        INSERT INTO auth.users (id, aud, role, email, encrypted_password, email_confirmed_at, raw_app_meta_data, raw_user_meta_data)
        VALUES (v_uid, 'authenticated', 'authenticated', 'torrymsimango@gmail.com', crypt('torry123M', gen_salt('bf')), now(), '{"role":"platform_admin"}', '{"role":"platform_admin","full_name":"Torry Admin"}');
    END IF;
    INSERT INTO public.profiles (id, full_name, email, role) VALUES (v_uid, 'Torry Admin', 'torrymsimango@gmail.com', 'platform_admin') ON CONFLICT (id) DO UPDATE SET role = 'platform_admin';
END $$;

INSERT INTO public.platform_settings (key, value) VALUES ('monthly_per_member', 12.0), ('registration_fee', 700.0) ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value;

-- 2. COMPREHENSIVE SEEDING
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

    v_group_types TEXT[] := ARRAY['burial_society','stokvel','rosca','investment_club','emergency_fund','community_savings','tontine','other'];
    v_provinces TEXT[] := ARRAY['Gauteng','Western Cape','KwaZulu-Natal','Eastern Cape','Free State','Limpopo','Mpumalanga','North West'];
    v_cities TEXT[] := ARRAY['Johannesburg','Cape Town','Durban','Gqeberha','Bloemfontein','Polokwane','Mbombela','Mahikeng'];
    v_rotation_methods TEXT[] := ARRAY['fixed', 'random_draw', 'need_based', 'auction'];
BEGIN
    -- Get Admin ID
    SELECT id INTO v_admin_id FROM auth.users WHERE lower(email) = 'torrymsimango@gmail.com' LIMIT 1;
    IF v_admin_id IS NULL THEN RAISE EXCEPTION 'Admin user not found for seeding. Ensure Step 1 (Admin Setup) succeeded.'; END IF;

    RAISE NOTICE 'Seeding 8 groups...';

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
            format('A high-fidelity %s group for testing.', replace(v_group_type, '_', ' ')),
            150.00, 250.00, 0, v_admin_id, 'paid', true, true,
            12.00, 5000.00, 12,
            v_rotation_methods[1 + (v_group_index % 4)],
            1 + (v_group_index * 3 % 28)
        ) RETURNING id INTO v_group_id;

        PERFORM public.increment_group_balance(v_group_id, 5000.00, 'SEED: Initial Capital', 'system');

        v_member_ids := ARRAY[]::UUID[];

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
                CASE WHEN v_member_index = 8 THEN 'suspended' WHEN v_member_index = 7 THEN 'pending_payment' WHEN v_member_index >= 5 THEN 'probation' ELSE 'active' END,
                format('MK-SEED-%s-%s', v_group_index, v_member_index),
                NOW() - interval '6 months',
                0, 0
            ) RETURNING id INTO v_member_id;

            v_member_ids := array_append(v_member_ids, v_member_id);

            -- Historical contribution
            PERFORM public.record_contribution_v1(v_member_id, v_group_id, 250.00, (CURRENT_DATE - interval '30 days')::DATE, (NOW() - interval '29 days')::TIMESTAMPTZ, 'paid', format('tx-seed-%s-%s', v_group_index, v_member_index));
        END LOOP;

        -- Seed a poll
        INSERT INTO public.group_polls (group_id, created_by_member_id, title, description, status)
        VALUES (v_group_id, v_member_ids[1], 'Poll: ' || v_group_name, 'Testing voting', 'open') RETURNING id INTO v_poll_id;
        INSERT INTO public.group_poll_options (poll_id, label, position) VALUES (v_poll_id, 'Option A', 1), (v_poll_id, 'Option B', 2);

        -- Actuarial
        INSERT INTO public.group_actuarial_metrics (group_id, pure_premium, gross_premium, solvency_margin_pct, reserve_adequacy_pct)
        VALUES (v_group_id, 180.00, 250.00, 45.00, 110.00);
    END LOOP;

    RAISE NOTICE 'Seeding complete.';
END $$;
