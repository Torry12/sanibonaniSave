-- 06_rls_debug_fix_safe.sql
-- Safe, defensive RLS diagnostics and temporary debug helpers.
-- This version avoids "relation does not exist" errors by checking table existence
-- before running queries. Intended for development/staging only.
-- Usage:
--   psql -h $HOST -p $PORT -U $USER -d $DB -f 06_rls_debug_fix_safe.sql
-- NOTE: Does not create any permissive debug policies by default. Debug CREATEs are
-- provided but commented; enable them only in a dev environment.

DO $$
DECLARE
    v_cnt bigint;
    rec record;
    tbl text;
BEGIN
    RAISE NOTICE '=== RLS DIAGNOSTICS (safe mode) ===';

    -- 1) existence + row counts for key tables
    FOR tbl IN SELECT unnest(ARRAY['profiles','members','groups','contributions','member_documents','beneficiaries','payments','loans']) LOOP
        IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema='public' AND table_name = tbl) THEN
            EXECUTE format('SELECT count(*) FROM public.%I', tbl) INTO v_cnt;
            RAISE NOTICE 'Table % exists: % rows', tbl, v_cnt;
        ELSE
            RAISE NOTICE 'Table % MISSING', tbl;
        END IF;
    END LOOP;

    -- 2) members without matching profile
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema='public' AND table_name = 'members')
       AND EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema='public' AND table_name = 'profiles') THEN

        EXECUTE 'SELECT count(*) FROM public.members m LEFT JOIN public.profiles p ON m.user_id = p.id WHERE p.id IS NULL' INTO v_cnt;
        RAISE NOTICE 'Members with missing profile join: %', v_cnt;

        RAISE NOTICE 'Sample (up to 20) members with missing profile:';
        FOR rec IN EXECUTE 'SELECT m.id AS member_id, m.group_id, m.user_id FROM public.members m LEFT JOIN public.profiles p ON m.user_id = p.id WHERE p.id IS NULL ORDER BY m.created_at DESC LIMIT 20' LOOP
            RAISE NOTICE 'member_id=% group_id=% user_id=%', rec.member_id, rec.group_id, rec.user_id;
        END LOOP;
    ELSE
        RAISE NOTICE 'Skipping members-vs-profiles diagnostic because members or profiles table is missing.';
    END IF;

    -- 3) sample mapping of members -> profiles
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema='public' AND table_name = 'members')
       AND EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema='public' AND table_name = 'profiles') THEN
        RAISE NOTICE 'Sample (up to 20) member <-> profile rows:';
        FOR rec IN EXECUTE 'SELECT m.id AS member_id, m.user_id AS member_user_id, p.id AS profile_id, p.email, p.role FROM public.members m LEFT JOIN public.profiles p ON m.user_id = p.id ORDER BY m.created_at DESC LIMIT 20' LOOP
            RAISE NOTICE 'member_id=% member_user_id=% profile_id=% email=% role=%', rec.member_id, rec.member_user_id, rec.profile_id, rec.email, rec.role;
        END LOOP;
    END IF;

    -- 4) column data types for suspicious columns
    RAISE NOTICE 'Column types for members.user_id and profiles.id (if present):';
    FOR rec IN SELECT table_name, column_name, data_type FROM information_schema.columns
               WHERE (table_name IN ('members','profiles') AND column_name IN ('user_id','id'))
               ORDER BY table_name, column_name LOOP
        RAISE NOTICE '%.% -> %', rec.table_name, rec.column_name, rec.data_type;
    END LOOP;

    -- 5) list pg_policies for core tables (pg_policies exists in modern Postgres)
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'pg_catalog' AND table_name = 'pg_policies') OR (SELECT COUNT(*) FROM pg_catalog.pg_namespace n JOIN pg_catalog.pg_class c ON n.oid = c.relnamespace WHERE c.relname = 'pg_policies') > 0 THEN
        RAISE NOTICE 'Listing policies for core tables:';
        FOR rec IN SELECT schemaname, tablename, policyname FROM pg_policies WHERE tablename IN ('groups','members','contributions','member_documents','beneficiaries','payments','loans','profiles') LOOP
            RAISE NOTICE 'policy: % on %.%', rec.policyname, rec.schemaname, rec.tablename;
        END LOOP;
    ELSE
        RAISE NOTICE 'pg_policies view not available on this server; skipping policy listing.';
    END IF;

    RAISE NOTICE 'Diagnostics complete.';
END
$$ LANGUAGE plpgsql;

-- =========================
-- TEMPORARY DEBUG: Permissive SELECT policies (DEVELOPMENT ONLY)
-- Uncomment blocks below to enable permissive SELECT policies for authenticated users.
-- Ensure you REVERT them after testing (see REVERT section).
-- =========================

-- Example: enable permissive read on groups
-- DROP POLICY IF EXISTS "Debug: Groups - Authenticated Read All" ON public.groups;
-- CREATE POLICY "Debug: Groups - Authenticated Read All" ON public.groups FOR SELECT TO authenticated USING (true);

-- Example: enable permissive read on members
-- DROP POLICY IF EXISTS "Debug: Members - Authenticated Read All" ON public.members;
-- CREATE POLICY "Debug: Members - Authenticated Read All" ON public.members FOR SELECT TO authenticated USING (true);

-- Example: enable permissive read on contributions
-- DROP POLICY IF EXISTS "Debug: Contributions - Authenticated Read All" ON public.contributions;
-- CREATE POLICY "Debug: Contributions - Authenticated Read All" ON public.contributions FOR SELECT TO authenticated USING (true);

-- Example: enable permissive read on member_documents
-- DROP POLICY IF EXISTS "Debug: MemberDocuments - Authenticated Read All" ON public.member_documents;
-- CREATE POLICY "Debug: MemberDocuments - Authenticated Read All" ON public.member_documents FOR SELECT TO authenticated USING (true);

-- =========================
-- REVERT (remove debug policies) - run after testing
-- =========================
-- DROP POLICY IF EXISTS "Debug: Groups - Authenticated Read All" ON public.groups;
-- DROP POLICY IF EXISTS "Debug: Members - Authenticated Read All" ON public.members;
-- DROP POLICY IF EXISTS "Debug: Contributions - Authenticated Read All" ON public.contributions;
-- DROP POLICY IF EXISTS "Debug: MemberDocuments - Authenticated Read All" ON public.member_documents;

-- End of file

