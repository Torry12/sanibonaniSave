-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — SUPABASE ONLINE PRODUCTION REBUILD
-- Version: 1.1 (Cloud Orchestrator - August 2026)
-- ─────────────────────────────────────────────────────────────────────────────

-- 🛡️ SECURITY GUARD: This script is intended for CLOUD projects.
-- It excludes local seed data to maintain production integrity.

-- 1. RESET: Wipes all tables, functions, triggers, and views
\i '../07_RESET_DATABASE.sql'

-- 2. SCHEMA: Definitive table structures and extensions
\i '../01_DATABASE_SCHEMA.sql'

-- 3. FUNCTIONS: Authoritative business logic and triggers
\i '../02_FUNCTIONS_AND_TRIGGERS.sql'

-- 3.1 ADVANCED RPCs: Complex payment processing v4
\i '../18_DEPLOY_PROCESS_COMPLEX_PAYMENT_V4.sql'

-- 4. SECURITY: Global RLS policies and storage rules
\i '../03_SECURITY_AND_RLS.sql'

-- 5. ADMIN SETUP: Platform administrator bootstrap
\i '../04_PLATFORM_ADMIN_SETUP.sql'

-- 6. VIEWS: Analytics and reporting snapshots
\i '../05_PERFORMANCE_VIEWS.sql'

-- 7. ONLINE SPECIFIC: Production only overrides
\i '01_ONLINE_SPECIFIC_SETUP.sql'

-- 8. STORAGE: BRL policies and bucket provisioning
\i '../30_STORAGE_RLS.sql'

-- ─────────────────────────────────────────────────────────────────────────────
-- SUCCESS: Online Production Environment Initialized.
-- ─────────────────────────────────────────────────────────────────────────────
