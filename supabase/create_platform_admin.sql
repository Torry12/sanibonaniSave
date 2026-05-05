DO $$
DECLARE
    v_email text := 'torrymsimango@hotmail.com';
    v_password text := 'torry123M';
    v_full_name text := 'Torry Msimango';
    v_user_id uuid;
BEGIN
    SELECT id INTO v_user_id
    FROM auth.users
    WHERE email = v_email
    LIMIT 1;

    IF v_user_id IS NULL THEN
        v_user_id := gen_random_uuid();

        INSERT INTO auth.users (
            id, aud, role, email, encrypted_password,
            email_confirmed_at, raw_app_meta_data, raw_user_meta_data,
            created_at, updated_at, confirmation_token
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
            raw_app_meta_data = COALESCE(raw_app_meta_data, '{}'::jsonb) || '{"provider":"email","providers":["email"]}'::jsonb,
            raw_user_meta_data = COALESCE(raw_user_meta_data, '{}'::jsonb) || jsonb_build_object('full_name', v_full_name, 'role', 'platform_admin'),
            updated_at = NOW()
        WHERE id = v_user_id;
    END IF;

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
        RAISE NOTICE 'auth.identities not found, skipping identity upsert for %', v_email;
    END;

    INSERT INTO public.profiles (id, full_name, email, role)
    VALUES (v_user_id, v_full_name, v_email, 'platform_admin')
    ON CONFLICT (id) DO UPDATE
    SET
        full_name = EXCLUDED.full_name,
        email = EXCLUDED.email,
        role = 'platform_admin',
        updated_at = NOW();
END $$ LANGUAGE plpgsql;