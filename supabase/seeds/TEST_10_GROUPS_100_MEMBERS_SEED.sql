-- DEPRECATED: Use supabase/seeds/SAFE_SEED.sql or supabase/seeds/E2E_SEED.sql instead.
-- This script is retained for reference only. All new tests and data loads should use the canonical seeds.
-- TEST_10_GROUPS_100_MEMBERS_SEED.sql
-- Purpose: Create 10 groups each with 100 members (varied scenarios) for testing.
-- Non-destructive for existing seed artifacts: cleans up objects previously created by this seed only.
-- Notes:
--  - Uses app RPCs where available (record_contribution_v1, increment_group_balance) to preserve ledger audit.
--  - Expects platform admin 'torrymsimango@gmail.com' to exist. If missing the script will abort.

DO $$
DECLARE
    v_admin_id UUID;
    v_group_id UUID;
    v_member_id UUID;
    v_extra_admin_id UUID;
    v_group_idx INT;
    v_member_idx INT;
    v_group_name TEXT;
    v_group_type TEXT;
    v_group_balance NUMERIC(12,2);
    v_contrib_amt NUMERIC(10,2);
    v_types TEXT[] := ARRAY['burial_society','stokvel','rosca','investment_club','emergency_fund','community_savings','tontine','stokvel','burial_society','other'];
    v_provinces TEXT[] := ARRAY['Gauteng','Western Cape','KwaZulu-Natal','Eastern Cape','Free State','Limpopo','Mpumalanga','North West','Northern Cape','Free State'];
    v_cities TEXT[] := ARRAY['Johannesburg','Cape Town','Durban','Gqeberha','Bloemfontein','Polokwane','Mbombela','Mahikeng','Kimberley','Bloemfontein'];
    v_town TEXT := 'Test Township';
    v_extra_admin_password TEXT := 'SeedExtra@123';
BEGIN
    -- Ensure platform admin exists
    SELECT id INTO v_admin_id FROM auth.users WHERE lower(email) = lower('torrymsimango@gmail.com') LIMIT 1;
    IF v_admin_id IS NULL THEN
        RAISE EXCEPTION 'Platform admin user not found. Please run platform admin setup or create % first.', 'torrymsimango@gmail.com';
    END IF;

    -- Cleanup previous runs of this seed (non-destructive to other data)
    DELETE FROM public.audit_logs WHERE details->>'seed' = 'true' AND action = 'TEST_10X100_GROUP_CREATED';
    DELETE FROM public.platform_ledger WHERE description LIKE 'TEST_10X100 %';
    DELETE FROM public.group_ledger WHERE description LIKE 'TEST_10X100 %';
    DELETE FROM public.payouts WHERE payout_reference LIKE 'TEST_10X100_%';
    DELETE FROM public.groups WHERE name LIKE 'TEST10X100-G%';

    FOR v_group_idx IN 1..10 LOOP
        v_group_type := v_types[v_group_idx];
        v_group_name := format('TEST10X100-G%s %s', lpad(v_group_idx::text,2,'0'), initcap(replace(v_group_type,'_',' ')));
        v_group_balance := (5000 + (v_group_idx * 1000))::numeric(12,2);
        v_contrib_amt := (200 + (v_group_idx * 5))::numeric(10,2);

        -- Upsert group by name (select then insert/update to avoid requiring unique index)
        SELECT id INTO v_group_id FROM public.groups WHERE name = v_group_name LIMIT 1;
        IF v_group_id IS NULL THEN
            INSERT INTO public.groups (
                id, name, type, province, city, township, description, joining_fee, monthly_contribution,
                bank_name, account_number, branch_code, account_type, gateway_public_key, balance, admin_user_id,
                fee_status, registration_paid, is_platform_suspended, constitution_status, created_at, updated_at
            ) VALUES (
                gen_random_uuid(), v_group_name, v_group_type, v_provinces[v_group_idx], v_cities[v_group_idx], v_town,
                'Test seed group for stress + scenarios', 150.00, v_contrib_amt,
                'FNB', '6200' || lpad(v_group_idx::text,7,'0'), '250655', 'Savings', 'pk_test_10x100_' || v_group_idx, v_group_balance, v_admin_id,
                'paid', TRUE, FALSE, 'verified', now(), now()
            ) RETURNING id INTO v_group_id;
        ELSE
            UPDATE public.groups SET balance = v_group_balance, updated_at = now() WHERE id = v_group_id;
        END IF;

        -- Ensure group opening ledger (idempotent by description + group)
        IF NOT EXISTS (SELECT 1 FROM public.group_ledger WHERE group_id = v_group_id AND description = format('TEST_10X100 opening %s', v_group_idx)) THEN
            INSERT INTO public.group_ledger (id, group_id, transaction_id, amount, balance_after, description, category, created_at)
            VALUES (gen_random_uuid(), v_group_id, NULL, 0.00, v_group_balance, format('TEST_10X100 opening %s', v_group_idx), 'opening_balance', now() - interval '30 days');
        END IF;

        -- Create 3 extra admin users per group and make them members
        FOR v_member_idx IN 1..3 LOOP
            -- build extra admin email
            PERFORM 1; -- no-op to allow loop
            SELECT id INTO v_extra_admin_id FROM auth.users WHERE lower(email) = lower(format('test.extraadmin.g%s.%s@example.com', lpad(v_group_idx::text,2,'0'), v_member_idx::text)) LIMIT 1;
            IF v_extra_admin_id IS NULL THEN
                v_extra_admin_id := gen_random_uuid();
                INSERT INTO auth.users (id, aud, role, email, encrypted_password, email_confirmed_at, raw_app_meta_data, raw_user_meta_data, created_at, updated_at)
                VALUES (v_extra_admin_id, 'authenticated', 'authenticated', format('test.extraadmin.g%s.%s@example.com', lpad(v_group_idx::text,2,'0'), v_member_idx::text), crypt(v_extra_admin_password, gen_salt('bf')), now(), '{"provider":"email","providers":["email"],"role":"group_admin"}'::jsonb, jsonb_build_object('full_name', format('Extra Admin %s-%s', v_group_idx, v_member_idx), 'role', 'group_admin'), now(), now());
            ELSE
                UPDATE auth.users SET encrypted_password = crypt(v_extra_admin_password, gen_salt('bf')), email_confirmed_at = COALESCE(email_confirmed_at, now()), raw_user_meta_data = COALESCE(raw_user_meta_data, '{}'::jsonb) || jsonb_build_object('full_name', format('Extra Admin %s-%s', v_group_idx, v_member_idx), 'role', 'group_admin') WHERE id = v_extra_admin_id;
            END IF;

            INSERT INTO auth.identities (id, user_id, provider, provider_id, identity_data, created_at, updated_at)
            VALUES (gen_random_uuid(), v_extra_admin_id, 'email', format('test.extraadmin.g%s.%s@example.com', lpad(v_group_idx::text,2,'0'), v_member_idx::text), jsonb_build_object('sub', v_extra_admin_id::text, 'email', format('test.extraadmin.g%s.%s@example.com', lpad(v_group_idx::text,2,'0'), v_member_idx::text)), now(), now())
            ON CONFLICT (provider, provider_id) DO UPDATE SET user_id = EXCLUDED.user_id, identity_data = EXCLUDED.identity_data, updated_at = now();

            INSERT INTO public.profiles (id, full_name, email, role)
            VALUES (v_extra_admin_id, format('Extra Admin %s-%s', v_group_idx, v_member_idx), format('test.extraadmin.g%s.%s@example.com', lpad(v_group_idx::text,2,'0'), v_member_idx::text), 'group_admin')
            ON CONFLICT (id) DO UPDATE SET full_name = EXCLUDED.full_name, email = EXCLUDED.email, role = 'group_admin';

            -- Add as member
            INSERT INTO public.members (id, group_id, user_id, full_name, email, phone, status, joined_at, member_key)
            VALUES (gen_random_uuid(), v_group_id, v_extra_admin_id, format('Extra Admin %s-%s', v_group_idx, v_member_idx), format('test.extraadmin.g%s.%s@example.com', lpad(v_group_idx::text,2,'0'), v_member_idx::text), format('07%s', lpad((v_group_idx*100 + v_member_idx)::text,8,'0')), 'active', now() - interval '90 days', format('TEST10X100_ADMIN_%s_%s', v_group_idx, v_member_idx))
            ON CONFLICT DO NOTHING;
        END LOOP;

        -- Create 100 members per group
        FOR v_member_idx IN 1..100 LOOP
            INSERT INTO public.members (id, group_id, user_id, full_name, email, phone, status, joined_at, member_key)
            VALUES (
                gen_random_uuid(),
                v_group_id,
                NULL,
                format('Test Member %s-%s', v_group_idx, v_member_idx),
                format('test.member.g%s.%s@example.com', lpad(v_group_idx::text,2,'0'), lpad(v_member_idx::text,3,'0')),
                format('07%s', lpad((v_group_idx*1000 + v_member_idx)::text,8,'0')),
                CASE WHEN v_member_idx > 95 THEN 'suspended' WHEN v_member_idx > 85 THEN 'probation' WHEN v_member_idx % 10 = 0 THEN 'pending_payment' ELSE 'active' END,
                now() - ((v_member_idx % 90) || ' days')::interval,
                format('TEST10X100_%s_%s', v_group_idx, v_member_idx)
            ) RETURNING id INTO v_member_id;

            -- Seed varying contributions: give first 60 members two paid contributions each
            IF v_member_idx <= 60 THEN
                PERFORM public.record_contribution_v1(v_member_id, v_group_id, v_contrib_amt, current_date - 60, now() - interval '59 days', 'paid', format('test10x100_tx_g%s_m%s_1', lpad(v_group_idx::text,2,'0'), lpad(v_member_idx::text,3,'0')));
                PERFORM public.record_contribution_v1(v_member_id, v_group_id, v_contrib_amt, current_date - 30, now() - interval '29 days', 'paid', format('test10x100_tx_g%s_m%s_2', lpad(v_group_idx::text,2,'0'), lpad(v_member_idx::text,3,'0')));
            END IF;

            -- Create a pending due for many members
            IF (v_member_idx % 5) = 0 THEN
                INSERT INTO public.contributions (id, member_id, group_id, amount, due_date, status, payment_method, created_at)
                VALUES (gen_random_uuid(), v_member_id, v_group_id, v_contrib_amt, current_date + (v_member_idx % 30), 'due', 'bank', now());
            END IF;
        END LOOP;

        -- Create a variety of payouts and disbursements
        INSERT INTO public.payouts (id, group_id, amount, bank_name, account_no, branch_code, status, processed_by, processed_at, payout_reference, created_at)
        VALUES (gen_random_uuid(), v_group_id, 1500.00 + v_group_idx*100, 'Standard Bank', '6200' || lpad((1000+v_group_idx)::text,7,'0'), '250655', CASE WHEN v_group_idx % 3 = 0 THEN 'completed' WHEN v_group_idx % 3 = 1 THEN 'processing' ELSE 'pending' END, CASE WHEN v_group_idx % 3 = 0 THEN v_admin_id ELSE NULL END, CASE WHEN v_group_idx % 3 = 0 THEN now() - interval '2 days' ELSE NULL END, format('TEST_10X100_PAYOUT_%s', v_group_idx), now())
        ON CONFLICT DO NOTHING;

        -- If payout was completed, decrement the group balance via RPC for ledger correctness
        IF (v_group_idx % 3) = 0 THEN
            PERFORM public.increment_group_balance(v_group_id, -(1500.00 + v_group_idx*100));
        END IF;

        -- Add audit log for group creation by seed
        INSERT INTO public.audit_logs (id, actor_id, target_group_id, action, details, created_at)
        VALUES (gen_random_uuid(), v_admin_id, v_group_id, 'TEST_10X100_GROUP_CREATED', jsonb_build_object('seed', true, 'group_index', v_group_idx, 'members', 100), now())
        ON CONFLICT (id) DO NOTHING;

    END LOOP;

    RAISE NOTICE 'TEST_10X100 seed completed: 10 groups x 100 members created/ensured.';
END$$;
