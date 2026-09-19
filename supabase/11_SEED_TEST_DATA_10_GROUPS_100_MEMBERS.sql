-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — TEST DATA SEED (10 Groups / 100 Members)
-- Version: 3.0 (Business-Rule Compliant - August 2026)
-- ─────────────────────────────────────────────────────────────────────────────

DO $$
DECLARE
    -- Constants
    v_qa_password TEXT := 'Pass123!';
    v_now TIMESTAMPTZ := NOW();

    -- Arrays for randomization
    v_types TEXT ARRAY := ARRAY['burial_society', 'stokvel', 'rosca', 'investment_club', 'emergency_fund', 'community_savings', 'tontine', 'stokvel', 'burial_society', 'rosca'];
    v_group_names TEXT ARRAY := ARRAY['Eternal Peace Burial', 'Unity Savings Stokvel', 'Morning Star ROSCA', 'Blue Chip Investors', 'Crisis Response Fund', 'Neighborhood Savings', 'Legacy Tontine', 'Friends of Soweto', 'Zion Funeral Society', 'Quick Turn ROSCA'];
    v_provinces TEXT ARRAY := ARRAY['Gauteng', 'Western Cape', 'KwaZulu-Natal', 'Eastern Cape', 'Limpopo', 'Free State', 'Mpumalanga', 'North West', 'Gauteng', 'Western Cape'];
    v_cities TEXT ARRAY := ARRAY['Johannesburg', 'Cape Town', 'Durban', 'Gqeberha', 'Polokwane', 'Bloemfontein', 'Nelspruit', 'Rustenburg', 'Pretoria', 'Stellenbosch'];

    -- Loop Variables
    v_admin_id UUID;
    v_group_id UUID;
    v_member_id UUID;
    v_user_id UUID;
    v_type TEXT;
    v_group_name TEXT;
    v_monthly_contrib NUMERIC;
    v_joining_fee NUMERIC := 150.00;
    v_email TEXT;
    v_full_name TEXT;
    v_status TEXT;

BEGIN
    -- 1. IDENTIFY OR CREATE PLATFORM ADMIN
    SELECT id INTO v_admin_id FROM auth.users WHERE email = 'torrymsimango@gmail.com' LIMIT 1;
    IF v_admin_id IS NULL THEN
        v_admin_id := gen_random_uuid();
        INSERT INTO auth.users (id, instance_id, aud, role, email, encrypted_password, email_confirmed_at, raw_app_meta_data, raw_user_meta_data, created_at, updated_at)
        VALUES (v_admin_id, '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated', 'torrymsimango@gmail.com', extensions.crypt(v_qa_password, extensions.gen_salt('bf')), now(), '{"provider":"email","providers":["email"],"role":"platform_admin"}', '{"full_name":"Torry Msimango","role":"platform_admin"}', now(), now());

        INSERT INTO public.profiles (id, full_name, email, role)
        VALUES (v_admin_id, 'Torry Msimango', 'torrymsimango@gmail.com', 'platform_admin');
    END IF;

    -- 2. CLEANUP (Targeted)
    RAISE NOTICE '🚀 Seeding 10 Groups and 100 Members...';
    DELETE FROM public.groups WHERE name LIKE 'SEED-%';
    DELETE FROM auth.users WHERE email LIKE 'seed.member.%@example.com';

    -- 3. GENERATE 10 GROUPS
    FOR i IN 1..10 LOOP
        v_type := v_types[i];
        v_group_name := 'SEED-' || v_group_names[i];
        v_monthly_contrib := CASE WHEN v_type = 'investment_club' THEN 1000.00 ELSE 500.00 END;

        INSERT INTO public.groups (
            name, type, province, city, joining_fee, monthly_contribution,
            admin_user_id, fee_status, registration_paid, balance, created_at
        ) VALUES (
            v_group_name, v_type, v_provinces[i], v_cities[i], v_joining_fee, v_monthly_contrib,
            v_admin_id, 'paid', TRUE, 0.00, v_now - interval '8 months'
        ) RETURNING id INTO v_group_id;

        -- Admin is automatically added as member by trigger.
        -- Update Admin member details
        UPDATE public.members
        SET joined_at = v_now - interval '8 months',
            status = 'active'
        WHERE group_id = v_group_id AND user_id = v_admin_id;

        -- 4. GENERATE 9 MEMBERS PER GROUP (Total 100 with admin)
        FOR m IN 1..9 LOOP
            v_email := 'seed.member.' || i || '.' || m || '@example.com';
            v_full_name := 'Seed Member ' || i || '-' || m;
            v_user_id := gen_random_uuid();
            v_status := CASE WHEN m > 7 THEN 'probation' ELSE 'active' END;

            -- Auth & Profile
            INSERT INTO auth.users (id, instance_id, aud, role, email, encrypted_password, email_confirmed_at, raw_app_meta_data, raw_user_meta_data, created_at, updated_at)
            VALUES (v_user_id, '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated', v_email, extensions.crypt(v_qa_password, extensions.gen_salt('bf')), now(), '{"provider":"email","providers":["email"],"role":"member"}', jsonb_build_object('full_name', v_full_name, 'role', 'member'), now(), now());

            INSERT INTO public.profiles (id, full_name, email, role) VALUES (v_user_id, v_full_name, v_email, 'member');

            -- Member Record
            INSERT INTO public.members (
                group_id, user_id, full_name, email, status, joined_at,
                rosca_position, province, city
            ) VALUES (
                v_group_id, v_user_id, v_full_name, v_email, v_status, v_now - interval '8 months',
                CASE WHEN v_type = 'rosca' THEN m + 1 ELSE NULL END,
                v_provinces[i], v_cities[i]
            ) RETURNING id INTO v_member_id;

            -- 🛡️ Business Rule: Pay Joining Fee (Essential for all members)
            PERFORM public.record_contribution_v2(
                v_member_id, v_group_id, v_joining_fee,
                (v_now - interval '8 months')::date,
                v_now - interval '8 months',
                'paid', 'SEED-JOIN-' || v_member_id, 'registration_contribution'
            );

            -- 🛡️ Business Rule: 6 Months History (Satisfies Burial waiting period & gives good projection data)
            FOR h IN 1..6 LOOP
                -- 90% payment probability for realistic variability
                IF random() < 0.9 THEN
                    PERFORM public.record_contribution_v2(
                        v_member_id, v_group_id, v_monthly_contrib,
                        (v_now - (h * 30 || ' days')::interval)::date,
                        v_now - (h * 30 || ' days')::interval,
                        'paid', 'SEED-HIST-' || v_member_id || '-' || h, 'contribution'
                    );
                END IF;
            END LOOP;
        end loop;

        -- 🛡️ Emergency Fund Hardening: Ensure initial liquidity
        IF v_type = 'emergency_fund' THEN
            PERFORM public.increment_group_balance(v_group_id, 5000.00, 'Seed Liquidity - Reserve Fund', 'adjustment');
        END IF;

        -- 🛡️ Investment Club Hardening: Profit injection for healthy NAV
        IF v_type = 'investment_club' THEN
            PERFORM public.increment_group_balance(v_group_id, 10000.00, 'Seed Gains - Market Performance', 'investment_gain');
        END IF;

        PERFORM public.recalculate_all_group_financials_v3(v_group_id);
    END LOOP;

    RAISE NOTICE '🏁 Large-Scale Seeding Complete.';
END $$;
