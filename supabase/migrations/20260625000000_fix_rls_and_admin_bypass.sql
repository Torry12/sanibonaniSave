-- SanibonaniSave - Platform Admin RLS Hardening & Universal Bypass
-- Date: 2026-06-25
-- Purpose:
--   1) Harden is_platform_admin() with email fallback for torrymsimango@gmail.com
--   2) Ensure Platform Admin Bypass exists on ALL tables for ALL operations
--   3) Fix member visibility for group peers

BEGIN;

-- 1. HARDENED PLATFORM ADMIN CHECK
CREATE OR REPLACE FUNCTION public.is_platform_admin()
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, auth
AS $$
BEGIN
    RETURN
        COALESCE((auth.jwt() -> 'app_metadata' ->> 'role') = 'platform_admin', false)
        OR COALESCE((auth.jwt() -> 'user_metadata' ->> 'role') = 'platform_admin', false)
        OR EXISTS (
            SELECT 1 FROM public.profiles p
            WHERE p.id = auth.uid() AND p.role = 'platform_admin'
        )
        -- Fallback to canonical email in case metadata or profile is out of sync
        OR COALESCE(auth.jwt() ->> 'email', '') = 'torrymsimango@gmail.com';
END;
$$;

GRANT EXECUTE ON FUNCTION public.is_platform_admin() TO anon, authenticated, service_role;

-- 2. RE-APPLY UNIVERSAL PLATFORM ADMIN BYPASS
DO $$
DECLARE
    v_table text;
BEGIN
    FOR v_table IN
        SELECT tablename
        FROM pg_tables
        WHERE schemaname = 'public'
    LOOP
        EXECUTE format('DROP POLICY IF EXISTS "Platform Admin Bypass" ON public.%I', v_table);
        EXECUTE format(
            'CREATE POLICY "Platform Admin Bypass" ON public.%I FOR ALL TO authenticated USING (public.is_platform_admin()) WITH CHECK (public.is_platform_admin())',
            v_table
        );
    END LOOP;
END $$;

-- 3. PEER VISIBILITY POLICY
-- Allow group members to see each other (required for some UI features)
DROP POLICY IF EXISTS "Members: View group peers" ON public.members;
CREATE POLICY "Members: View group peers"
ON public.members
FOR SELECT
TO authenticated
USING (public.is_group_member(group_id));

-- 4. EXPLICIT MAINTENANCE POLICIES (Redundant but safe)
DROP POLICY IF EXISTS "Groups: Platform admin view all" ON public.groups;
CREATE POLICY "Groups: Platform admin view all" ON public.groups FOR SELECT TO authenticated USING (public.is_platform_admin());

DROP POLICY IF EXISTS "Platform Ledger: Platform admin view all" ON public.platform_ledger;
CREATE POLICY "Platform Ledger: Platform admin view all" ON public.platform_ledger FOR SELECT TO authenticated USING (public.is_platform_admin());

DROP POLICY IF EXISTS "Audit Logs: Platform admin view all" ON public.audit_logs;
CREATE POLICY "Audit Logs: Platform admin view all" ON public.audit_logs FOR SELECT TO authenticated USING (public.is_platform_admin());

COMMIT;
