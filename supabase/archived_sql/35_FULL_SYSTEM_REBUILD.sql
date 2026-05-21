-- -----------------------------------------------------------------------------
-- SanibonaniSave - MASTER SYSTEM REBUILD & SEED
-- Date: 2026-05-18
--
-- HOW TO RUN:
-- This script uses psql meta-commands (\i) and MUST be run via the CLI:
--
-- 1. Local Supabase:
--    supabase db query --file supabase/35_FULL_SYSTEM_REBUILD.sql
--
-- 2. Direct psql:
--    psql "$DATABASE_URL" -f supabase/35_FULL_SYSTEM_REBUILD.sql
--
-- DO NOT copy-paste into the Supabase Dashboard SQL Editor (it doesn't support \i).
-- -----------------------------------------------------------------------------

-- psql meta-commands removed for Dashboard compatibility.
-- To run this script with dependencies, use the CLI as described above.

BEGIN;
-- 1. Reset Public Schema
DROP SCHEMA IF EXISTS public CASCADE;
CREATE SCHEMA public;
GRANT ALL ON SCHEMA public TO postgres, anon, authenticated, service_role;

-- 2. Core Schema & Logic
-- \i supabase/01_DATABASE_SCHEMA.sql
-- \i supabase/02_SECURITY_AND_RLS.sql
-- \i supabase/03_PLATFORM_ADMIN_SETUP.sql
-- \i supabase/04_PLATFORM_ADMIN_AUTH_ALIGNMENT.sql
-- \i supabase/07_MISSING_TABLES_AND_COLUMNS.sql

-- 3. Business Logic Enhancements
-- \i supabase/27_ADD_ROSCA_ROTATION_METHOD.sql
-- \i supabase/29_ALIGN_VALIDATION_CONSTRAINTS_WITH_APP.sql
-- \i supabase/30_CONSOLIDATED_RLS_ALIGNMENT.sql
-- \i supabase/31_ATOMIC_BALANCE_UPDATES.sql
-- \i supabase/32_GROUP_VOTING_AND_MULTI_ADMIN.sql

-- 4. Disbursement Fixes (Yoco Removal)
-- \i supabase/migrations/20260515000200_disbursement_rpcs.sql
-- \i supabase/33_REMOVE_YOCO_FROM_DISBURSEMENTS.sql

-- 5. Final Schema Tuning (Neutral Defaults)
ALTER TABLE public.contributions ALTER COLUMN payment_method SET DEFAULT 'bank';
ALTER TABLE public.loan_repayments ALTER COLUMN payment_method SET DEFAULT 'bank';

-- 6. Seed Data (Full E2E Scenarios)
-- \i supabase/25_SEED_FULL_APP_E2E.sql
-- \i supabase/26_VERIFY_FULL_APP_E2E.sql

COMMIT;

RAISE NOTICE 'Full system rebuild and seed complete.';
