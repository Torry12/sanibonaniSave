-- E2E_SEED_DRIVER.sql
-- Canonical driver: applies only the canonical E2E seed. Legacy/test seeds are deprecated.
-- For advanced or dashboard scenarios, see E2E_SEED.sql or legacy seeds (deprecated).
\echo '\n-- Applying canonical E2E seed (E2E_SEED.sql)'
\i 'supabase/seeds/E2E_SEED.sql'
\echo '\n-- E2E seed applied.'
-- Legacy/test seeds below are deprecated and not included by default:
-- \i 'supabase/25_SEED_FULL_APP_E2E.sql'
-- \i 'supabase/33_REBUILD_DATABASE_WITH_TEST_SEED.sql'
-- \i 'supabase/35_FULL_SYSTEM_REBUILD.sql'
-- \i 'supabase/36_DASHBOARD_FULL_REBUILD.sql'
-- \i 'supabase/37_DASHBOARD_SEED_DATA.sql'
-- \i 'supabase/38_DASHBOARD_COMPREHENSIVE_SEED.sql'
