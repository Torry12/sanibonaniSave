-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — 07. MASTER REBUILD SCRIPT
-- Version: 4.5 (Canonical Implementation - June 2026)
-- ─────────────────────────────────────────────────────────────────────────────
-- This script orchestrates the full rebuild of the database schema and
-- initializes it with the standard seed data set.

-- 1. Tables and Indexes (Master)
\i '../SUPABASE_MASTER_SCHEMA.sql'

-- 2. Functions and Triggers (Master)
\i '../SUPABASE_MASTER_FUNCTIONS.sql'

-- 3. Performance Views (Modular)
\i '03_PERFORMANCE_VIEWS.sql'

-- 4. Security and RLS (Canonical)
\i '../02_SECURITY_AND_RLS.sql'

-- 5. Seed Data (Standard)
\i '../seeds/SAFE_SEED.sql'

-- ─────────────────────────────────────────────────────────────────────────────
-- SUCCESS: Database rebuild and seeding complete.
-- ─────────────────────────────────────────────────────────────────────────────
