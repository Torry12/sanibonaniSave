-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — MASTER RLS POLICIES & PERMISSIONS
-- Version: 3.0 (Updated April 29, 2026)
-- ─────────────────────────────────────────────────────────────────────────────

-- 1. ENABLE RLS ON ALL TABLES
ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.groups ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.members ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.beneficiaries ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.contributions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.payments ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.policies ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.notifications ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.member_documents ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.group_actuarial_metrics ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.payouts ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.platform_fees ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.platform_settings ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.loans ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.loan_repayments ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.audit_logs ENABLE ROW LEVEL SECURITY;

-- 2. HELPER FUNCTIONS
CREATE OR REPLACE FUNCTION public.policy_exists(p_name TEXT, p_table TEXT)
RETURNS BOOLEAN AS $$
BEGIN
    RETURN EXISTS (SELECT 1 FROM pg_policies WHERE policyname = p_name AND tablename = p_table);
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION public.is_platform_admin()
RETURNS BOOLEAN AS $$
DECLARE
    v_jwt_role TEXT;
BEGIN
    -- Accept either profile role or JWT metadata role for platform admin access.
    v_jwt_role := COALESCE(
        NULLIF(auth.jwt() -> 'app_metadata' ->> 'role', ''),
        NULLIF(auth.jwt() -> 'user_metadata' ->> 'role', '')
    );

    RETURN v_jwt_role = 'platform_admin' OR EXISTS (
        SELECT 1 FROM public.profiles
        WHERE id = auth.uid() AND role = 'platform_admin'
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Bootstrap platform admin profile(s) from existing auth users.
-- If the auth user exists, this guarantees role = platform_admin in profiles.
INSERT INTO public.profiles (id, full_name, email, role)
SELECT
    u.id,
    COALESCE(NULLIF(u.raw_user_meta_data ->> 'full_name', ''), 'Platform Admin'),
    u.email,
    'platform_admin'
FROM auth.users u
WHERE lower(u.email) IN ('torrymsimango@gmail.com', 'torryymsimango@gmail.com', 'torrymsimango@hotmail.com')
ON CONFLICT (id) DO UPDATE
SET
    full_name = COALESCE(NULLIF(EXCLUDED.full_name, ''), public.profiles.full_name),
    email = EXCLUDED.email,
    role = 'platform_admin';

CREATE OR REPLACE FUNCTION public.is_group_admin(p_group_id UUID)
RETURNS BOOLEAN AS $$
BEGIN
    RETURN EXISTS (
        SELECT 1 FROM public.groups
        WHERE id = p_group_id AND admin_user_id = auth.uid()
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

CREATE OR REPLACE FUNCTION public.is_group_member(p_group_id UUID)
RETURNS BOOLEAN AS $$
BEGIN
    RETURN EXISTS (
        SELECT 1 FROM public.members
        WHERE group_id = p_group_id AND user_id = auth.uid()
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- 3. PROFILES POLICIES
DO $$ BEGIN
    IF NOT public.policy_exists('View own profile', 'profiles') THEN
        CREATE POLICY "View own profile" ON public.profiles FOR SELECT TO authenticated USING (auth.uid() = id);
    END IF;
    IF NOT public.policy_exists('Update own profile', 'profiles') THEN
        CREATE POLICY "Update own profile" ON public.profiles FOR UPDATE TO authenticated
        USING (auth.uid() = id)
        WITH CHECK (
            auth.uid() = id
            AND (
                role IS NULL
                OR role = 'member'
                OR (role = 'group_admin' AND EXISTS (
                    SELECT 1 FROM public.groups g WHERE g.admin_user_id = auth.uid()
                ))
                OR (role = 'platform_admin' AND public.is_platform_admin())
            )
        );
    END IF;
    IF NOT public.policy_exists('Platform admin view all profiles', 'profiles') THEN
        CREATE POLICY "Platform admin view all profiles" ON public.profiles FOR SELECT TO authenticated USING (public.is_platform_admin());
    END IF;
END $$;

-- 4. GROUPS POLICIES
DO $$ BEGIN
    IF NOT public.policy_exists('Discover public groups', 'groups') THEN
        CREATE POLICY "Discover public groups" ON public.groups FOR SELECT TO anon, authenticated USING (is_public = true);
    END IF;
    IF NOT public.policy_exists('View joined groups', 'groups') THEN
        CREATE POLICY "View joined groups" ON public.groups FOR SELECT TO authenticated USING (admin_user_id = auth.uid() OR public.is_group_member(id));
    END IF;
    IF NOT public.policy_exists('Create group', 'groups') THEN
        CREATE POLICY "Create group" ON public.groups FOR INSERT TO authenticated
        WITH CHECK (admin_user_id = auth.uid());
    END IF;
    IF NOT public.policy_exists('Admin manage group', 'groups') THEN
        CREATE POLICY "Admin manage group" ON public.groups FOR UPDATE TO authenticated USING (admin_user_id = auth.uid());
    END IF;
    IF NOT public.policy_exists('Platform admin manage all groups', 'groups') THEN
        CREATE POLICY "Platform admin manage all groups" ON public.groups FOR ALL TO authenticated USING (public.is_platform_admin());
    END IF;
END $$;

-- 5. MEMBERS POLICIES
DO $$ BEGIN
    IF NOT public.policy_exists('View own membership', 'members') THEN
        CREATE POLICY "View own membership" ON public.members FOR SELECT TO authenticated USING (user_id = auth.uid());
    END IF;
    IF NOT public.policy_exists('Admin view group members', 'members') THEN
        CREATE POLICY "Admin view group members" ON public.members FOR SELECT TO authenticated USING (public.is_group_admin(group_id));
    END IF;
    IF NOT public.policy_exists('Join group', 'members') THEN
        CREATE POLICY "Join group" ON public.members FOR INSERT TO authenticated WITH CHECK (true);
    END IF;
    IF NOT public.policy_exists('Update own membership', 'members') THEN
        CREATE POLICY "Update own membership" ON public.members FOR UPDATE TO authenticated USING (user_id = auth.uid());
    END IF;
    IF NOT public.policy_exists('Admin update group members', 'members') THEN
        CREATE POLICY "Admin update group members" ON public.members FOR UPDATE TO authenticated USING (public.is_group_admin(group_id));
    END IF;
    IF NOT public.policy_exists('Platform admin manage all members', 'members') THEN
        CREATE POLICY "Platform admin manage all members" ON public.members FOR ALL TO authenticated USING (public.is_platform_admin());
    END IF;
END $$;

-- 6. CONTRIBUTIONS POLICIES
DO $$ BEGIN
    IF NOT public.policy_exists('View own contributions', 'contributions') THEN
        CREATE POLICY "View own contributions" ON public.contributions FOR SELECT TO authenticated
        USING (member_id IN (SELECT id FROM public.members WHERE user_id = auth.uid()));
    END IF;
    IF NOT public.policy_exists('Admin view group contributions', 'contributions') THEN
        CREATE POLICY "Admin view group contributions" ON public.contributions FOR SELECT TO authenticated USING (public.is_group_admin(group_id));
    END IF;
    IF NOT public.policy_exists('Platform admin view all contributions', 'contributions') THEN
        CREATE POLICY "Platform admin view all contributions" ON public.contributions FOR SELECT TO authenticated USING (public.is_platform_admin());
    END IF;
    IF NOT public.policy_exists('Allow contribution insert', 'contributions') THEN
        CREATE POLICY "Allow contribution insert" ON public.contributions FOR INSERT TO authenticated WITH CHECK (true);
    END IF;
END $$;

-- 7. BENEFICIARIES POLICIES
DO $$ BEGIN
    IF NOT public.policy_exists('Members manage own beneficiaries', 'beneficiaries') THEN
        CREATE POLICY "Members manage own beneficiaries" ON public.beneficiaries FOR ALL TO authenticated
        USING (member_id IN (SELECT id FROM public.members WHERE user_id = auth.uid()));
    END IF;
    IF NOT public.policy_exists('Admins view group beneficiaries', 'beneficiaries') THEN
        CREATE POLICY "Admins view group beneficiaries" ON public.beneficiaries FOR SELECT TO authenticated USING (public.is_group_admin(group_id));
    END IF;
    IF NOT public.policy_exists('Platform admin manage all beneficiaries', 'beneficiaries') THEN
        CREATE POLICY "Platform admin manage all beneficiaries" ON public.beneficiaries FOR ALL TO authenticated USING (public.is_platform_admin());
    END IF;
END $$;

-- 8. PAYMENTS POLICIES
DO $$ BEGIN
    IF NOT public.policy_exists('View own payments', 'payments') THEN
        CREATE POLICY "View own payments" ON public.payments FOR SELECT TO authenticated
        USING (member_id IN (SELECT id FROM public.members WHERE user_id = auth.uid()));
    END IF;
    IF NOT public.policy_exists('Admin view group payments', 'payments') THEN
        CREATE POLICY "Admin view group payments" ON public.payments FOR SELECT TO authenticated USING (public.is_group_admin(group_id));
    END IF;
    IF NOT public.policy_exists('Insert payment', 'payments') THEN
        CREATE POLICY "Insert payment" ON public.payments FOR INSERT TO authenticated WITH CHECK (true);
    END IF;
    IF NOT public.policy_exists('Platform admin view all payments', 'payments') THEN
        CREATE POLICY "Platform admin view all payments" ON public.payments FOR SELECT TO authenticated USING (public.is_platform_admin());
    END IF;
END $$;

-- 9. PAYOUTS POLICIES
DO $$ BEGIN
    IF NOT public.policy_exists('Admin manage group payouts', 'payouts') THEN
        CREATE POLICY "Admin manage group payouts" ON public.payouts FOR ALL TO authenticated USING (public.is_group_admin(group_id));
    END IF;
    IF NOT public.policy_exists('Platform admin manage all payouts', 'payouts') THEN
        CREATE POLICY "Platform admin manage all payouts" ON public.payouts FOR ALL TO authenticated USING (public.is_platform_admin());
    END IF;
END $$;

-- 10. NOTIFICATIONS POLICIES
DO $$ BEGIN
    IF NOT public.policy_exists('View own or group notifications', 'notifications') THEN
        CREATE POLICY "View own or group notifications" ON public.notifications FOR SELECT TO authenticated
        USING (member_id IN (SELECT id FROM public.members WHERE user_id = auth.uid()) OR public.is_group_admin(group_id));
    END IF;
    IF NOT public.policy_exists('Platform admin manage all notifications', 'notifications') THEN
        CREATE POLICY "Platform admin manage all notifications" ON public.notifications FOR ALL TO authenticated USING (public.is_platform_admin());
    END IF;
    IF NOT public.policy_exists('Allow notification insert', 'notifications') THEN
        CREATE POLICY "Allow notification insert" ON public.notifications FOR INSERT TO authenticated WITH CHECK (true);
    END IF;
END $$;

-- 11. LOANS POLICIES
DO $$ BEGIN
    IF NOT public.policy_exists('Members view own loans', 'loans') THEN
        CREATE POLICY "Members view own loans" ON public.loans FOR SELECT TO authenticated
        USING (member_id IN (SELECT id FROM public.members WHERE user_id = auth.uid()));
    END IF;
    IF NOT public.policy_exists('Admins view group loans', 'loans') THEN
        CREATE POLICY "Admins view group loans" ON public.loans FOR SELECT TO authenticated USING (public.is_group_admin(group_id));
    END IF;
    IF NOT public.policy_exists('Platform admin manage all loans', 'loans') THEN
        CREATE POLICY "Platform admin manage all loans" ON public.loans FOR ALL TO authenticated USING (public.is_platform_admin());
    END IF;
END $$;

-- 12. PLATFORM SETTINGS POLICIES
DO $$ BEGIN
    IF NOT public.policy_exists('Allow all view settings', 'platform_settings') THEN
        CREATE POLICY "Allow all view settings" ON public.platform_settings FOR SELECT TO anon, authenticated USING (true);
    END IF;
    IF NOT public.policy_exists('Platform admin manage settings', 'platform_settings') THEN
        CREATE POLICY "Platform admin manage settings" ON public.platform_settings FOR ALL TO authenticated USING (public.is_platform_admin());
    END IF;
END $$;

-- 13. AUDIT LOGS POLICIES
DO $$ BEGIN
    IF NOT public.policy_exists('Allow insert audit logs', 'audit_logs') THEN
        CREATE POLICY "Allow insert audit logs" ON public.audit_logs FOR INSERT TO authenticated WITH CHECK (true);
    END IF;
    IF NOT public.policy_exists('Platform admin view audit logs', 'audit_logs') THEN
        CREATE POLICY "Platform admin view audit logs" ON public.audit_logs FOR SELECT TO authenticated USING (public.is_platform_admin());
    END IF;
END $$;

-- 14. PLATFORM FEES POLICIES
DO $$ BEGIN
    IF NOT public.policy_exists('Admins view group platform fees', 'platform_fees') THEN
        CREATE POLICY "Admins view group platform fees" ON public.platform_fees FOR SELECT TO authenticated USING (public.is_group_admin(group_id));
    END IF;
    IF NOT public.policy_exists('Platform admin manage all platform fees', 'platform_fees') THEN
        CREATE POLICY "Platform admin manage all platform fees" ON public.platform_fees FOR ALL TO authenticated USING (public.is_platform_admin());
    END IF;
END $$;

-- 15. MEMBER DOCUMENTS POLICIES
DO $$ BEGIN
    IF NOT public.policy_exists('Members view own documents', 'member_documents') THEN
        CREATE POLICY "Members view own documents" ON public.member_documents FOR SELECT TO authenticated
        USING (member_id IN (SELECT id FROM public.members WHERE user_id = auth.uid()));
    END IF;
    IF NOT public.policy_exists('Admins view group documents', 'member_documents') THEN
        CREATE POLICY "Admins view group documents" ON public.member_documents FOR SELECT TO authenticated USING (public.is_group_admin(group_id));
    END IF;
    IF NOT public.policy_exists('Platform admin manage all documents', 'member_documents') THEN
        CREATE POLICY "Platform admin manage all documents" ON public.member_documents FOR ALL TO authenticated USING (public.is_platform_admin());
    END IF;
    IF NOT public.policy_exists('Allow document insert', 'member_documents') THEN
        CREATE POLICY "Allow document insert" ON public.member_documents FOR INSERT TO authenticated WITH CHECK (true);
    END IF;
END $$;

-- 16. ACTUARIAL METRICS POLICIES
DO $$ BEGIN
    IF NOT public.policy_exists('Admins view group metrics', 'group_actuarial_metrics') THEN
        CREATE POLICY "Admins view group metrics" ON public.group_actuarial_metrics FOR SELECT TO authenticated USING (public.is_group_admin(group_id));
    END IF;
    IF NOT public.policy_exists('Platform admin manage all metrics', 'group_actuarial_metrics') THEN
        CREATE POLICY "Platform admin manage all metrics" ON public.group_actuarial_metrics FOR ALL TO authenticated USING (public.is_platform_admin());
    END IF;
END $$;

-- 17. POLICIES (INSURANCE) POLICIES
DO $$ BEGIN
    IF NOT public.policy_exists('View group insurance policies', 'policies') THEN
        CREATE POLICY "View group insurance policies" ON public.policies FOR SELECT TO authenticated
        USING (public.is_group_admin(group_id) OR public.is_group_member(group_id));
    END IF;
    IF NOT public.policy_exists('Admin manage group insurance policies', 'policies') THEN
        CREATE POLICY "Admin manage group insurance policies" ON public.policies FOR ALL TO authenticated USING (public.is_group_admin(group_id));
    END IF;
END $$;

-- 18. UNIVERSAL PLATFORM ADMIN FULL ACCESS (ALL TABLES)
-- 18A. HARDENED ROLE/OWNERSHIP POLICIES (RECREATE TO OVERRIDE OLDER DEFINITIONS)
DROP POLICY IF EXISTS "Create group" ON public.groups;
CREATE POLICY "Create group" ON public.groups FOR INSERT TO authenticated
WITH CHECK (admin_user_id = auth.uid());

DROP POLICY IF EXISTS "Update own profile" ON public.profiles;
CREATE POLICY "Update own profile" ON public.profiles FOR UPDATE TO authenticated
USING (auth.uid() = id)
WITH CHECK (
    auth.uid() = id
    AND (
        role IS NULL
        OR role = 'member'
        OR (role = 'group_admin' AND EXISTS (
            SELECT 1 FROM public.groups g WHERE g.admin_user_id = auth.uid()
        ))
        OR (role = 'platform_admin' AND public.is_platform_admin())
    )
);

-- 18B. UNIVERSAL PLATFORM ADMIN FULL ACCESS (ALL TABLES)
DO $$
DECLARE
    v_table TEXT;
    v_tables TEXT[] := ARRAY[
        'profiles',
        'groups',
        'members',
        'beneficiaries',
        'contributions',
        'payments',
        'policies',
        'notifications',
        'member_documents',
        'group_actuarial_metrics',
        'payouts',
        'platform_fees',
        'platform_settings',
        'loans',
        'loan_repayments',
        'audit_logs'
    ];
BEGIN
    FOREACH v_table IN ARRAY v_tables LOOP
        IF NOT public.policy_exists('Platform admin full access', v_table) THEN
            EXECUTE format(
                'CREATE POLICY %I ON public.%I FOR ALL TO authenticated USING (public.is_platform_admin()) WITH CHECK (public.is_platform_admin())',
                'Platform admin full access',
                v_table
            );
        END IF;
    END LOOP;
END $$;

-- 19. SERVICE ROLE BYPASS
-- Ensure service_role can always access everything
GRANT ALL ON ALL TABLES IN SCHEMA public TO service_role;
GRANT ALL ON ALL SEQUENCES IN SCHEMA public TO service_role;

-- 20. REFRESH PostgREST CACHE
NOTIFY pgrst, 'reload schema';
