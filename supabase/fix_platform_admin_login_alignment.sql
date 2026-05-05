-- ============================================================================
-- PLATFORM ADMIN ALIGNMENT FIX (SAFE)
-- Aligns auth metadata + profile role without weakening RLS globally.
-- ============================================================================

DO $$
DECLARE
    v_email TEXT := 'torrymsimango@gmail.com';
    v_password TEXT := 'torry123M';
    v_full_name TEXT := 'Torry Msimango';
    v_user_id UUID;
BEGIN
    -- 1) Locate existing auth user (preferred path: account already exists).
    SELECT id
    INTO v_user_id
    FROM auth.users
    WHERE lower(email) = lower(v_email)
    LIMIT 1;

    -- 2) Create user only when missing; otherwise align password and metadata.
    IF v_user_id IS NULL THEN
        v_user_id := gen_random_uuid();

        INSERT INTO auth.users (
            id,
            aud,
            role,
            email,
            encrypted_password,
            email_confirmed_at,
            raw_app_meta_data,
            raw_user_meta_data,
            created_at,
            updated_at,
            confirmation_token
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
            ''
        );
    ELSE
        UPDATE auth.users
        SET
            encrypted_password = extensions.crypt(v_password, extensions.gen_salt('bf')),
            email_confirmed_at = COALESCE(email_confirmed_at, NOW()),
            raw_app_meta_data = COALESCE(raw_app_meta_data, '{}'::jsonb)
                || '{"provider":"email","providers":["email"]}'::jsonb,
            raw_user_meta_data = COALESCE(raw_user_meta_data, '{}'::jsonb)
                || jsonb_build_object('full_name', v_full_name, 'role', 'platform_admin'),
            updated_at = NOW()
        WHERE id = v_user_id;
    END IF;

    -- 3) Ensure email identity row exists (required by some Supabase auth flows).
    BEGIN
        INSERT INTO auth.identities (
            id,
            user_id,
            provider,
            provider_id,
            identity_data,
            created_at,
            updated_at,
            last_sign_in_at
        ) VALUES (
            gen_random_uuid(),
            v_user_id,
            'email',
            lower(v_email),
            jsonb_build_object('sub', v_user_id::text, 'email', v_email),
            NOW(),
            NOW(),
            NOW()
        )
        ON CONFLICT (provider, provider_id) DO UPDATE
        SET
            user_id = EXCLUDED.user_id,
            identity_data = EXCLUDED.identity_data,
            updated_at = NOW();
    EXCEPTION WHEN undefined_table THEN
        RAISE NOTICE 'auth.identities table not found, skipping identity upsert';
    END;

    -- 4) Upsert public profile role for app routing + RLS checks.
    INSERT INTO public.profiles (id, full_name, email, role)
    VALUES (v_user_id, v_full_name, v_email, 'platform_admin')
    ON CONFLICT (id) DO UPDATE
    SET
        full_name = EXCLUDED.full_name,
        email = EXCLUDED.email,
        role = 'platform_admin',
        updated_at = NOW();

    -- 5) Optional cleanup of conflicting legacy test account profile only.
    DELETE FROM public.profiles
    WHERE email = 'platformadmin@test.com'
      AND id <> v_user_id;
END $$ LANGUAGE plpgsql;

-- 6) Keep permissions minimal here; policy scripts should define broader access.
GRANT USAGE ON SCHEMA public TO authenticated;
GRANT SELECT, UPDATE ON TABLE public.profiles TO authenticated;

-- 7) Create profile self-read policy only if it does not already exist.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_policies
        WHERE schemaname = 'public'
          AND tablename = 'profiles'
          AND policyname = 'View Own Profile'
    ) THEN
        CREATE POLICY "View Own Profile"
        ON public.profiles
        FOR SELECT TO authenticated
        USING (auth.uid() = id);
    END IF;
END $$;

-- Verification Queries
SELECT
    'AUTH USER' AS source,
    id,
    email,
    raw_user_meta_data ->> 'role' AS role
FROM auth.users
WHERE lower(email) = lower('torrymsimango@gmail.com')

UNION ALL

SELECT
    'PUBLIC PROFILE' AS source,
    id,
    email,
    role
FROM public.profiles
WHERE lower(email) = lower('torrymsimango@gmail.com');
