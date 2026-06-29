-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — CONSOLIDATED FULL DATABASE SETUP
-- Version: 7.0 (Canonical Entry Point - June 2026)
-- ─────────────────────────────────────────────────────────────────────────────

-- This script performs a complete database setup using the canonical master files.
-- It expects a clean database or will use 'CREATE TABLE IF NOT EXISTS' where defined.

\echo '--- [1/5] Initializing Master Schema ---'
\i 'supabase/SUPABASE_MASTER_SCHEMA.sql'

\echo '--- [2/5] Initializing Master Functions & Triggers ---'
\i 'supabase/SUPABASE_MASTER_FUNCTIONS.sql'

\echo '--- [3/5] Applying Security and RLS Policies ---'
\i 'supabase/02_SECURITY_AND_RLS.sql'

\echo '--- [4/5] Applying Performance Optimizations ---'
\i 'supabase/39_ENGINEERING_PERFORMANCE_OPTIMIZATIONS.sql'

\echo '--- [5/5] Initializing Safe Test Data (Seed) ---'
\i 'supabase/seeds/SAFE_SEED.sql'

\echo '─────────────────────────────────────────────────────────────────────────────'
\echo 'SUCCESS: Full Database Setup Completed.'
\echo '─────────────────────────────────────────────────────────────────────────────'
