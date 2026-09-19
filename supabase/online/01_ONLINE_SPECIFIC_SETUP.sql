-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — ONLINE DATABASE SPECIFIC CONFIGURATION
-- ─────────────────────────────────────────────────────────────────────────────
-- This script contains logic that should ONLY run on the cloud/production instance.

-- 1. SSL Enforcement (Recommended for production)
-- Note: Supabase Cloud usually handles this at the gateway level.

-- 2. Production Roles and Grants
-- Ensure standard platform roles exist even if local setup is simplified.
-- GRANT usage ON SCHEMA public TO anon, authenticated;

-- 3. Analytics & Logging (Production specific)
-- ALTER DATABASE postgres SET log_statement = 'all'; -- Only if needed for deep audit

-- 4. Environment Identifier
-- You can use this to differentiate environments in Edge Functions or Triggers
-- CREATE OR REPLACE FUNCTION get_environment() RETURNS text AS $$ BEGIN RETURN 'production'; END; $$ LANGUAGE plpgsql;

-- 5. Production Admin User (Override local if needed)
-- INSERT INTO auth.users (id, email, ...) VALUES (...) ON CONFLICT DO NOTHING;

-- ─────────────────────────────────────────────────────────────────────────────
