-- CONSOLIDATED_FULL.sql
-- Apply schema, migrations, and all supplemental scripts and seeds (FULL rebuild). Use with caution.
\echo '--- Start consolidated full apply ---'

\i 'supabase/CONSOLIDATED_SCHEMA_ONLY.sql'

-- 3) Top-level supplemental migrations and fixes (supabase/*.sql) - numeric order
\echo '\n-- Applying top-level supplemental scripts and seeds...'
\i 'supabase/02_SECURITY_AND_RLS.sql'
\i 'supabase/03_PLATFORM_ADMIN_SETUP.sql'
\i 'supabase/04_MIGRATIONS_AND_UPDATES.sql'
\i 'supabase/04_PLATFORM_ADMIN_AUTH_ALIGNMENT.sql'
-- Seeds and supplemental scripts are grouped into drivers under supabase/seeds/
\i 'supabase/seeds/SAFE_SEED_DRIVER.sql'
\i 'supabase/seeds/E2E_SEED_DRIVER.sql'

\echo '\n--- Consolidated full apply complete ---'

