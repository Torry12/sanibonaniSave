-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — 00. SCHEMA RESET
-- ─────────────────────────────────────────────────────────────────────────────
-- WARNING: This script drops everything in the public schema.
-- This is the recommended first step for a clean rebuild.

-- 1. Drop public schema
DROP SCHEMA IF EXISTS public CASCADE;

-- 2. Recreate public schema
CREATE SCHEMA public;

-- 3. Grant standard usage
GRANT USAGE ON SCHEMA public TO postgres, anon, authenticated, service_role;

-- 4. Enable required extensions
CREATE EXTENSION IF NOT EXISTS "pgcrypto" WITH SCHEMA public;
CREATE EXTENSION IF NOT EXISTS "pg_trgm" WITH SCHEMA public;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp" WITH SCHEMA public;

-- 5. Standard Search Path
SET search_path TO public;
