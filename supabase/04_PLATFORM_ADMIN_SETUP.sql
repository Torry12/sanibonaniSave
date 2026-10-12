-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — PLATFORM ADMIN SETUP
-- Version: 2.1 (Organized Layout - June 2026)
-- ─────────────────────────────────────────────────────────────────────────────

DO $$
DECLARE
    v_email text := 'torrymsimango@gmail.com';
    v_legacy_emails text[] := ARRAY['torryymsimango@gmail.com', 'torrymsimango@hotmail.com'];
    v_password text := 'torry123M';
    v_full_name text := 'Torry Msimango';
    v_user_id uuid;
BEGIN
    -- 1. Create or Update Auth User
    SELECT id INTO v_user_id
    FROM auth.users
    WHERE lower(email) = lower(v_email)
       OR lower(email) IN (SELECT lower(alias) FROM unnest(v_legacy_emails) AS alias)
    ORDER BY CASE WHEN lower(email) = lower(v_email) THEN 0 ELSE 1 END
    LIMIT 1;

    IF v_user_id IS NULL THEN
        v_user_id := gen_random_uuid();
        INSERT INTO auth.users (
            id, instance_id, aud, role, email, encrypted_password, email_confirmed_at,
            raw_app_meta_data, raw_user_meta_data, created_at, updated_at,
            confirmation_token, recovery_token, email_change_token_new, email_change
        )
        VALUES (
            v_user_id, '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated', v_email, extensions.crypt(v_password, extensions.gen_salt('bf', 10)), now(),
            '{"provider":"email","providers":["email"],"role":"platform_admin"}', jsonb_build_object('full_name', v_full_name, 'role', 'platform_admin'),
            now(), now(), '', '', '', ''
        );
    ELSE
        UPDATE auth.users
        SET encrypted_password = extensions.crypt(v_password, extensions.gen_salt('bf')),
            email = v_email,
            email_confirmed_at = coalesce(email_confirmed_at, now()),
            raw_app_meta_data = coalesce(raw_app_meta_data, '{}'::jsonb) || '{"provider":"email","providers":["email"],"role":"platform_admin"}'::jsonb,
            raw_user_meta_data = coalesce(raw_user_meta_data, '{}'::jsonb) || jsonb_build_object('full_name', v_full_name, 'role', 'platform_admin'),
            -- Defensive: Ensure no NULLs in columns that cause GoTrue Scan errors
            email_change_token_new = COALESCE(email_change_token_new, ''),
            confirmation_token = COALESCE(confirmation_token, ''),
            recovery_token = COALESCE(recovery_token, ''),
            email_change = COALESCE(email_change, '')
        WHERE id = v_user_id;
    END IF;

    -- 2. Ensure Identity
    DELETE FROM auth.identities
    WHERE user_id = v_user_id
      AND provider = 'email'
      AND provider_id <> v_email;

    INSERT INTO auth.identities (id, user_id, provider, provider_id, identity_data, created_at, updated_at)
    VALUES (gen_random_uuid(), v_user_id, 'email', v_email, jsonb_build_object('sub', v_user_id, 'email', v_email), now(), now())
    ON CONFLICT (provider, provider_id) DO UPDATE
    SET user_id = EXCLUDED.user_id,
        identity_data = EXCLUDED.identity_data,
        updated_at = now();

    -- Remove legacy aliases if they exist so they do not conflict
    DELETE FROM auth.users
    WHERE lower(email) IN (SELECT lower(alias) FROM unnest(v_legacy_emails) AS alias)
      AND id <> v_user_id;

    -- 3. Create Profile
    -- Remove stale profile rows for canonical/legacy emails so id-based upsert stays deterministic.
    DELETE FROM public.profiles
    WHERE id <> v_user_id
      AND (
          lower(email) = lower(v_email)
          OR lower(email) IN (SELECT lower(alias) FROM unnest(v_legacy_emails) AS alias)
      );

    INSERT INTO public.profiles (id, full_name, email, role)
    VALUES (v_user_id, v_full_name, v_email, 'platform_admin')
    ON CONFLICT (id) DO UPDATE
    SET full_name = EXCLUDED.full_name,
        email = EXCLUDED.email,
        role = 'platform_admin';

    RAISE NOTICE 'Platform Admin setup complete for %', v_email;
END $$;
