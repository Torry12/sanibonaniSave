-- Backfill members.user_id for seeded member records so RLS member reads work.
-- Idempotent: safe to run repeatedly.

BEGIN;

DO $$
DECLARE
    v_member RECORD;
    v_auth_user_id UUID;
    v_seed_member_password CONSTANT TEXT := 'Test@12345';
BEGIN
    -- First, link any member row to an existing auth user by email.
    UPDATE public.members AS m
    SET user_id = u.id,
        updated_at = now()
    FROM auth.users AS u
    WHERE m.user_id IS NULL
      AND m.email IS NOT NULL
      AND lower(m.email) = lower(u.email);

    -- Then provision missing auth users only for deterministic seed-email patterns.
    FOR v_member IN
        SELECT m.id, m.email, COALESCE(NULLIF(m.full_name, ''), 'Seed Member') AS full_name
        FROM public.members AS m
        WHERE m.user_id IS NULL
          AND m.email IS NOT NULL
          AND (
              m.email LIKE 'safe.member.%@example.com'
              OR m.email LIKE 'seed.member.%@example.com'
              OR m.email LIKE 'test.member.g%@example.com'
          )
    LOOP
        SELECT u.id INTO v_auth_user_id
        FROM auth.users AS u
        WHERE lower(u.email) = lower(v_member.email)
        LIMIT 1;

        IF v_auth_user_id IS NULL THEN
            v_auth_user_id := gen_random_uuid();
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
                updated_at
            )
            VALUES (
                v_auth_user_id,
                'authenticated',
                'authenticated',
                v_member.email,
                crypt(v_seed_member_password, gen_salt('bf')),
                now(),
                '{"provider":"email","providers":["email"],"role":"member"}'::jsonb,
                jsonb_build_object('full_name', v_member.full_name, 'role', 'member'),
                now(),
                now()
            );
        ELSE
            UPDATE auth.users
            SET email_confirmed_at = COALESCE(email_confirmed_at, now()),
                raw_app_meta_data = COALESCE(raw_app_meta_data, '{}'::jsonb) || '{"provider":"email","providers":["email"],"role":"member"}'::jsonb,
                raw_user_meta_data = COALESCE(raw_user_meta_data, '{}'::jsonb) || jsonb_build_object('full_name', v_member.full_name, 'role', 'member'),
                updated_at = now()
            WHERE id = v_auth_user_id;
        END IF;

        INSERT INTO auth.identities (
            id,
            user_id,
            provider,
            provider_id,
            identity_data,
            created_at,
            updated_at
        )
        VALUES (
            gen_random_uuid(),
            v_auth_user_id,
            'email',
            v_member.email,
            jsonb_build_object('sub', v_auth_user_id::text, 'email', v_member.email),
            now(),
            now()
        )
        ON CONFLICT (provider, provider_id)
        DO UPDATE SET
            user_id = EXCLUDED.user_id,
            identity_data = EXCLUDED.identity_data,
            updated_at = now();

        INSERT INTO public.profiles (id, full_name, email, role)
        VALUES (v_auth_user_id, v_member.full_name, v_member.email, 'member')
        ON CONFLICT (id)
        DO UPDATE SET
            full_name = EXCLUDED.full_name,
            email = EXCLUDED.email,
            role = 'member';

        UPDATE public.members
        SET user_id = v_auth_user_id,
            updated_at = now()
        WHERE id = v_member.id;
    END LOOP;
END $$;

COMMIT;

NOTIFY pgrst, 'reload schema';

