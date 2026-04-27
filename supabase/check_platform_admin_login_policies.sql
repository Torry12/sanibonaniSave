-- Platform Admin Login/Access Policy Audit
-- Run in Supabase SQL Editor.
-- This script checks whether DB grants/RLS/policies are causing platform admin access issues.

-- 0) Target account
WITH target AS (
    SELECT
        u.id,
        u.email,
        u.email_confirmed_at,
        u.raw_user_meta_data ->> 'role' AS auth_role,
        p.role AS profile_role,
        p.updated_at AS profile_updated_at
    FROM auth.users u
    LEFT JOIN public.profiles p ON p.id = u.id
    WHERE u.email = 'torryymsimango@gmail.com'
)
SELECT
    id,
    email,
    email_confirmed_at,
    auth_role,
    profile_role,
    CASE
        WHEN id IS NULL THEN 'MISSING_AUTH_USER'
        WHEN email_confirmed_at IS NULL THEN 'EMAIL_NOT_CONFIRMED'
        WHEN auth_role <> 'platform_admin' THEN 'AUTH_ROLE_MISMATCH'
        WHEN profile_role IS NULL THEN 'MISSING_PROFILE_ROW'
        WHEN profile_role <> 'platform_admin' THEN 'PROFILE_ROLE_MISMATCH'
        ELSE 'OK'
    END AS auth_profile_status,
    profile_updated_at
FROM target;

-- 1) Verify grants needed by PostgREST/authenticated role
SELECT
    has_schema_privilege('authenticated', 'public', 'usage') AS auth_schema_usage,
    has_table_privilege('authenticated', 'public.profiles', 'select') AS auth_profiles_select,
    has_table_privilege('authenticated', 'public.groups', 'select') AS auth_groups_select,
    has_table_privilege('authenticated', 'public.payouts', 'select') AS auth_payouts_select,
    has_table_privilege('authenticated', 'public.platform_fees', 'select') AS auth_platform_fees_select;

-- 2) RLS status on key tables
SELECT
    schemaname,
    tablename,
    rowsecurity AS rls_enabled,
    (SELECT c.relforcerowsecurity FROM pg_class c WHERE c.relname = t.tablename LIMIT 1) AS rls_forced
FROM pg_tables t
WHERE schemaname = 'public'
  AND tablename IN ('profiles', 'groups', 'payouts', 'platform_fees', 'group_actuarial_metrics')
ORDER BY tablename;

-- 3) Check required policies exist
SELECT
    tablename,
    policyname,
    roles,
    cmd
FROM pg_policies
WHERE schemaname = 'public'
  AND (
      (tablename = 'profiles' AND policyname IN (
          'View Own Profile',
          'Allow Profile Insert',
          'Allow Profile Update'
      ))
      OR (tablename = 'groups' AND policyname = 'Platform Admin All Access')
      OR (tablename = 'payouts' AND policyname = 'Platform Admin Payout Access')
      OR (tablename = 'platform_fees' AND policyname = 'Platform Admin Platform Fee Access')
      OR (tablename = 'group_actuarial_metrics' AND policyname = 'Platform Admin Actuarial Access')
  )
ORDER BY tablename, policyname;

-- 4) Simulate authenticated request context for platform admin user
-- This validates auth.uid()-based RLS behavior used by app queries.
DO $$
DECLARE
    v_uid uuid;
    v_profile_count int := 0;
    v_platform_admin_gate boolean := false;
BEGIN
    SELECT id INTO v_uid FROM auth.users WHERE email = 'torryymsimango@gmail.com' LIMIT 1;

    IF v_uid IS NULL THEN
        RAISE NOTICE 'SKIP: auth user not found';
        RETURN;
    END IF;

    PERFORM set_config('request.jwt.claim.role', 'authenticated', true);
    PERFORM set_config('request.jwt.claim.sub', v_uid::text, true);

    SELECT COUNT(*) INTO v_profile_count
    FROM public.profiles
    WHERE id = auth.uid();

    SELECT EXISTS (
        SELECT 1
        FROM public.profiles
        WHERE id = auth.uid() AND role = 'platform_admin'
    ) INTO v_platform_admin_gate;

    RAISE NOTICE 'auth.uid()=% profile_row_visible=% platform_admin_gate=%', auth.uid(), v_profile_count, v_platform_admin_gate;
END $$;

-- 5) Quick diagnosis summary
WITH checks AS (
    SELECT
        u.id IS NOT NULL AS has_auth_user,
        u.email_confirmed_at IS NOT NULL AS email_confirmed,
        (u.raw_user_meta_data ->> 'role') = 'platform_admin' AS auth_role_ok,
        p.id IS NOT NULL AS has_profile,
        p.role = 'platform_admin' AS profile_role_ok
    FROM auth.users u
    LEFT JOIN public.profiles p ON p.id = u.id
    WHERE u.email = 'torryymsimango@gmail.com'
)
SELECT
    has_auth_user,
    email_confirmed,
    auth_role_ok,
    has_profile,
    profile_role_ok,
    CASE
        WHEN NOT has_auth_user THEN 'Create auth user first.'
        WHEN NOT email_confirmed THEN 'Confirm email or set email_confirmed_at.'
        WHEN NOT auth_role_ok OR NOT has_profile OR NOT profile_role_ok THEN 'Run create_platform_admin.sql then verify_platform_admin.sql.'
        ELSE 'Account alignment is OK. If app still routes wrong, inspect client role resolution/nav.'
    END AS recommendation
FROM checks;

