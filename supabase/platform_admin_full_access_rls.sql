-- ==========================================================================
-- SanibonaniSave - Platform Admin Full Access RLS
-- Version: 1.0 (May 4, 2026)
--
-- Purpose:
-- 1) Ensure public.is_platform_admin() exists and is SECURITY DEFINER.
-- 2) Enable RLS on every table in public schema.
-- 3) Create (or recreate) one consistent ALL policy per table so platform
--    admin can SELECT/INSERT/UPDATE/DELETE every record.
-- 4) Keep anon/authenticated/service_role grants aligned.
--
-- Run in Supabase SQL Editor with service_role/admin privileges.
-- ============================================================================

-- 0) Ensure schema usage grants are present
GRANT USAGE ON SCHEMA public TO anon, authenticated, service_role;

-- 1) Platform-admin helper function
CREATE OR REPLACE FUNCTION public.is_platform_admin()
RETURNS boolean
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_role text;
BEGIN
    -- Prefer persisted profile role
    IF EXISTS (
        SELECT 1
        FROM public.profiles p
        WHERE p.id = auth.uid()
          AND p.role = 'platform_admin'
    ) THEN
        RETURN true;
    END IF;

    -- Fallback to JWT metadata (for fresh sessions / profile lag)
    v_role := COALESCE(
        NULLIF(auth.jwt() -> 'app_metadata' ->> 'role', ''),
        NULLIF(auth.jwt() -> 'user_metadata' ->> 'role', '')
    );

    RETURN v_role = 'platform_admin';
END;
$$;

-- 2) Apply universal policy to all public tables
DO $$
DECLARE
    v_table text;
    v_policy_name constant text := 'Platform admin full access';
BEGIN
    FOR v_table IN
        SELECT t.tablename
        FROM pg_tables t
        WHERE t.schemaname = 'public'
        ORDER BY t.tablename
    LOOP
        BEGIN
            EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY', v_table);

            -- Recreate to guarantee both USING and WITH CHECK are present.
            EXECUTE format(
                'DROP POLICY IF EXISTS %I ON public.%I',
                v_policy_name,
                v_table
            );

            EXECUTE format(
                'CREATE POLICY %I ON public.%I FOR ALL TO authenticated USING (public.is_platform_admin()) WITH CHECK (public.is_platform_admin())',
                v_policy_name,
                v_table
            );

            RAISE NOTICE 'Applied platform admin full access on table: %', v_table;
        EXCEPTION
            WHEN others THEN
                -- Continue with other tables if one table cannot be altered.
                RAISE WARNING 'Skipped table % due to: %', v_table, SQLERRM;
        END;
    END LOOP;
END $$;

-- 3) Keep grants aligned (RLS still controls row access for anon/authenticated)
GRANT ALL ON ALL TABLES IN SCHEMA public TO postgres, service_role;
GRANT ALL ON ALL SEQUENCES IN SCHEMA public TO postgres, service_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO authenticated;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO anon;

-- 4) Refresh PostgREST schema cache
NOTIFY pgrst, 'reload schema';

-- 5) Verification helpers
-- 5a) Confirm platform admin helper exists
SELECT proname, prosecdef
FROM pg_proc
WHERE pronamespace = 'public'::regnamespace
  AND proname = 'is_platform_admin';

-- 5b) Confirm policy is on all public tables
SELECT
    t.tablename,
    CASE WHEN p.policyname IS NULL THEN 'MISSING' ELSE 'OK' END AS platform_admin_policy
FROM pg_tables t
LEFT JOIN pg_policies p
    ON p.schemaname = 'public'
   AND p.tablename = t.tablename
   AND p.policyname = 'Platform admin full access'
WHERE t.schemaname = 'public'
ORDER BY t.tablename;

