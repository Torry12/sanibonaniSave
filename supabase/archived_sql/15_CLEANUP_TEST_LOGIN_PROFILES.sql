-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — CLEANUP TEST LOGIN PROFILES
-- Removes test auth users created by 13_CREATE_TEST_LOGIN_PROFILES.sql
-- Safe ordering: unlink members.user_id before deleting auth.users
-- ─────────────────────────────────────────────────────────────────────────────

DO $$
DECLARE
    v_platform_admin_id UUID;
    v_first_seed_group_id UUID;
BEGIN
    -- Find platform admin to restore group ownership.
    SELECT id INTO v_platform_admin_id
    FROM auth.users
    WHERE email = 'torrymsimango@gmail.com'
    LIMIT 1;

    -- First seeded group (if present) was reassigned to test group admin.
    SELECT id INTO v_first_seed_group_id
    FROM public.groups
    WHERE name LIKE 'SEED-G%'
    ORDER BY created_at ASC
    LIMIT 1;

    IF v_first_seed_group_id IS NOT NULL AND v_platform_admin_id IS NOT NULL THEN
        UPDATE public.groups
        SET admin_user_id = v_platform_admin_id
        WHERE id = v_first_seed_group_id;
    END IF;

    -- Unlink seeded members from auth users to satisfy FK constraints.
    UPDATE public.members
    SET user_id = NULL
    WHERE email LIKE 'seed.member.%@example.com';

    -- Remove identities first for test group admin and seeded member accounts.
    DELETE FROM auth.identities
    WHERE provider = 'email'
      AND (
            provider_id = 'test.groupadmin@example.com'
         OR provider_id LIKE 'seed.member.%@example.com'
      );

    -- Remove test auth users (profiles cascade via FK ON DELETE CASCADE).
    DELETE FROM auth.users
    WHERE email = 'test.groupadmin@example.com'
       OR email LIKE 'seed.member.%@example.com';

    -- Defensive cleanup if any profile rows were left for test users.
    DELETE FROM public.profiles
    WHERE email = 'test.groupadmin@example.com'
       OR email LIKE 'seed.member.%@example.com';

    RAISE NOTICE 'Test login profiles cleaned: group admin + seeded member login accounts removed.';
END $$;

