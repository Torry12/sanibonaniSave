-- 06_rls_debug_fix.sql
-- Purpose: Diagnostic queries and an (optional) safe, reversible temporary RLS policy set
-- to help determine why the app (authenticated users) cannot see rows even though the
-- database is populated. Run only on a development or staging database. DO NOT run
-- against production without review.
--
-- Usage:
-- 1) Inspect diagnostics (run the SELECTs in the DIAGNOSTICS section).
-- 2) If diagnostics indicate RLS blocking (e.g., members.user_id not matching profiles.id),
--    either fix the data or enable the DEBUG policies below and re-test the app.
-- 3) After verification, remove the DEBUG policies by running the REVERT section.
--
-- IMPORTANT: The DEBUG policies grant broad read access to authenticated users. Only
-- enable them temporarily for debugging and remove them immediately after use.

-- =========================
-- DIAGNOSTICS
-- =========================
-- 1) Members that refer to a user_id that has no profile row (common cause of auth mismatch)
SELECT m.id AS member_id, m.group_id, m.user_id
FROM public.members m
LEFT JOIN public.profiles p ON m.user_id = p.id
WHERE p.id IS NULL
LIMIT 200;

-- 2) Quick counts for key tables (how many rows exist)
SELECT 'profiles' AS table_name, COUNT(*) FROM public.profiles UNION ALL
SELECT 'members', COUNT(*) FROM public.members UNION ALL
SELECT 'groups', COUNT(*) FROM public.groups UNION ALL
SELECT 'contributions', COUNT(*) FROM public.contributions UNION ALL
SELECT 'member_documents', COUNT(*) FROM public.member_documents;

-- 3) Sample a few members and their profiles to verify the mapping values and types
SELECT m.id AS member_id, m.user_id AS member_user_id, p.id AS profile_id, p.email, p.role
FROM public.members m
LEFT JOIN public.profiles p ON m.user_id = p.id
ORDER BY m.created_at DESC
LIMIT 50;

-- 4) Show data types for suspicious columns
SELECT table_name, column_name, data_type
FROM information_schema.columns
WHERE (table_name IN ('members','profiles') AND column_name IN ('user_id','id'))
ORDER BY table_name, column_name;

-- 5) Check whether any policies exist for the tables in question (lists policy names)
SELECT schemaname, tablename, policyname
FROM pg_policies
WHERE tablename IN ('groups','members','contributions','member_documents','beneficiaries');

-- =========================
-- TEMPORARY DEBUG: PERMISSIVE READ-ONLY POLICIES
-- =========================
-- Run the commands below to temporarily grant authenticated users SELECT access
-- to core tables while you confirm the app behavior. These policies are SELECT-only
-- and are safe to remove after debugging.
--
-- NOTE: All DROP POLICY IF EXISTS lines are included so this script is re-runnable.
-- To enable debug mode, execute the section below. To revert, run the REVERT section.

-- Enable debug SELECT policies (UNCOMMENT to APPLY)
-- --------------------------------------------------
-- DROP POLICY IF EXISTS "Debug: Groups - Authenticated Read All" ON public.groups;
-- CREATE POLICY "Debug: Groups - Authenticated Read All" ON public.groups FOR SELECT TO authenticated USING (true);
--
-- DROP POLICY IF EXISTS "Debug: Members - Authenticated Read All" ON public.members;
-- CREATE POLICY "Debug: Members - Authenticated Read All" ON public.members FOR SELECT TO authenticated USING (true);
--
-- DROP POLICY IF EXISTS "Debug: Contributions - Authenticated Read All" ON public.contributions;
-- CREATE POLICY "Debug: Contributions - Authenticated Read All" ON public.contributions FOR SELECT TO authenticated USING (true);
--
-- DROP POLICY IF EXISTS "Debug: MemberDocuments - Authenticated Read All" ON public.member_documents;
-- CREATE POLICY "Debug: MemberDocuments - Authenticated Read All" ON public.member_documents FOR SELECT TO authenticated USING (true);
--
-- DROP POLICY IF EXISTS "Debug: Beneficiaries - Authenticated Read All" ON public.beneficiaries;
-- CREATE POLICY "Debug: Beneficiaries - Authenticated Read All" ON public.beneficiaries FOR SELECT TO authenticated USING (true);
--
-- DROP POLICY IF EXISTS "Debug: Loans - Authenticated Read All" ON public.loans;
-- CREATE POLICY "Debug: Loans - Authenticated Read All" ON public.loans FOR SELECT TO authenticated USING (true);
--
-- DROP POLICY IF EXISTS "Debug: Payments - Authenticated Read All" ON public.payments;
-- CREATE POLICY "Debug: Payments - Authenticated Read All" ON public.payments FOR SELECT TO authenticated USING (true);
--
-- After enabling the above, re-run the app and check whether data becomes visible.

-- =========================
-- REVERT: remove debug policies (run after finishing debugging)
-- =========================
-- Uncomment and run the DROP POLICY lines below to remove debug policies.
-- --------------------------------------------------
-- DROP POLICY IF EXISTS "Debug: Groups - Authenticated Read All" ON public.groups;
-- DROP POLICY IF EXISTS "Debug: Members - Authenticated Read All" ON public.members;
-- DROP POLICY IF EXISTS "Debug: Contributions - Authenticated Read All" ON public.contributions;
-- DROP POLICY IF EXISTS "Debug: MemberDocuments - Authenticated Read All" ON public.member_documents;
-- DROP POLICY IF EXISTS "Debug: Beneficiaries - Authenticated Read All" ON public.beneficiaries;
-- DROP POLICY IF EXISTS "Debug: Loans - Authenticated Read All" ON public.loans;
-- DROP POLICY IF EXISTS "Debug: Payments - Authenticated Read All" ON public.payments;

-- =========================
-- OPTIONAL: Data repair guidance (manual steps)
-- =========================
-- If diagnostics show members with user_id that don't match profiles.id, you can:
-- 1) Inspect the intended mapping and correct user_id values to match profile ids.
--    Example (run after careful review):
--    -- Backup first
--    BEGIN; -- DB transaction
--    UPDATE public.members m
--    SET user_id = p.id
--    FROM public.profiles p
--    WHERE m.user_id::text = p.email OR m.user_id::text = p.phone;
--    COMMIT;
--    -- The above is only an example. Do NOT run without confirming the matching logic.

-- =========================
-- End of script
-- =========================

