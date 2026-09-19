-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — MASTER RLS POLICIES & PERMISSIONS
-- Version: 4.1 (Bulletproof - June 2026)
-- ─────────────────────────────────────────────────────────────────────────────

-- 1. ENABLE RLS ON ALL TABLES
DO $$
DECLARE
    v_table text;
BEGIN
    FOR v_table IN (SELECT tablename FROM pg_tables WHERE schemaname = 'public') LOOP
        EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY', v_table);
    END LOOP;
END $$;

-- 2. HELPER FUNCTIONS
CREATE OR REPLACE FUNCTION public.is_platform_admin()
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, auth
AS $$
BEGIN
    -- 1. Direct Email Check (Fastest)
    IF (auth.jwt() ->> 'email') = 'torrymsimango@gmail.com' THEN
        RETURN true;
    END IF;

    -- 2. Metadata Check
    IF COALESCE((auth.jwt() -> 'app_metadata' ->> 'role') = 'platform_admin', false)
       OR COALESCE((auth.jwt() -> 'user_metadata' ->> 'role') = 'platform_admin', false) THEN
        RETURN true;
    END IF;

    -- 3. Profile Check
    RETURN EXISTS (
        SELECT 1
        FROM public.profiles p
        WHERE p.id = auth.uid()
          AND p.role = 'platform_admin'
    );
END;
$$;

GRANT EXECUTE ON FUNCTION public.is_platform_admin() TO anon, authenticated, service_role;

CREATE OR REPLACE FUNCTION public.is_group_admin(p_group_id UUID)
RETURNS BOOLEAN
LANGUAGE sql
SECURITY DEFINER
STABLE
AS $$
    SELECT EXISTS (
        SELECT 1 FROM public.groups
        WHERE id = p_group_id AND admin_user_id = auth.uid()
    );
$$;

CREATE OR REPLACE FUNCTION public.is_group_member(p_group_id UUID)
RETURNS BOOLEAN
LANGUAGE sql
SECURITY DEFINER
STABLE
AS $$
    SELECT EXISTS (
        SELECT 1 FROM public.members
        WHERE group_id = p_group_id AND user_id = auth.uid()
    );
$$;

-- 3. TABLE GRANTS
GRANT USAGE ON SCHEMA public TO anon, authenticated, service_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO anon, authenticated;
GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA public TO anon, authenticated;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO anon, authenticated;

-- 4. CORE RLS POLICIES

DO $$
BEGIN
    -- Profiles
    IF EXISTS (SELECT 1 FROM pg_tables WHERE schemaname = 'public' AND tablename = 'profiles') THEN
        EXECUTE 'DROP POLICY IF EXISTS "Profiles: View own" ON public.profiles';
        EXECUTE 'CREATE POLICY "Profiles: View own" ON public.profiles FOR SELECT TO authenticated USING (auth.uid() = id)';
    END IF;

    -- Groups
    IF EXISTS (SELECT 1 FROM pg_tables WHERE schemaname = 'public' AND tablename = 'groups') THEN
        EXECUTE 'DROP POLICY IF EXISTS "Groups: Discover public" ON public.groups';
        EXECUTE 'CREATE POLICY "Groups: Discover public" ON public.groups FOR SELECT TO anon, authenticated USING (is_public = true)';

        EXECUTE 'DROP POLICY IF EXISTS "Groups: View joined" ON public.groups';
        EXECUTE 'CREATE POLICY "Groups: View joined" ON public.groups FOR SELECT TO authenticated USING (admin_user_id = auth.uid() OR public.is_group_member(id))';
    END IF;

    -- Members
    IF EXISTS (SELECT 1 FROM pg_tables WHERE schemaname = 'public' AND tablename = 'members') THEN
        EXECUTE 'DROP POLICY IF EXISTS "Members: View own" ON public.members';
        EXECUTE 'CREATE POLICY "Members: View own" ON public.members FOR SELECT TO authenticated USING (user_id = auth.uid())';

        EXECUTE 'DROP POLICY IF EXISTS "Members: View group peers" ON public.members';
        EXECUTE 'CREATE POLICY "Members: View group peers" ON public.members FOR SELECT TO authenticated USING (public.is_group_member(group_id))';
    END IF;

    -- Member Behavior Track
    IF EXISTS (SELECT 1 FROM pg_tables WHERE schemaname = 'public' AND tablename = 'member_behavior_track') THEN
        EXECUTE 'DROP POLICY IF EXISTS "Behavior: View own" ON public.member_behavior_track';
        EXECUTE 'CREATE POLICY "Behavior: View own" ON public.member_behavior_track FOR SELECT TO authenticated USING (member_id IN (SELECT id FROM public.members WHERE user_id = auth.uid()))';

        EXECUTE 'DROP POLICY IF EXISTS "Behavior: Admin view group" ON public.member_behavior_track';
        EXECUTE 'CREATE POLICY "Behavior: Admin view group" ON public.member_behavior_track FOR SELECT TO authenticated USING (public.is_group_admin(group_id))';
    END IF;

    -- Contributions
    IF EXISTS (SELECT 1 FROM pg_tables WHERE schemaname = 'public' AND tablename = 'contributions') THEN
        EXECUTE 'DROP POLICY IF EXISTS "Contributions: View own" ON public.contributions';
        EXECUTE 'CREATE POLICY "Contributions: View own" ON public.contributions FOR SELECT TO authenticated USING (member_id IN (SELECT id FROM public.members WHERE user_id = auth.uid()))';

        EXECUTE 'DROP POLICY IF EXISTS "Contributions: Admin view group" ON public.contributions';
        EXECUTE 'CREATE POLICY "Contributions: Admin view group" ON public.contributions FOR SELECT TO authenticated USING (public.is_group_admin(group_id))';
    END IF;

    -- Payments
    IF EXISTS (SELECT 1 FROM pg_tables WHERE schemaname = 'public' AND tablename = 'payments') THEN
        EXECUTE 'DROP POLICY IF EXISTS "Payments: View own" ON public.payments';
        EXECUTE 'CREATE POLICY "Payments: View own" ON public.payments FOR SELECT TO authenticated USING (member_id IN (SELECT id FROM public.members WHERE user_id = auth.uid()))';

        EXECUTE 'DROP POLICY IF EXISTS "Payments: Admin view group" ON public.payments';
        EXECUTE 'CREATE POLICY "Payments: Admin view group" ON public.payments FOR SELECT TO authenticated USING (public.is_group_admin(group_id))';
    END IF;

    -- Loans
    IF EXISTS (SELECT 1 FROM pg_tables WHERE schemaname = 'public' AND tablename = 'loans') THEN
        EXECUTE 'DROP POLICY IF EXISTS "Loans: View own" ON public.loans';
        EXECUTE 'CREATE POLICY "Loans: View own" ON public.loans FOR SELECT TO authenticated USING (member_id IN (SELECT id FROM public.members WHERE user_id = auth.uid()))';

        EXECUTE 'DROP POLICY IF EXISTS "Loans: Admin view group" ON public.loans';
        EXECUTE 'CREATE POLICY "Loans: Admin view group" ON public.loans FOR SELECT TO authenticated USING (public.is_group_admin(group_id))';
    END IF;

    -- Loan Repayments
    IF EXISTS (SELECT 1 FROM pg_tables WHERE schemaname = 'public' AND tablename = 'loan_repayments') THEN
        EXECUTE 'DROP POLICY IF EXISTS "Loan Repayments: View own" ON public.loan_repayments';
        EXECUTE 'CREATE POLICY "Loan Repayments: View own" ON public.loan_repayments FOR SELECT TO authenticated USING (member_id IN (SELECT id FROM public.members WHERE user_id = auth.uid()))';

        EXECUTE 'DROP POLICY IF EXISTS "Loan Repayments: Admin view group" ON public.loan_repayments';
        EXECUTE 'CREATE POLICY "Loan Repayments: Admin view group" ON public.loan_repayments FOR SELECT TO authenticated USING (public.is_group_admin(group_id))';
    END IF;

    -- Payouts
    IF EXISTS (SELECT 1 FROM pg_tables WHERE schemaname = 'public' AND tablename = 'payouts') THEN
        EXECUTE 'DROP POLICY IF EXISTS "Payouts: Admin manage" ON public.payouts';
        EXECUTE 'CREATE POLICY "Payouts: Admin manage" ON public.payouts FOR ALL TO authenticated USING (public.is_group_admin(group_id))';
    END IF;

    -- Beneficiary Payout Claims
    IF EXISTS (SELECT 1 FROM pg_tables WHERE schemaname = 'public' AND tablename = 'beneficiary_payout_claims') THEN
        EXECUTE 'DROP POLICY IF EXISTS "Claims: View own" ON public.beneficiary_payout_claims';
        EXECUTE 'CREATE POLICY "Claims: View own" ON public.beneficiary_payout_claims FOR SELECT TO authenticated USING (member_id IN (SELECT id FROM public.members WHERE user_id = auth.uid()))';

        EXECUTE 'DROP POLICY IF EXISTS "Claims: Admin view group" ON public.beneficiary_payout_claims';
        EXECUTE 'CREATE POLICY "Claims: Admin view group" ON public.beneficiary_payout_claims FOR SELECT TO authenticated USING (public.is_group_admin(group_id))';
    END IF;

    -- Ledgers
    IF EXISTS (SELECT 1 FROM pg_tables WHERE schemaname = 'public' AND tablename = 'group_ledger') THEN
        EXECUTE 'DROP POLICY IF EXISTS "Ledger: Admin view group" ON public.group_ledger';
        EXECUTE 'CREATE POLICY "Ledger: Admin view group" ON public.group_ledger FOR SELECT TO authenticated USING (public.is_group_admin(group_id))';
    END IF;

END $$;

-- 5. UNIVERSAL PLATFORM ADMIN BYPASS
DO $$
DECLARE v_table text;
BEGIN
    FOR v_table IN (SELECT tablename FROM pg_tables WHERE schemaname = 'public') LOOP
        EXECUTE format('DROP POLICY IF EXISTS "Platform Admin Bypass" ON public.%I', v_table);
        EXECUTE format(
            'CREATE POLICY "Platform Admin Bypass" ON public.%I ' ||
            'FOR ALL TO authenticated ' ||
            'USING (public.is_platform_admin()) ' ||
            'WITH CHECK (public.is_platform_admin())',
            v_table
        );
    END LOOP;
END $$;
