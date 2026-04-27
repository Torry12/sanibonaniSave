-- SanibonaniSave RBAC v3 verification script
-- Run after auth_layers_v3.sql in Supabase SQL Editor.

-- 1) Helper functions exist
SELECT routine_name
FROM information_schema.routines
WHERE routine_schema = 'public'
  AND routine_name IN ('is_platform_admin', 'is_group_admin_for', 'is_group_member_for')
ORDER BY routine_name;

-- 2) RLS enabled status
SELECT tablename, rowsecurity AS rls_enabled
FROM pg_tables t
JOIN pg_class c ON c.relname = t.tablename
WHERE t.schemaname = 'public'
  AND t.tablename IN ('profiles', 'groups', 'members', 'contributions', 'payouts', 'platform_fees')
ORDER BY t.tablename;

-- 3) Required AuthV3 policies exist
SELECT tablename, policyname, cmd
FROM pg_policies
WHERE schemaname = 'public'
  AND policyname LIKE 'AuthV3 %'
ORDER BY tablename, policyname;

-- 4) Account-role alignment quick check
SELECT
    u.email,
    u.raw_user_meta_data ->> 'role' AS auth_role,
    p.role AS profile_role,
    CASE
        WHEN (u.raw_user_meta_data ->> 'role') = p.role THEN 'OK'
        ELSE 'MISMATCH'
    END AS status
FROM auth.users u
LEFT JOIN public.profiles p ON p.id = u.id
WHERE u.email IN ('torryymsimango@gmail.com', 'admin1@test.com', 'member1@test.com')
ORDER BY u.email;

-- 5) Simulated RLS visibility checks for each layer
DO $$
DECLARE
    v_platform_uid UUID;
    v_group_admin_uid UUID;
    v_member_uid UUID;
    v_profile_visible INT;
    v_groups_visible INT;
    v_members_visible INT;
BEGIN
    SELECT id INTO v_platform_uid FROM auth.users WHERE email = 'torryymsimango@gmail.com' LIMIT 1;
    SELECT id INTO v_group_admin_uid FROM auth.users WHERE email = 'admin1@test.com' LIMIT 1;
    SELECT id INTO v_member_uid FROM auth.users WHERE email = 'member1@test.com' LIMIT 1;

    IF v_platform_uid IS NOT NULL THEN
        PERFORM set_config('request.jwt.claim.role', 'authenticated', true);
        PERFORM set_config('request.jwt.claim.sub', v_platform_uid::text, true);

        SELECT COUNT(*) INTO v_profile_visible FROM public.profiles;
        SELECT COUNT(*) INTO v_groups_visible FROM public.groups;
        SELECT COUNT(*) INTO v_members_visible FROM public.members;

        RAISE NOTICE 'PLATFORM_ADMIN visibility profiles=% groups=% members=%', v_profile_visible, v_groups_visible, v_members_visible;
    ELSE
        RAISE NOTICE 'PLATFORM_ADMIN test skipped (user not found)';
    END IF;

    IF v_group_admin_uid IS NOT NULL THEN
        PERFORM set_config('request.jwt.claim.role', 'authenticated', true);
        PERFORM set_config('request.jwt.claim.sub', v_group_admin_uid::text, true);

        SELECT COUNT(*) INTO v_groups_visible FROM public.groups;
        SELECT COUNT(*) INTO v_members_visible FROM public.members;

        RAISE NOTICE 'GROUP_ADMIN visibility groups=% members=%', v_groups_visible, v_members_visible;
    ELSE
        RAISE NOTICE 'GROUP_ADMIN test skipped (user not found)';
    END IF;

    IF v_member_uid IS NOT NULL THEN
        PERFORM set_config('request.jwt.claim.role', 'authenticated', true);
        PERFORM set_config('request.jwt.claim.sub', v_member_uid::text, true);

        SELECT COUNT(*) INTO v_groups_visible FROM public.groups;
        SELECT COUNT(*) INTO v_members_visible FROM public.members;

        RAISE NOTICE 'MEMBER visibility groups=% members=%', v_groups_visible, v_members_visible;
    ELSE
        RAISE NOTICE 'MEMBER test skipped (user not found)';
    END IF;
END $$;

