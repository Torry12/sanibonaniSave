-- SanibonaniSave — Debug: Why data is not visible in the app
-- Run these in Supabase SQL Editor.

-- 1) Check that data exists
SELECT COUNT(*)::int AS groups FROM public.groups;
SELECT COUNT(*)::int AS members FROM public.members;

-- 2) Check GRANTS (permission denied vs RLS)
-- If these are false, the Supabase API will throw: "permission denied for table ..."
SELECT
  has_schema_privilege('anon', 'public', 'usage')        AS anon_schema_usage,
  has_table_privilege('anon', 'public.groups', 'select') AS anon_groups_select,
  has_table_privilege('anon', 'public.members', 'select') AS anon_members_select,
  has_table_privilege('anon', 'public.platform_settings', 'select') AS anon_platform_settings_select,
  has_table_privilege('authenticated', 'public.groups', 'select') AS auth_groups_select,
  has_table_privilege('authenticated', 'public.members', 'select') AS auth_members_select;

-- 3) Check if RLS is enabled (RLS ON + FORCE?)
SELECT
  schemaname,
  tablename,
  rowsecurity AS rls_enabled,
  (SELECT relforcerowsecurity FROM pg_class c WHERE c.relname = tablename LIMIT 1) AS rls_forced
FROM pg_tables
WHERE schemaname = 'public'
  AND tablename IN (
    'groups','members','contributions','payments','notifications','member_documents','payouts','platform_fees','group_actuarial_metrics','profiles'
  )
ORDER BY tablename;

-- 4) List policies (confirm you ran rls_policies.sql after schema reset)
SELECT schemaname, tablename, policyname, roles, cmd, qual
FROM pg_policies
WHERE schemaname = 'public'
  AND tablename IN ('groups','group_actuarial_metrics')
ORDER BY tablename, policyname;

-- 5) Simulate anon visibility (should return public groups)
-- Supabase uses the anon key -> role 'anon' when NOT logged in.
SET LOCAL ROLE anon;
SELECT id, name, is_public, fee_status
FROM public.groups
WHERE is_public = true
ORDER BY created_at DESC
LIMIT 20;

-- 6) Reset role
RESET ROLE;

