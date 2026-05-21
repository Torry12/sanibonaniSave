-- CONSOLIDATED_FULL.sql
-- Apply schema, migrations, and all supplemental scripts and seeds (FULL rebuild). Use with caution.
\echo '--- Start consolidated full apply ---'

\i 'supabase/CONSOLIDATED_SCHEMA_ONLY.sql'

-- 3) Engineering performance optimizations (atomic ops, analytics, error logging)
\echo '\n-- Applying: supabase/39_ENGINEERING_PERFORMANCE_OPTIMIZATIONS.sql'
\i 'supabase/39_ENGINEERING_PERFORMANCE_OPTIMIZATIONS.sql'

-- 4) Architecture model schema templates (event-driven, risk, social credit, etc.)
\echo '\n-- Applying: supabase/28_ARCHITECTURE_MODEL_SCHEMA_TEMPLATES.sql'
\i 'supabase/28_ARCHITECTURE_MODEL_SCHEMA_TEMPLATES.sql'

-- 5) Top-level supplemental migrations and fixes (supabase/*.sql) - numeric order
\echo '\n-- Applying top-level supplemental scripts and seeds...'
\i 'supabase/02_SECURITY_AND_RLS.sql'
\i 'supabase/03_PLATFORM_ADMIN_SETUP.sql'
\i 'supabase/04_MIGRATIONS_AND_UPDATES.sql'
\i 'supabase/04_PLATFORM_ADMIN_AUTH_ALIGNMENT.sql'
-- Seeds: use consolidated canonical seed files (SAFE and E2E). SAFE is safe for most tests; E2E is heavy.
\i 'supabase/seeds/SAFE_SEED.sql'
\i 'supabase/seeds/E2E_SEED.sql'

\echo '\n--- Consolidated full apply complete ---'
