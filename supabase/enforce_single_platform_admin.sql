-- Enforce a single canonical platform admin account (idempotent)
-- Run in Supabase SQL Editor with a privileged role.

DO $$
DECLARE
    v_canonical_email TEXT := 'torrymsimango@gmail.com';
    v_canonical_password TEXT := 'torry123M';
    v_canonical_name TEXT := 'Torry Msimango';
    v_canonical_user_id UUID;
BEGIN
    -- 1) Ensure canonical auth user exists and is configured as platform_admin.
    SELECT id INTO v_canonical_user_id
    FROM auth.users
    WHERE email = v_canonical_email
    LIMIT 1;

    IF v_canonical_user_id IS NULL THEN
        v_canonical_user_id := gen_random_uuid();

        INSERT INTO auth.users (
            id, aud, role, email, encrypted_password,
            email_confirmed_at, raw_app_meta_data, raw_user_meta_data,
            created_at, updated_at, confirmation_token
        ) VALUES (
            v_canonical_user_id,
            'authenticated',
            'authenticated',
            v_canonical_email,
            extensions.crypt(v_canonical_password, extensions.gen_salt('bf')),
            NOW(),
            '{"provider":"email","providers":["email"]}'::jsonb,
            jsonb_build_object('full_name', v_canonical_name, 'role', 'platform_admin'),
            NOW(),
            NOW(),
            ''
        );
    ELSE
        UPDATE auth.users
        SET
            encrypted_password = extensions.crypt(v_canonical_password, extensions.gen_salt('bf')),
            email_confirmed_at = COALESCE(email_confirmed_at, NOW()),
            raw_app_meta_data = COALESCE(raw_app_meta_data, '{}'::jsonb) || '{"provider":"email","providers":["email"]}'::jsonb,
            raw_user_meta_data = COALESCE(raw_user_meta_data, '{}'::jsonb) || jsonb_build_object('full_name', v_canonical_name, 'role', 'platform_admin'),
            updated_at = NOW()
        WHERE id = v_canonical_user_id;
    END IF;

    INSERT INTO public.profiles (id, full_name, email, role)
    VALUES (v_canonical_user_id, v_canonical_name, v_canonical_email, 'platform_admin')
    ON CONFLICT (id) DO UPDATE SET
        full_name = EXCLUDED.full_name,
        email = EXCLUDED.email,
        role = 'platform_admin',
        updated_at = NOW();

    -- 2) Demote any non-canonical platform admin metadata in auth.users.
    UPDATE auth.users
    SET
        raw_user_meta_data = COALESCE(raw_user_meta_data, '{}'::jsonb) || '{"role":"member"}'::jsonb,
        updated_at = NOW()
    WHERE email <> v_canonical_email
      AND COALESCE(raw_user_meta_data ->> 'role', '') = 'platform_admin';

    -- 3) Demote any non-canonical platform admin profiles.
    UPDATE public.profiles
    SET role = 'member', updated_at = NOW()
    WHERE email <> v_canonical_email
      AND role = 'platform_admin';
END $$ LANGUAGE plpgsql;

-- Verification: should return exactly one row (canonical email only)
SELECT p.id, p.email, p.role
FROM public.profiles p
WHERE p.role = 'platform_admin'
ORDER BY p.email;

-- Verification: should return 0 rows
SELECT u.id, u.email, u.raw_user_meta_data ->> 'role' AS auth_role
FROM auth.users u
WHERE u.email <> 'torrymsimango@gmail.com'
  AND COALESCE(u.raw_user_meta_data ->> 'role', '') = 'platform_admin';

