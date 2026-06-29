-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — DATA VISIBILITY & RLS DIAGNOSTIC
-- Version: 1.0 (June 2026)
-- ─────────────────────────────────────────────────────────────────────────────

-- 1. Identity Context
SELECT
    auth.uid() as current_user_id,
    auth.jwt() ->> 'email' as current_user_email,
    auth.jwt() -> 'app_metadata' ->> 'role' as app_metadata_role,
    public.is_platform_admin() as is_identified_as_platform_admin;

-- 2. Profile Verification
SELECT id, email, role, full_name
FROM public.profiles
WHERE id = auth.uid();

-- 3. Row Counts (Network-wide Visibility Check)
SELECT 'Groups' as "Table", count(*) as "Row Count" FROM public.groups
UNION ALL SELECT 'Members', count(*) FROM public.members
UNION ALL SELECT 'Ledger Entries (Group)', count(*) FROM public.group_ledger
UNION ALL SELECT 'Ledger Entries (Platform)', count(*) FROM public.platform_ledger
UNION ALL SELECT 'Audit Logs', count(*) FROM public.audit_logs;

-- 4. RLS Policy Status
SELECT
    schemaname,
    tablename,
    policyname,
    permissive,
    roles,
    cmd,
    qual
FROM pg_policies
WHERE schemaname = 'public'
  AND tablename = 'groups';

-- 5. RPC Connectivity Test
-- Attempt to fetch summary stats view (supports RLS)
SELECT * FROM public.platform_summary_stats;
