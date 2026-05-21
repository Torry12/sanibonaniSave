-- -----------------------------------------------------------------------------
-- SanibonaniSave - Rebuild database with full test data
-- Date: 2026-05-17
-- Runner: psql/Supabase CLI SQL runner with relative include support.
--
-- This is intentionally destructive. Run only against a local/dev/test project.
-- It rebuilds the public schema, reapplies app schema/RLS/admin alignment, removes
-- YoCo coupling from disbursements, then loads broad E2E seed data.
--
-- Example:
--   psql "$DATABASE_URL" -f supabase/33_REBUILD_DATABASE_WITH_TEST_SEED.sql
-- -----------------------------------------------------------------------------

-- psql meta-commands removed for Dashboard compatibility.
-- \set ON_ERROR_STOP on

BEGIN;
DROP SCHEMA IF EXISTS public CASCADE;
CREATE SCHEMA public;
GRANT USAGE ON SCHEMA public TO postgres, anon, authenticated, service_role;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO postgres, service_role;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON ROUTINES TO postgres, service_role;
COMMIT;

-- Note: The following files must be run manually or via CLI.
-- The Dashboard does not support the \i command.
-- \i supabase/01_DATABASE_SCHEMA.sql
-- \i supabase/02_SECURITY_AND_RLS.sql
-- \i supabase/03_PLATFORM_ADMIN_SETUP.sql
-- \i supabase/04_PLATFORM_ADMIN_AUTH_ALIGNMENT.sql
-- \i supabase/07_MISSING_TABLES_AND_COLUMNS.sql
-- \i supabase/24_PLATFORM_ADMIN_RLS_HOTFIX.sql
-- \i supabase/27_ADD_ROSCA_ROTATION_METHOD.sql
-- \i supabase/29_ALIGN_VALIDATION_CONSTRAINTS_WITH_APP.sql
-- \i supabase/30_CONSOLIDATED_RLS_ALIGNMENT.sql
-- \i supabase/31_ATOMIC_BALANCE_UPDATES.sql
-- \i supabase/32_GROUP_VOTING_AND_MULTI_ADMIN.sql
-- \i supabase/migrations/20260515000200_disbursement_rpcs.sql
-- \i supabase/33_REMOVE_YOCO_FROM_DISBURSEMENTS.sql
-- \i supabase/25_SEED_FULL_APP_E2E.sql
-- \i supabase/26_VERIFY_FULL_APP_E2E.sql
