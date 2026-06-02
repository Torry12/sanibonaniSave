-- ─────────────────────────────────────────────────────────────────────────────
-- Backfill seeded member auth links
-- Purpose:
--   1) Link existing public.members rows to auth.users by email.
--   2) Create missing auth users/identities/profiles for known seeded member emails.
--   3) Ensure public.members.user_id is populated for seeded members.
-- ─────────────────────────────────────────────────────────────────────────────

BEGIN;

-- 1) Fast path: link by existing auth identity email owner.
UPDATE public.members m
SET user_id = i.user_id,
    updated_at = now()
FROM auth.identities i
WHERE m.user_id IS NULL
  AND m.email IS NOT NULL
  AND i.provider = 'email'
  AND lower(m.email) = lower(i.provider_id);

-- 1b) Fallback: link by existing auth user email.
UPDATE public.members m
SET user_id = u.id,
    updated_at = now()
FROM auth.users u
WHERE m.user_id IS NULL
  AND m.email IS NOT NULL
  AND lower(m.email) = lower(u.email);

DO $$
DECLARE
    v_member RECORD;
    v_auth_user_id UUID;
    -- Seed-only deterministic credential used by existing test-login documentation.
    -- Do not use these accounts as-is in production environments.
    v_member_password CONSTANT TEXT := 'Test@12345';
BEGIN
    -- 2) Create/update auth users for seeded member email patterns still missing links.
    FOR v_member IN
        SELECT
            m.id,
            lower(trim(m.email)) AS email,
            COALESCE(NULLIF(trim(m.full_name), ''), split_part(lower(trim(m.email)), '@', 1)) AS full_name
        FROM public.members m
        WHERE m.user_id IS NULL
          AND m.email IS NOT NULL
          AND (
              lower(m.email) LIKE 'seed.member.%@example.com'
              OR lower(m.email) LIKE 'safe.member.%@example.com'
              OR lower(m.email) LIKE 'test.member.g%@example.com'
          )
    LOOP
        SELECT i.user_id
        INTO v_auth_user_id
        FROM auth.identities i
        WHERE i.provider = 'email'
          AND lower(i.provider_id) = v_member.email
        LIMIT 1;

        IF v_auth_user_id IS NULL THEN
            SELECT u.id
            INTO v_auth_user_id
            FROM auth.users u
            WHERE lower(u.email) = v_member.email
            LIMIT 1;
        END IF;

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
                v_member.email,
                crypt(v_member_password, gen_salt('bf')),
                now(),
                '{"provider":"email","providers":["email"],"role":"member"}'::jsonb,
                jsonb_build_object('full_name', v_member.full_name, 'role', 'member'),
                now(),
                now()
            )
            ON CONFLICT (email) DO UPDATE
                SET email_confirmed_at = COALESCE(auth.users.email_confirmed_at, now()),
                    raw_app_meta_data = jsonb_set(COALESCE(auth.users.raw_app_meta_data, '{}'::jsonb), '{role}', '"member"'::jsonb, true),
                    raw_user_meta_data = COALESCE(auth.users.raw_user_meta_data, '{}'::jsonb) || jsonb_build_object('full_name', v_member.full_name, 'role', 'member'),
                    updated_at = now()
            RETURNING id INTO v_auth_user_id;
        ELSE
            UPDATE auth.users
            SET email_confirmed_at = COALESCE(email_confirmed_at, now()),
                raw_app_meta_data = jsonb_set(COALESCE(raw_app_meta_data, '{}'::jsonb), '{role}', '"member"'::jsonb, true),
                raw_user_meta_data = COALESCE(raw_user_meta_data, '{}'::jsonb) || jsonb_build_object('full_name', v_member.full_name, 'role', 'member'),
                updated_at = now()
            WHERE id = v_auth_user_id;
        END IF;

        INSERT INTO auth.identities (id, user_id, provider, provider_id, identity_data, created_at, updated_at)
        VALUES (
            gen_random_uuid(),
            v_auth_user_id,
            'email',
            v_member.email,
            jsonb_build_object('sub', v_auth_user_id::text, 'email', v_member.email),
            now(),
            now()
        )
        ON CONFLICT (provider, provider_id) DO UPDATE
            SET identity_data = EXCLUDED.identity_data,
                updated_at = now()
            WHERE auth.identities.user_id = EXCLUDED.user_id;

        -- Re-read canonical identity owner after upsert attempt so we never relink
        -- members away from an existing identity owner in conflict scenarios.
        SELECT i.user_id
        INTO v_auth_user_id
        FROM auth.identities i
        WHERE i.provider = 'email'
          AND i.provider_id = v_member.email
        LIMIT 1;

        INSERT INTO public.profiles (id, full_name, email, role)
        VALUES (v_auth_user_id, v_member.full_name, v_member.email, 'member')
        ON CONFLICT (id) DO UPDATE
            SET full_name = EXCLUDED.full_name,
                email = EXCLUDED.email,
                role = 'member';

        UPDATE public.members
        SET user_id = v_auth_user_id,
            updated_at = now()
        WHERE id = v_member.id
          AND user_id IS NULL;
    END LOOP;
END $$;

-- 3) Final sync for any remaining direct email matches.
UPDATE public.members m
SET user_id = u.id,
    updated_at = now()
FROM auth.users u
WHERE m.user_id IS NULL
  AND m.email IS NOT NULL
  AND lower(m.email) = lower(u.email);

COMMIT;

NOTIFY pgrst, 'reload schema';
