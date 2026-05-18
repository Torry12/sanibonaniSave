-- CONSOLIDATED APPLY SCRIPT
-- Purpose: Apply all schema, migrations, and seed SQL files in a recommended order.
-- Usage (from repo root, with psql available):
--   psql "$PG_CONN" -f supabase/CONSOLIDATED_APPLY.sql
-- where $PG_CONN is e.g. "host=localhost port=5432 dbname=postgres user=postgres password=postgres"
--
-- NOTE: This script uses psql's \i directive to include individual files. It does NOT modify the original files.
-- Make a DB backup before running. Review the files listed below before executing in production.

\echo '--- Start consolidated apply ---'

-- 1) Base consolidated schema
\echo '\n-- Applying: supabase/01_DATABASE_SCHEMA.sql'
\i 'supabase/01_DATABASE_SCHEMA.sql'

-- 2) Migrations (supabase/migrations) - ordered by filename
\echo '\n-- Applying migrations...'
\i 'supabase/migrations/20260514000100_initial_schema.sql'
\i 'supabase/migrations/20260514000200_security_and_rls.sql'
\i 'supabase/migrations/20260514000300_platform_admin_setup.sql'
\i 'supabase/migrations/20260514000400_migrations_and_updates.sql'
\i 'supabase/migrations/20260514000500_platform_admin_auth_alignment.sql'
\i 'supabase/migrations/20260514000600_platform_admin_rls_hotfix.sql'
\i 'supabase/migrations/20260514000700_add_rosca_rotation_method.sql'
\i 'supabase/migrations/20260514000800_architecture_model_schema_templates.sql'
\i 'supabase/migrations/20260514000900_align_validation_constraints_with_app.sql'
\i 'supabase/migrations/20260515000000_consolidated_rls_alignment.sql'
\i 'supabase/migrations/20260515000100_atomic_balance_updates.sql'
\i 'supabase/migrations/20260515000200_disbursement_rpcs.sql'
\i 'supabase/migrations/20260515000200_group_voting_and_multi_admin.sql'
\i 'supabase/migrations/20260518000100_backfill_group_ledger_from_payments.sql'
\i 'supabase/migrations/20260518000200_recompute_group_balances_from_ledger.sql'

-- 3) Top-level supplemental migrations and fixes (supabase/*.sql) - numeric order
\echo '\n-- Applying top-level supplemental scripts...'
\i 'supabase/02_SECURITY_AND_RLS.sql'
\i 'supabase/03_PLATFORM_ADMIN_SETUP.sql'
\i 'supabase/04_MIGRATIONS_AND_UPDATES.sql'
\i 'supabase/04_PLATFORM_ADMIN_AUTH_ALIGNMENT.sql'
\i 'supabase/05_SEED_FRESH_START.sql'
\i 'supabase/06_UTILITY_QUERIES.sql'
\i 'supabase/07_MISSING_TABLES_AND_COLUMNS.sql'
\i 'supabase/08_SMALL_STRESS_TEST_SEED.sql'
\i 'supabase/09_COMPREHENSIVE_TEST_SEED.sql'
\i 'supabase/10_SCALED_TEST_SEED_300.sql'
\i 'supabase/11_SEED_TEST_DATA_10_GROUPS_100_MEMBERS.sql'
\i 'supabase/12_VERIFY_TEST_DATA_10_GROUPS_100_MEMBERS.sql'
\i 'supabase/13_CREATE_TEST_LOGIN_PROFILES.sql'
\i 'supabase/14_VERIFY_TEST_LOGIN_PROFILES.sql'
\i 'supabase/15_CLEANUP_TEST_LOGIN_PROFILES.sql'
\i 'supabase/16_PLATFORM_MEMBER_BEHAVIOR_INSIGHTS.sql'
\i 'supabase/17_SEED_DEBUG_LOGIC_SCENARIOS.sql'
\i 'supabase/18_VERIFY_DEBUG_LOGIC_SCENARIOS.sql'
\i 'supabase/19_CLEANUP_DEBUG_LOGIC_SCENARIOS.sql'
\i 'supabase/20_RUN_DEBUG_LOGIC_SEED_AND_VERIFY.sql'
\i 'supabase/21_RUN_DEBUG_LOGIC_CLEANUP.sql'
\i 'supabase/22_SEED_TEST_DATA_10_GROUPS_100_MEMBERS_SAFE.sql'
\i 'supabase/23_VERIFY_TEST_DATA_10_GROUPS_100_MEMBERS_SAFE.sql'
\i 'supabase/24_PLATFORM_ADMIN_RLS_HOTFIX.sql'
\i 'supabase/25_SEED_FULL_APP_E2E.sql'
\i 'supabase/26_VERIFY_FULL_APP_E2E.sql'
\i 'supabase/27_ADD_ROSCA_ROTATION_METHOD.sql'
\i 'supabase/28_ARCHITECTURE_MODEL_SCHEMA_TEMPLATES.sql'
\i 'supabase/29_ALIGN_VALIDATION_CONSTRAINTS_WITH_APP.sql'
\i 'supabase/30_CONSOLIDATED_RLS_ALIGNMENT.sql'
\i 'supabase/31_ATOMIC_BALANCE_UPDATES.sql'
\i 'supabase/32_GROUP_VOTING_AND_MULTI_ADMIN.sql'
\i 'supabase/33_REMOVE_YOCO_FROM_DISBURSEMENTS.sql'
\i 'supabase/33_REBUILD_DATABASE_WITH_TEST_SEED.sql'
\i 'supabase/35_FULL_SYSTEM_REBUILD.sql'
\i 'supabase/36_DASHBOARD_FULL_REBUILD.sql'
\i 'supabase/37_DASHBOARD_SEED_DATA.sql'
\i 'supabase/38_DASHBOARD_COMPREHENSIVE_SEED.sql'

\echo '\n--- Consolidated apply complete ---'

