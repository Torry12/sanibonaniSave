-- ============================================================================
-- SanibonaniSave — PLATFORM ADMIN ALIGNMENT V4
-- Ensures the platform admin account matches the app's hardcoded policy.
-- ============================================================================

DO $$
DECLARE
    v_email TEXT := 'torrymsimango@gmail.com';
    v_password TEXT := 'torry123M';
    v_full_name TEXT := 'Torry Msimango';
    v_user_id UUID := '1b8aca84-c136-4c1b-b024-902584ae80d8'; -- Deterministic ID
BEGIN
    -- 1) Remove any conflicting user with the same email but different ID
    DELETE FROM auth.users WHERE lower(email) = lower(v_email) AND id <> v_user_id;

    -- 2) Upsert the Auth User
    INSERT INTO auth.users (
        id, aud, role, email, encrypted_password,
        email_confirmed_at, raw_app_meta_data, raw_user_meta_data,
        created_at, updated_at, confirmation_token, is_super_admin
    ) VALUES (
        v_user_id,
        'authenticated',
        'authenticated',
        v_email,
        extensions.crypt(v_password, extensions.gen_salt('bf')),
        NOW(),
        '{"provider":"email","providers":["email"]}'::jsonb,
        jsonb_build_object('full_name', v_full_name, 'role', 'platform_admin'),
        NOW(),
        NOW(),
        '',
        TRUE
    )
    ON CONFLICT (id) DO UPDATE
    SET
        email = v_email,
        encrypted_password = extensions.crypt(v_password, extensions.gen_salt('bf')),
        email_confirmed_at = COALESCE(auth.users.email_confirmed_at, NOW()),
        raw_user_meta_data = jsonb_build_object('full_name', v_full_name, 'role', 'platform_admin'),
        updated_at = NOW();

    -- 3) Ensure Identity exists (Critical for standard email sign-in)
    INSERT INTO auth.identities (id, user_id, provider, provider_id, identity_data, created_at, updated_at)
    VALUES (gen_random_uuid(), v_user_id, 'email', lower(v_email), jsonb_build_object('sub', v_user_id, 'email', v_email), NOW(), NOW())
    ON CONFLICT (provider, provider_id) DO UPDATE SET user_id = v_user_id;

    -- 4) Upsert Public Profile
    INSERT INTO public.profiles (id, full_name, email, role)
    VALUES (v_user_id, v_full_name, v_email, 'platform_admin')
    ON CONFLICT (id) DO UPDATE
    SET role = 'platform_admin', email = v_email, full_name = v_full_name, updated_at = NOW();

    -- 5) Clear any existing platform admin roles from other users to avoid confusion
    UPDATE public.profiles SET role = 'member' WHERE role = 'platform_admin' AND id <> v_user_id;

    RAISE NOTICE 'Platform Admin aligned: %', v_email;
END $$;

-- ── Verification ──
SELECT
    u.id,
    u.email,
    p.role as profile_role,
    (u.raw_user_meta_data->>'role') as metadata_role
FROM auth.users u
JOIN public.profiles p ON u.id = p.id
WHERE lower(u.email) = 'torrymsimango@gmail.com';
