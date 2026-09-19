-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — ONLINE PRODUCTION REBUILD ORCHESTRATOR
-- ─────────────────────────────────────────────────────────────────────────────
-- This script rebuilds the production/online database.
-- WARNING: This script will DESTROY existing data if 07_RESET is included.

-- Step 1: Core Schema & Logic (Relative paths to parent directory)
\i '../01_DATABASE_SCHEMA.sql'
\i '../02_FUNCTIONS_AND_TRIGGERS.sql'
\i '../03_SECURITY_AND_RLS.sql'
\i '../04_PLATFORM_ADMIN_SETUP.sql'
\i '../05_PERFORMANCE_VIEWS.sql'

-- Step 2: Online Specific Overrides
\i '01_ONLINE_SPECIFIC_SETUP.sql'

-- Note: 06_SEED_DATA is intentionally EXCLUDED from online rebuilds
-- to prevent test data from entering production.

-- ─────────────────────────────────────────────────────────────────────────────
