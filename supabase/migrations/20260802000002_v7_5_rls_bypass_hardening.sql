-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — RLS BYPASS HARDENING
-- Version: 1.0 (August 2, 2026)
-- ─────────────────────────────────────────────────────────────────────────────

-- 1. HARDEN IDENTITY CHECK
-- Ensure we handle NULL emails and case-sensitivity robustly.
CREATE OR REPLACE FUNCTION public.is_platform_admin()
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, auth
AS $$
DECLARE
    v_email TEXT;
BEGIN
    v_email := LOWER(COALESCE(auth.jwt() ->> 'email', ''));

    -- Hardcoded bypass for primary developer/admin
    IF v_email = 'torrymsimango@gmail.com' THEN RETURN true; END IF;

    -- Check roles in profiles
    RETURN EXISTS (
        SELECT 1 FROM public.profiles
        WHERE id = auth.uid()
        AND role = 'platform_admin'
    );
END;
$$;


-- 2. RE-APPLY UNIVERSAL BYPASS
-- This ensures all tables (including those added in recent migrations) are covered.
DO $$
DECLARE v_table text;
BEGIN
    FOR v_table IN (SELECT tablename FROM pg_tables WHERE schemaname = 'public') LOOP
        EXECUTE format('DROP POLICY IF EXISTS "Platform Admin Bypass" ON public.%I', v_table);
        EXECUTE format('CREATE POLICY "Platform Admin Bypass" ON public.%I FOR ALL TO authenticated USING (public.is_platform_admin()) WITH CHECK (public.is_platform_admin())', v_table);
    END LOOP;
END $$;
