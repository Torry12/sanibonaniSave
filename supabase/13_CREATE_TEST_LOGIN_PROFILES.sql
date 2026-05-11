-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — TEST LOGIN PROFILES (Platform Admin / Group Admin / Members)
-- Purpose: Provision deterministic auth users for login testing across roles.
-- Depends on: 01_DATABASE_SCHEMA.sql, 03_PLATFORM_ADMIN_SETUP.sql,
--             11_SEED_TEST_DATA_10_GROUPS_100_MEMBERS.sql
-- ─────────────────────────────────────────────────────────────────────────────

DO $$
DECLARE
    v_platform_admin_email CONSTANT TEXT := 'torrymsimango@gmail.com';
    v_platform_admin_password CONSTANT TEXT := 'torry123M';

    v_group_admin_email CONSTANT TEXT := 'test.groupadmin@example.com';
    v_group_admin_password CONSTANT TEXT := 'Test@12345';
    v_group_admin_name CONSTANT TEXT := 'Test Group Admin';

    v_member_password CONSTANT TEXT := 'Test@12345';

    v_platform_admin_id UUID;
    v_group_admin_id UUID;
    v_target_group_id UUID;

    v_member_id UUID;
    v_member_email TEXT;
    v_member_name TEXT;
    v_auth_user_id UUID;
    v_identity_id UUID;
BEGIN
    -- 1) Ensure Platform Admin exists and has known test password.
    SELECT id INTO v_platform_admin_id
    FROM auth.users
    WHERE email = v_platform_admin_email
    LIMIT 1;

    IF v_platform_admin_id IS NULL THEN
        RAISE EXCEPTION 'Platform admin % not found. Run 03_PLATFORM_ADMIN_SETUP.sql first.', v_platform_admin_email;
    END IF;

    UPDATE auth.users
    SET encrypted_password = crypt(v_platform_admin_password, gen_salt('bf')),
        email_confirmed_at = COALESCE(email_confirmed_at, now()),
        raw_app_meta_data = COALESCE(raw_app_meta_data, '{}'::jsonb) || '{"provider":"email","providers":["email"],"role":"platform_admin"}'::jsonb,
        raw_user_meta_data = COALESCE(raw_user_meta_data, '{}'::jsonb) || '{"role":"platform_admin"}'::jsonb
    WHERE id = v_platform_admin_id;

    INSERT INTO auth.identities (id, user_id, provider, provider_id, identity_data, created_at, updated_at)
    VALUES (
        gen_random_uuid(),
        v_platform_admin_id,
        'email',
        v_platform_admin_email,
        jsonb_build_object('sub', v_platform_admin_id::text, 'email', v_platform_admin_email),
        now(), now()
    )
    ON CONFLICT (provider, provider_id) DO NOTHING;

    INSERT INTO public.profiles (id, full_name, email, role)
    VALUES (v_platform_admin_id, 'Torry Msimango', v_platform_admin_email, 'platform_admin')
    ON CONFLICT (id) DO UPDATE
        SET email = EXCLUDED.email,
            role = 'platform_admin';

    -- 2) Pick a seeded group to assign to a dedicated test group admin.
    SELECT id INTO v_target_group_id
    FROM public.groups
    WHERE name LIKE 'SEED-G%'
    ORDER BY created_at ASC
    LIMIT 1;

    IF v_target_group_id IS NULL THEN
        RAISE EXCEPTION 'No seeded test groups found. Run 11_SEED_TEST_DATA_10_GROUPS_100_MEMBERS.sql first.';
    END IF;

    -- 3) Create or update Group Admin auth user.
    SELECT id INTO v_group_admin_id
    FROM auth.users
    WHERE email = v_group_admin_email
    LIMIT 1;

    IF v_group_admin_id IS NULL THEN
        v_group_admin_id := gen_random_uuid();
        INSERT INTO auth.users (
            id, aud, role, email, encrypted_password, email_confirmed_at,
            raw_app_meta_data, raw_user_meta_data, created_at, updated_at
        )
        VALUES (
            v_group_admin_id,
            'authenticated',
            'authenticated',
            v_group_admin_email,
            crypt(v_group_admin_password, gen_salt('bf')),
            now(),
            '{"provider":"email","providers":["email"],"role":"group_admin"}'::jsonb,
            jsonb_build_object('full_name', v_group_admin_name, 'role', 'group_admin'),
            now(),
            now()
        );
    ELSE
        UPDATE auth.users
        SET encrypted_password = crypt(v_group_admin_password, gen_salt('bf')),
            email_confirmed_at = COALESCE(email_confirmed_at, now()),
            raw_app_meta_data = COALESCE(raw_app_meta_data, '{}'::jsonb) || '{"provider":"email","providers":["email"],"role":"group_admin"}'::jsonb,
            raw_user_meta_data = COALESCE(raw_user_meta_data, '{}'::jsonb) || jsonb_build_object('full_name', v_group_admin_name, 'role', 'group_admin'),
            updated_at = now()
        WHERE id = v_group_admin_id;
    END IF;

    INSERT INTO auth.identities (id, user_id, provider, provider_id, identity_data, created_at, updated_at)
    VALUES (
        gen_random_uuid(),
        v_group_admin_id,
        'email',
        v_group_admin_email,
        jsonb_build_object('sub', v_group_admin_id::text, 'email', v_group_admin_email),
        now(), now()
    )
    ON CONFLICT (provider, provider_id) DO NOTHING;

    INSERT INTO public.profiles (id, full_name, email, role)
    VALUES (v_group_admin_id, v_group_admin_name, v_group_admin_email, 'group_admin')
    ON CONFLICT (id) DO UPDATE
        SET full_name = EXCLUDED.full_name,
            email = EXCLUDED.email,
            role = 'group_admin';

    -- Assign this admin to one seeded group for realistic group-admin visibility.
    UPDATE public.groups
    SET admin_user_id = v_group_admin_id
    WHERE id = v_target_group_id;

    -- 4) Create member login profiles and link them to existing seeded members.
    -- We provision 10 member logins from the target seeded group.
    FOR v_member_id, v_member_email, v_member_name IN
        SELECT m.id, m.email, m.full_name
        FROM public.members m
        WHERE m.group_id = v_target_group_id
          AND m.email LIKE 'seed.member.%@example.com'
        ORDER BY m.created_at ASC
        LIMIT 10
    LOOP
        SELECT id INTO v_auth_user_id
        FROM auth.users
        WHERE email = v_member_email
        LIMIT 1;

        IF v_auth_user_id IS NULL THEN
            v_auth_user_id := gen_random_uuid();
            INSERT INTO auth.users (
                id, aud, role, email, encrypted_password, email_confirmed_at,
                raw_app_meta_data, raw_user_meta_data, created_at, updated_at
            )
            VALUES (
                v_auth_user_id,
                'authenticated',
                'authenticated',
                v_member_email,
                crypt(v_member_password, gen_salt('bf')),
                now(),
                '{"provider":"email","providers":["email"],"role":"member"}'::jsonb,
                jsonb_build_object('full_name', v_member_name, 'role', 'member'),
                now(),
                now()
            );
        ELSE
            UPDATE auth.users
            SET encrypted_password = crypt(v_member_password, gen_salt('bf')),
                email_confirmed_at = COALESCE(email_confirmed_at, now()),
                raw_app_meta_data = COALESCE(raw_app_meta_data, '{}'::jsonb) || '{"provider":"email","providers":["email"],"role":"member"}'::jsonb,
                raw_user_meta_data = COALESCE(raw_user_meta_data, '{}'::jsonb) || jsonb_build_object('full_name', v_member_name, 'role', 'member'),
                updated_at = now()
            WHERE id = v_auth_user_id;
        END IF;

        SELECT id INTO v_identity_id
        FROM auth.identities
        WHERE provider = 'email' AND provider_id = v_member_email
        LIMIT 1;

        IF v_identity_id IS NULL THEN
            INSERT INTO auth.identities (id, user_id, provider, provider_id, identity_data, created_at, updated_at)
            VALUES (
                gen_random_uuid(),
                v_auth_user_id,
                'email',
                v_member_email,
                jsonb_build_object('sub', v_auth_user_id::text, 'email', v_member_email),
                now(), now()
            );
        END IF;

        INSERT INTO public.profiles (id, full_name, email, role)
        VALUES (v_auth_user_id, v_member_name, v_member_email, 'member')
        ON CONFLICT (id) DO UPDATE
            SET full_name = EXCLUDED.full_name,
                email = EXCLUDED.email,
                role = 'member';

        UPDATE public.members
        SET user_id = v_auth_user_id,
            status = CASE WHEN status = 'pending_payment' THEN 'active' ELSE status END
        WHERE id = v_member_id;
    END LOOP;

    RAISE NOTICE 'Test login profiles ready: platform_admin=% ; group_admin=% ; member_password=%',
        v_platform_admin_email,
        v_group_admin_email,
        v_member_password;
END $$;

