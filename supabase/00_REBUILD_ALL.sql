-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — SUPABASE COMPLETE RESET AND REBUILD
-- Version: 5.0 (Consolidated - June 2026)
-- ─────────────────────────────────────────────────────────────────────────────
-- This script orchestrates the full rebuild of the database from a clean state.
-- Usage: psql -f 00_REBUILD_ALL.sql

-- 07. RESET: Destroys all tables in the public schema
\i '07_RESET_DATABASE.sql'

-- 01. SCHEMA: Tables, Extensions, and Core Indexes
\i '01_DATABASE_SCHEMA.sql'

-- 02. FUNCTIONS: Business logic, Triggers, and RPCs
\i '02_FUNCTIONS_AND_TRIGGERS.sql'

-- 03. SECURITY: RLS Policies and Permissions
\i '03_SECURITY_AND_RLS.sql'

-- 04. ADMIN SETUP: Platform Administrator Bootstrapping
\i '04_PLATFORM_ADMIN_SETUP.sql'

-- 05. VIEWS: Analytics and Performance Dashboards
\i '05_PERFORMANCE_VIEWS.sql'

-- 06. SEED DATA: (Optional) Large test dataset
\i '06_SEED_DATA.sql'

-- ─────────────────────────────────────────────────────────────────────────────
-- SUCCESS: Database rebuild complete.
-- ─────────────────────────────────────────────────────────────────────────────
