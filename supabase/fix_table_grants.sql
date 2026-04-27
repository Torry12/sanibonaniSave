-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — QUICK FIX: Table GRANTS for PostgREST ("permission denied")
-- Version: 1.0 (April 19, 2026)
--
-- Purpose:
-- If the Android app shows 0 groups / 0 members and logs contain:
--   "permission denied for table groups"
-- then schema/table GRANTS for anon/authenticated are missing.
--
-- Run this script in the Supabase SQL Editor.
-- Safe to re-run.
-- ─────────────────────────────────────────────────────────────────────────────

-- 1) Schema access
GRANT USAGE ON SCHEMA public TO anon, authenticated, service_role;

-- 2) Table / sequence privileges
GRANT ALL ON ALL TABLES IN SCHEMA public TO postgres, service_role;
GRANT ALL ON ALL SEQUENCES IN SCHEMA public TO postgres, service_role;

-- NOTE: RLS will still control row access.
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO anon, authenticated;
GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA public TO anon, authenticated;

-- 3) Default privileges for future tables/sequences
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO anon, authenticated;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO anon, authenticated;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO service_role;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO service_role;

-- 4) Quick verification
SELECT
  has_schema_privilege('anon', 'public', 'usage') AS anon_schema_usage,
  has_table_privilege('anon', 'public.groups', 'select') AS anon_groups_select,
  has_table_privilege('authenticated', 'public.groups', 'select') AS auth_groups_select;

