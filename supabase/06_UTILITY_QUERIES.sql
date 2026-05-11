-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — UTILITY & DEBUGGING QUERIES
-- Version: 1.0 (Consolidated May 2026)
-- ─────────────────────────────────────────────────────────────────────────────

-- 1. Check Table Counts
SELECT 'Groups' as table, count(*) FROM public.groups
UNION ALL SELECT 'Members', count(*) FROM public.members
UNION ALL SELECT 'Contributions', count(*) FROM public.contributions
UNION ALL SELECT 'Audit Logs', count(*) FROM public.audit_logs;

-- 2. Verify Platform Admin
SELECT p.email, p.role, u.last_sign_in_at
FROM public.profiles p
JOIN auth.users u ON u.id = p.id
WHERE p.role = 'platform_admin';

-- 3. Debug RLS Visibility
-- Run as a specific user to test what they can see
-- SET ROLE authenticated;
-- SET auth.uid = '...';
-- SELECT * FROM public.groups;
-- RESET ROLE;

-- 4. Fix Permission Denied (Quick Grant)
GRANT USAGE ON SCHEMA public TO anon, authenticated;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO anon, authenticated;
