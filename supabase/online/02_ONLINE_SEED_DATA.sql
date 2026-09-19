-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — ONLINE TEST SEED DATA
-- Version: 1.0 (Targeted Integration Testing)
-- ─────────────────────────────────────────────────────────────────────────────

DO $$
DECLARE
    v_admin_id UUID;
    v_test_member_id UUID;
    v_group_id UUID;
    v_member_password text := 'test123M';
BEGIN
    -- 1. Get Platform Admin
    SELECT id INTO v_admin_id FROM auth.users WHERE email = 'torrymsimango@gmail.com' LIMIT 1;

    -- 2. Create standard Online Test Member (if not exists)
    -- This allows testing login with a non-admin account online.
    SELECT id INTO v_test_member_id FROM auth.users WHERE email = 'test.member@sanibonani.co.za' LIMIT 1;

    IF v_test_member_id IS NULL THEN
        v_test_member_id := gen_random_uuid();
        INSERT INTO auth.users (
            id, instance_id, aud, role, email, encrypted_password, email_confirmed_at,
            raw_app_meta_data, raw_user_meta_data, created_at, updated_at
        )
        VALUES (
            v_test_member_id, '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated',
            'test.member@sanibonani.co.za', crypt(v_member_password, gen_salt('bf')), now(),
            '{"provider":"email","providers":["email"],"role":"member"}',
            jsonb_build_object('full_name', 'Online Test Member', 'role', 'member'),
            now(), now()
        );

        INSERT INTO auth.identities (id, user_id, provider, provider_id, identity_data, created_at, updated_at)
        VALUES (gen_random_uuid(), v_test_member_id, 'email', 'test.member@sanibonani.co.za',
                jsonb_build_object('sub', v_test_member_id, 'email', 'test.member@sanibonani.co.za'), now(), now());

        INSERT INTO public.profiles (id, full_name, email, role)
        VALUES (v_test_member_id, 'Online Test Member', 'test.member@sanibonani.co.za', 'member');
    END IF;

    -- 3. Create one "Gold Standard" test group for each type
    -- These are identifiable by the 'ONLINE-TEST-' prefix in their names.

    -- Cleanup existing online test groups
    DELETE FROM public.groups WHERE name LIKE 'ONLINE-TEST-%';

    -- Burial Society
    INSERT INTO public.groups (name, type, province, city, description, joining_fee, monthly_contribution, admin_user_id, balance)
    VALUES ('ONLINE-TEST-Burial-Society', 'burial_society', 'Gauteng', 'Johannesburg', 'Targeted online testing for Burial Societies.', 250.00, 150.00, v_admin_id, 1000.00)
    RETURNING id INTO v_group_id;

    INSERT INTO public.members (group_id, user_id, full_name, email, role, status, member_key)
    VALUES (v_group_id, v_test_member_id, 'Online Test Member', 'test.member@sanibonani.co.za', 'member', 'active', 'ONLINE-MEMBER-1');

    -- Stokvel
    INSERT INTO public.groups (name, type, province, city, description, joining_fee, monthly_contribution, admin_user_id, balance)
    VALUES ('ONLINE-TEST-Stokvel', 'stokvel', 'Western Cape', 'Cape Town', 'Targeted online testing for Stokvels.', 100.00, 500.00, v_admin_id, 5000.00)
    RETURNING id INTO v_group_id;

    INSERT INTO public.members (group_id, user_id, full_name, email, role, status, member_key)
    VALUES (v_group_id, v_test_member_id, 'Online Test Member', 'test.member@sanibonani.co.za', 'member', 'active', 'ONLINE-MEMBER-2');

    RAISE NOTICE 'Online Test Seed Data injected successfully.';
END $$;
