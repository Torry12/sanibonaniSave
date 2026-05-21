-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — SMALL STRESS TEST SEED (150 Members)
-- ─────────────────────────────────────────────────────────────────────────────
DO $$
DECLARE
    v_admin_id UUID;
    v_group_id UUID;
    v_member_id UUID;
    v_group_types TEXT[] := ARRAY['stokvel', 'burial_society', 'rosca', 'investment_club'];
    v_member_statuses TEXT[] := ARRAY['active', 'probation', 'suspended', 'withdrawn'];
    v_g_type TEXT;
BEGIN
    SELECT id INTO v_admin_id FROM auth.users WHERE email = 'torrymsimango@gmail.com' LIMIT 1;
    IF v_admin_id IS NULL THEN RAISE EXCEPTION 'Admin not found. Run 03_PLATFORM_ADMIN_SETUP.sql first.'; END IF;

    -- Create 5 groups (30 members per group = 150 total; includes admin member)
    FOR i IN 1..5 LOOP
        v_g_type := v_group_types[1 + (i % 4)];
        INSERT INTO public.groups (id, name, type, province, city, admin_user_id, balance)
        VALUES (gen_random_uuid(), 'Stress Test Group ' || i, v_g_type, 'Gauteng', 'Pretoria', v_admin_id, (random()*50000)::numeric)
        RETURNING id INTO v_group_id;

        INSERT INTO public.members (
            id,
            group_id,
            user_id,
            full_name,
            email,
            phone,
            status,
            joined_at,
            member_key
        ) VALUES (
            gen_random_uuid(),
            v_group_id,
            v_admin_id,
            'Stress Admin ' || i,
            'stress.admin.' || i || '@example.com',
            '0766600' || lpad(i::text, 3, '0'),
            'active',
            now() - interval '180 days',
            'STRESS_ADMIN_KEY_' || i
        )
        ON CONFLICT (group_id, user_id) DO NOTHING;

        -- Create 29 additional members per group (admin + 29 = 30)
        FOR j IN 1..29 LOOP
            INSERT INTO public.members (id, group_id, full_name, email, status, joined_at)
            VALUES (gen_random_uuid(), v_group_id, 'Test Member ' || (i*30 + j), 'member' || (i*30 + j) || '@test.com', 'active', now() - (random() * interval '180 days'))
            RETURNING id INTO v_member_id;

            -- Create transactional history for each member (3-6 contributions each)
            FOR k IN 1..(3 + (random()*3)::int) LOOP
                INSERT INTO public.contributions (group_id, member_id, amount, created_at, due_date, status)
                VALUES (v_group_id, v_member_id, 500.0, now() - (k * interval '1 month'), now() - (k * interval '1 month'), 'paid');
            END LOOP;

            -- Create occasional loan
            IF random() > 0.8 THEN
                INSERT INTO public.loans (group_id, member_id, amount, total_to_repay, monthly_repayment, status)
                VALUES (v_group_id, v_member_id, 1000.0, 1000.0, 100.0, 'active');
            END IF;
        END LOOP;

        UPDATE public.groups
        SET current_members = 30
        WHERE id = v_group_id;
    END LOOP;
    RAISE NOTICE 'Seed generation complete: 150 members across 5 groups created.';
END $$;
