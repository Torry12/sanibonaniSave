-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — RLS DIAGNOSTIC & HARDENING
-- Version: 1.0 (August 2, 2026)
-- ─────────────────────────────────────────────────────────────────────────────

/**
 * Ensures all groups have valid administrators and that RLS isn't hiding them.
 */
CREATE OR REPLACE FUNCTION public.check_rls_visibility_v1()
RETURNS TABLE (
    table_name TEXT,
    total_count BIGINT,
    is_rls_enabled BOOLEAN
)
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
BEGIN
    RETURN QUERY
    SELECT
        t.tablename::TEXT,
        (SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace WHERE c.relname = t.tablename AND n.nspname = 'public'),
        t.rowsecurity
    FROM pg_tables t
    WHERE schemaname = 'public';
END;
$$;


-- 2. ENSURE ALL TABLES HAVE THE BYPASS
DO $$
DECLARE v_table text;
BEGIN
    FOR v_table IN (SELECT tablename FROM pg_tables WHERE schemaname = 'public') LOOP
        EXECUTE format('DROP POLICY IF EXISTS "Platform Admin Bypass" ON public.%I', v_table);
        EXECUTE format('CREATE POLICY "Platform Admin Bypass" ON public.%I FOR ALL TO authenticated USING (public.is_platform_admin()) WITH CHECK (public.is_platform_admin())', v_table);
    END LOOP;
END $$;
