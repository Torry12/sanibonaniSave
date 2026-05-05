-- ═══════════════════════════════════════════════════════════════════════════════
-- SanibonaniSave — CREATE / UPSERT ALL PLATFORM LOGIN CREDENTIALS
-- Version: 1.1 (May 4, 2026)
-- ═══════════════════════════════════════════════════════════════════════════════
-- Run this in the Supabase SQL Editor (with admin / service_role access).
-- The script is IDEMPOTENT — safe to re-run; it will UPDATE existing users
-- rather than failing on duplicate email.
-- ═══════════════════════════════════════════════════════════════════════════════

-- ─── Step 1: Create helper function (standalone, outside DO block) ─────────────
CREATE OR REPLACE FUNCTION _tmp_upsert_user(
    p_id        uuid,
    p_email     text,
    p_password  text,
    p_full_name text,
    p_role      text
) RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_exists boolean;
    v_hashed text;
    v_existing_id uuid;
    v_user_id uuid;
BEGIN
    SELECT id
    INTO v_existing_id
    FROM auth.users
    WHERE id = p_id OR lower(email) = lower(p_email)
    ORDER BY CASE WHEN id = p_id THEN 0 ELSE 1 END
    LIMIT 1;

    v_exists := v_existing_id IS NOT NULL;
    v_user_id := COALESCE(v_existing_id, p_id);
    v_hashed := extensions.crypt(p_password, extensions.gen_salt('bf'));

    IF v_exists THEN
        -- Update password + metadata so credentials stay in sync
        UPDATE auth.users
        SET
            encrypted_password  = v_hashed,
            raw_user_meta_data  = jsonb_build_object('full_name', p_full_name, 'role', p_role),
            email_confirmed_at  = COALESCE(email_confirmed_at, NOW()),
            updated_at          = NOW()
        WHERE id = v_user_id;
        RAISE NOTICE 'UPDATED user: % (%)', p_email, p_role;
    ELSE
        INSERT INTO auth.users (
            id, aud, role, email, encrypted_password,
            email_confirmed_at, raw_app_meta_data, raw_user_meta_data,
            created_at, updated_at, confirmation_token
        ) VALUES (
            p_id,
            'authenticated',
            'authenticated',
            p_email,
            v_hashed,
            NOW(),
            '{"provider":"email","providers":["email"]}'::jsonb,
            jsonb_build_object('full_name', p_full_name, 'role', p_role),
            NOW(),
            NOW(),
            ''
        );
        RAISE NOTICE 'CREATED user: % (%)', p_email, p_role;
    END IF;

    -- Ensure identity record exists (required for email/password login)
    INSERT INTO auth.identities (
        id,
        user_id,
        identity_data,
        provider,
        provider_id,
        last_sign_in_at,
        created_at,
        updated_at
    )
    SELECT
        v_user_id,
        v_user_id,
        jsonb_build_object(
            'sub', v_user_id::text,
            'email', lower(p_email),
            'email_verified', true
        ),
        'email',
        lower(p_email),
        NOW(),
        NOW(),
        NOW()
    WHERE NOT EXISTS (
        SELECT 1
        FROM auth.identities
        WHERE (user_id = v_user_id AND provider = 'email')
           OR (provider = 'email' AND provider_id = lower(p_email))
    );

    -- Ensure public.profiles row exists, with correct role
    INSERT INTO public.profiles (id, email, full_name, role, created_at, updated_at)
    VALUES (v_user_id, p_email, p_full_name, p_role, NOW(), NOW())
    ON CONFLICT (id) DO UPDATE
        SET role       = EXCLUDED.role,
            full_name  = EXCLUDED.full_name,
            updated_at = NOW();
END;
$$;


-- ─── Step 2: Call the helper for each user ─────────────────────────────────────
DO $$
BEGIN
    RAISE NOTICE '═══════════════════════════════════════════════════════';
    RAISE NOTICE ' SanibonaniSave — Creating / Updating Platform Credentials';
    RAISE NOTICE '═══════════════════════════════════════════════════════';

    -- Platform Admin
    PERFORM _tmp_upsert_user(
        '1b8aca84-c136-4c1b-b024-902584ae80d8'::uuid,
        'torrymsimango@gmail.com', 'torry123M', 'Torry Msimango', 'platform_admin'
    );

    -- Group Admin 1
    PERFORM _tmp_upsert_user(
        'a1a1a1a1-a1a1-a1a1-a1a1-a1a1a1a1a1a1'::uuid,
        'admin1@test.com', 'password123', 'John Admin', 'group_admin'
    );

    -- Group Admin 2
    PERFORM _tmp_upsert_user(
        'a2a2a2a2-a2a2-a2a2-a2a2-a2a2a2a2a2a2'::uuid,
        'admin2@test.com', 'password123', 'Jane Admin', 'group_admin'
    );

    -- Member 1
    PERFORM _tmp_upsert_user(
        'b1b1b1b1-b1b1-b1b1-b1b1-b1b1b1b1b1b1'::uuid,
        'member1@test.com', 'password123', 'Sipho Nkosi', 'member'
    );

    -- Member 2
    PERFORM _tmp_upsert_user(
        'b2b2b2b2-b2b2-b2b2-b2b2-b2b2b2b2b2b2'::uuid,
        'member2@test.com', 'password123', 'Thandi Dlamini', 'member'
    );

    -- Member 3
    PERFORM _tmp_upsert_user(
        'b3b3b3b3-b3b3-b3b3-b3b3-b3b3b3b3b3b3'::uuid,
        'member3@test.com', 'password123', 'Bongani Moyo', 'member'
    );

    -- Member 4
    PERFORM _tmp_upsert_user(
        'b4b4b4b4-b4b4-b4b4-b4b4-b4b4b4b4b4b4'::uuid,
        'member4@test.com', 'password123', 'Nomvula Zulu', 'member'
    );

    RAISE NOTICE '───────────────────────────────────────────────────────';
    RAISE NOTICE ' Done. Refreshing PostgREST schema cache...';
    RAISE NOTICE '───────────────────────────────────────────────────────';
END $$;


-- ─── Step 3: Drop the temporary helper function ────────────────────────────────
DROP FUNCTION IF EXISTS _tmp_upsert_user(uuid, text, text, text, text);


-- Reload PostgREST schema cache
NOTIFY pgrst, 'reload schema';


-- ═══════════════════════════════════════════════════════════════════════════════
-- Verification: confirm all platform users exist with correct roles
-- ═══════════════════════════════════════════════════════════════════════════════
SELECT
    u.email,
    u.raw_user_meta_data ->> 'role'                                         AS auth_role,
    p.role                                                                   AS profile_role,
    CASE
        WHEN u.email_confirmed_at IS NOT NULL THEN 'CONFIRMED'
        ELSE 'UNCONFIRMED'
    END                                                                      AS email_status,
    CASE
        WHEN (u.raw_user_meta_data ->> 'role') = p.role THEN '✅ OK'
        ELSE '❌ MISMATCH'
    END                                                                      AS consistency
FROM auth.users u
LEFT JOIN public.profiles p ON p.id = u.id
WHERE u.email IN (
    'torrymsimango@gmail.com',
    'admin1@test.com',
    'admin2@test.com',
    'member1@test.com',
    'member2@test.com',
    'member3@test.com',
    'member4@test.com'
)
ORDER BY
    CASE u.email
        WHEN 'torrymsimango@gmail.com' THEN 1
        WHEN 'admin1@test.com'         THEN 2
        WHEN 'admin2@test.com'         THEN 3
        ELSE 4
    END;
