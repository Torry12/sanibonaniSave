-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — QUICK FIX: "permission denied" / 401 on PostgREST
-- Version: 1.0 (April 19, 2026)
--
-- Purpose:
-- If the Android app shows 0 data (Landing/Platform Admin) and Logcat / API calls
-- contain errors like:
--   {"code":"42501","message":"permission denied for table groups"}
-- it usually means the Supabase API roles (anon/authenticated) do NOT have GRANTs.
--
-- Run this script in the Supabase SQL Editor, then re-open the app and tap
-- "Retry Connection" (and optionally "Reset Local Data" in Platform Admin → Maintenance).
-- ─────────────────────────────────────────────────────────────────────────────

-- 1) Restore schema + table privileges
GRANT USAGE ON SCHEMA public TO anon, authenticated, service_role;
GRANT ALL ON ALL TABLES IN SCHEMA public TO postgres, service_role;
GRANT ALL ON ALL SEQUENCES IN SCHEMA public TO postgres, service_role;

GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO anon, authenticated;
GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA public TO anon, authenticated;

-- Ensure future tables/sequences inherit correct privileges
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO anon, authenticated;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO anon, authenticated;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO service_role;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO service_role;

-- 2) Ensure the minimal tables used on Landing exist (safe if already present)
-- NOTE: schema.sql is still the source of truth; this just prevents 404s in dev.
CREATE TABLE IF NOT EXISTS public.platform_settings (
    key TEXT PRIMARY KEY,
    value NUMERIC NOT NULL,
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

INSERT INTO public.platform_settings (key, value)
VALUES
    ('monthly_per_member', 10.0),
    ('registration_fee', 700.0)
ON CONFLICT (key) DO NOTHING;

-- 3) Public discovery (pre-login) requires anon SELECT policies when RLS is enabled
ALTER TABLE IF EXISTS public.groups ENABLE ROW LEVEL SECURITY;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_policies
        WHERE schemaname = 'public'
          AND tablename  = 'groups'
          AND policyname = 'Discover Public Groups'
    ) THEN
        ALTER POLICY "Discover Public Groups" ON public.groups TO anon, authenticated;
        ALTER POLICY "Discover Public Groups" ON public.groups USING (is_public = true);
    ELSE
        CREATE POLICY "Discover Public Groups" ON public.groups
        FOR SELECT TO anon, authenticated
        USING (is_public = true);
    END IF;
END $$;

-- 4) Ask PostgREST to refresh its schema cache (helps clear "404 Not Found" after schema changes)
NOTIFY pgrst, 'reload schema';

