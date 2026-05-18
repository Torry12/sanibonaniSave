-- E2E_SEED_DRIVER.sql
-- Driver that includes E2E and comprehensive seed scripts (heavy; intended for test environments)
\echo '\n-- Applying e2e seed group (heavy)'
\i 'supabase/25_SEED_FULL_APP_E2E.sql'
\i 'supabase/33_REBUILD_DATABASE_WITH_TEST_SEED.sql'
\i 'supabase/35_FULL_SYSTEM_REBUILD.sql'
\i 'supabase/36_DASHBOARD_FULL_REBUILD.sql'
\i 'supabase/37_DASHBOARD_SEED_DATA.sql'
\i 'supabase/38_DASHBOARD_COMPREHENSIVE_SEED.sql'
\echo '\n-- E2E seeds applied.'

