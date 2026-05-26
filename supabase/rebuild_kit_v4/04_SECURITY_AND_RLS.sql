-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — 04. SECURITY AND RLS
-- ─────────────────────────────────────────────────────────────────────────────

BEGIN;

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

CREATE OR REPLACE FUNCTION public.is_group_admin(p_group_id UUID)
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
STABLE
AS $$
BEGIN
    RETURN EXISTS (
        SELECT 1 FROM public.groups
        WHERE id = p_group_id AND admin_user_id = auth.uid()
    );
END;
$$;

CREATE OR REPLACE FUNCTION public.is_group_member(p_group_id UUID)
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
STABLE
AS $$
BEGIN
    RETURN EXISTS (
        SELECT 1 FROM public.members
        WHERE group_id = p_group_id AND user_id = auth.uid()
    );
END;
$$;

-- 3. TABLE GRANTS
GRANT USAGE ON SCHEMA public TO anon, authenticated, service_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO anon, authenticated;
GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA public TO anon, authenticated;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO anon, authenticated;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO anon, authenticated;

-- 4. CORE RLS POLICIES

-- Profiles
CREATE POLICY "Profiles: View own" ON public.profiles FOR SELECT TO authenticated USING (auth.uid() = id);
CREATE POLICY "Profiles: Update own" ON public.profiles FOR UPDATE TO authenticated USING (auth.uid() = id);

-- Groups
CREATE POLICY "Groups: Discover public" ON public.groups FOR SELECT TO anon, authenticated USING (is_public = true);
CREATE POLICY "Groups: View joined" ON public.groups FOR SELECT TO authenticated USING (admin_user_id = auth.uid() OR public.is_group_member(id));
CREATE POLICY "Groups: Create" ON public.groups FOR INSERT TO authenticated WITH CHECK (admin_user_id = auth.uid());

-- Members
CREATE POLICY "Members: View own" ON public.members FOR SELECT TO authenticated USING (user_id = auth.uid());
CREATE POLICY "Members: Admin view group" ON public.members FOR SELECT TO authenticated USING (public.is_group_admin(group_id));
CREATE POLICY "Members: Group members view each other" ON public.members FOR SELECT TO authenticated USING (public.is_group_member(group_id));

-- Actuarial Metrics
CREATE POLICY "Metrics: Admin view group" ON public.group_actuarial_metrics FOR SELECT TO authenticated USING (public.is_group_admin(group_id));
CREATE POLICY "Metrics: Public summary access" ON public.group_actuarial_metrics FOR SELECT TO anon, authenticated USING (true);

-- Platform Settings
CREATE POLICY "Platform Settings: Public view" ON public.platform_settings FOR SELECT TO anon, authenticated USING (true);
CREATE POLICY "Platform Settings: Admin manage" ON public.platform_settings FOR ALL TO authenticated USING (public.is_platform_admin()) WITH CHECK (public.is_platform_admin());

-- Platform Ledger
CREATE POLICY "Platform Ledger: Public summary access" ON public.platform_ledger FOR SELECT TO anon, authenticated USING (true);
CREATE POLICY "Platform Ledger: Admin full access" ON public.platform_ledger FOR ALL TO authenticated USING (public.is_platform_admin()) WITH CHECK (public.is_platform_admin());

-- Contributions
CREATE POLICY "Contributions: Member view own" ON public.contributions FOR SELECT TO authenticated USING (member_id IN (SELECT id FROM public.members WHERE user_id = auth.uid()));
CREATE POLICY "Contributions: Admin view group" ON public.contributions FOR SELECT TO authenticated USING (public.is_group_admin(group_id));

-- Payments
CREATE POLICY "Payments: Member view own" ON public.payments FOR SELECT TO authenticated USING (member_id IN (SELECT id FROM public.members WHERE user_id = auth.uid()));
CREATE POLICY "Payments: Admin view group" ON public.payments FOR SELECT TO authenticated USING (public.is_group_admin(group_id));

-- Loans
CREATE POLICY "Loans: Member view own" ON public.loans FOR SELECT TO authenticated USING (member_id IN (SELECT id FROM public.members WHERE user_id = auth.uid()));
CREATE POLICY "Loans: Admin view group" ON public.loans FOR SELECT TO authenticated USING (public.is_group_admin(group_id));

-- 5. UNIVERSAL PLATFORM ADMIN BYPASS
DO $$
DECLARE v_table text;
BEGIN
    FOR v_table IN (SELECT tablename FROM pg_tables WHERE schemaname = 'public') LOOP
        EXECUTE format('DROP POLICY IF EXISTS "Platform Admin Bypass" ON public.%I', v_table);
        EXECUTE format('CREATE POLICY "Platform Admin Bypass" ON public.%I FOR ALL TO authenticated USING (public.is_platform_admin()) WITH CHECK (public.is_platform_admin())', v_table);
    END LOOP;
END $$;

COMMIT;
