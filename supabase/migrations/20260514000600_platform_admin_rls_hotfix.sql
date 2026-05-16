-- SanibonaniSave - Platform Admin RLS Hotfix
-- Date: 2026-05-13
-- Purpose:
--   1) Fix platform-admin role detection (COALESCE bug)
--   2) Re-apply platform-admin bypass policies with WITH CHECK
--   3) Ensure maintenance flows can read lower-level data (groups/members/etc.)

BEGIN;

-- 1) Fix helper role check.
-- Previous logic used COALESCE(boolean, boolean) which returned false too early.
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
            SELECT 1
            FROM public.profiles p
            WHERE p.id = auth.uid()
              AND p.role = 'platform_admin'
        );
END;
$$;

GRANT EXECUTE ON FUNCTION public.is_platform_admin() TO anon, authenticated, service_role;

-- 2) Re-create universal platform admin bypass on all public tables.
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

-- 3) Explicit read policies for maintenance views (defensive and easier to audit).
DROP POLICY IF EXISTS "Groups: Platform admin view all" ON public.groups;
CREATE POLICY "Groups: Platform admin view all"
ON public.groups
FOR SELECT
TO authenticated
USING (public.is_platform_admin());

DROP POLICY IF EXISTS "Members: Platform admin view all" ON public.members;
CREATE POLICY "Members: Platform admin view all"
ON public.members
FOR SELECT
TO authenticated
USING (public.is_platform_admin());

DROP POLICY IF EXISTS "Notifications: Platform admin view all" ON public.notifications;
CREATE POLICY "Notifications: Platform admin view all"
ON public.notifications
FOR SELECT
TO authenticated
USING (public.is_platform_admin());

DROP POLICY IF EXISTS "Claims: Platform admin view all" ON public.beneficiary_payout_claims;
CREATE POLICY "Claims: Platform admin view all"
ON public.beneficiary_payout_claims
FOR SELECT
TO authenticated
USING (public.is_platform_admin());

DROP POLICY IF EXISTS "Documents: Platform admin view all" ON public.member_documents;
CREATE POLICY "Documents: Platform admin view all"
ON public.member_documents
FOR SELECT
TO authenticated
USING (public.is_platform_admin());

DROP POLICY IF EXISTS "Audit Logs: Platform admin view all" ON public.audit_logs;
CREATE POLICY "Audit Logs: Platform admin view all"
ON public.audit_logs
FOR SELECT
TO authenticated
USING (public.is_platform_admin());

DROP POLICY IF EXISTS "Platform Ledger: Platform admin view all" ON public.platform_ledger;
CREATE POLICY "Platform Ledger: Platform admin view all"
ON public.platform_ledger
FOR SELECT
TO authenticated
USING (public.is_platform_admin());

DROP POLICY IF EXISTS "Payments: Platform admin view all" ON public.payments;
CREATE POLICY "Payments: Platform admin view all"
ON public.payments
FOR SELECT
TO authenticated
USING (public.is_platform_admin());

DROP POLICY IF EXISTS "Payouts: Platform admin view all" ON public.payouts;
CREATE POLICY "Payouts: Platform admin view all"
ON public.payouts
FOR SELECT
TO authenticated
USING (public.is_platform_admin());

DROP POLICY IF EXISTS "Loans: Platform admin view all" ON public.loans;
CREATE POLICY "Loans: Platform admin view all"
ON public.loans
FOR SELECT
TO authenticated
USING (public.is_platform_admin());

COMMIT;

