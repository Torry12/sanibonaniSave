-- SanibonaniSave RBAC v3 (Platform Admin, Group Admin, Member)
-- Apply in Supabase SQL Editor with a privileged role.
-- This script hardens key RLS rules to enforce least-privilege access.

-- 1) Base grants for API roles
GRANT USAGE ON SCHEMA public TO anon, authenticated, service_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO authenticated;
GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA public TO authenticated;

-- 2) Helper functions (SECURITY DEFINER to avoid recursive RLS lookups)
CREATE OR REPLACE FUNCTION public.is_platform_admin()
RETURNS BOOLEAN AS $$
BEGIN
    RETURN EXISTS (
        SELECT 1 FROM public.profiles
        WHERE id = auth.uid() AND role IN ('platform_admin', 'group_admin')
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER STABLE;

CREATE OR REPLACE FUNCTION public.is_group_admin_for(p_group_id UUID)
RETURNS BOOLEAN AS $$
BEGIN
    RETURN EXISTS (
        SELECT 1 FROM public.groups
        WHERE id = p_group_id AND admin_user_id = auth.uid()
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER STABLE;

CREATE OR REPLACE FUNCTION public.is_group_member_for(p_group_id UUID)
RETURNS BOOLEAN AS $$
BEGIN
    RETURN EXISTS (
        SELECT 1 FROM public.members
        WHERE group_id = p_group_id AND user_id = auth.uid()
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER STABLE;

-- 3) Ensure RLS is enabled on protected tables
ALTER TABLE IF EXISTS public.profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE IF EXISTS public.groups ENABLE ROW LEVEL SECURITY;
ALTER TABLE IF EXISTS public.members ENABLE ROW LEVEL SECURITY;
ALTER TABLE IF EXISTS public.contributions ENABLE ROW LEVEL SECURITY;
ALTER TABLE IF EXISTS public.payouts ENABLE ROW LEVEL SECURITY;
ALTER TABLE IF EXISTS public.platform_fees ENABLE ROW LEVEL SECURITY;

-- 4) Remove permissive legacy policies that bypass role layers
DROP POLICY IF EXISTS "Allow Registration Insert" ON public.groups;
DROP POLICY IF EXISTS "Allow Registration Update" ON public.groups;
DROP POLICY IF EXISTS "Allow Member Registration" ON public.members;
DROP POLICY IF EXISTS "Allow Contribution Insert" ON public.contributions;
DROP POLICY IF EXISTS "Allow Platform Fee Insert" ON public.platform_fees;
DROP POLICY IF EXISTS "Allow Platform Fee Update" ON public.platform_fees;

-- 5) Profiles policies
DROP POLICY IF EXISTS "AuthV3 Profiles Self Read" ON public.profiles;
CREATE POLICY "AuthV3 Profiles Self Read" ON public.profiles
FOR SELECT TO authenticated
USING (id = auth.uid() OR public.is_platform_admin());

DROP POLICY IF EXISTS "AuthV3 Profiles Self Update" ON public.profiles;
CREATE POLICY "AuthV3 Profiles Self Update" ON public.profiles
FOR UPDATE TO authenticated
USING (id = auth.uid() OR public.is_platform_admin())
WITH CHECK (id = auth.uid() OR public.is_platform_admin());

-- 6) Groups policies
DROP POLICY IF EXISTS "AuthV3 Groups Select" ON public.groups;
CREATE POLICY "AuthV3 Groups Select" ON public.groups
FOR SELECT TO authenticated
USING (
    is_public = true
    OR admin_user_id = auth.uid()
    OR public.is_group_member_for(id)
    OR public.is_platform_admin()
);

DROP POLICY IF EXISTS "AuthV3 Groups Insert" ON public.groups;
CREATE POLICY "AuthV3 Groups Insert" ON public.groups
FOR INSERT TO authenticated
WITH CHECK (
    admin_user_id = auth.uid()
    OR public.is_platform_admin()
);

DROP POLICY IF EXISTS "AuthV3 Groups UpdateDelete" ON public.groups;
CREATE POLICY "AuthV3 Groups UpdateDelete" ON public.groups
FOR ALL TO authenticated
USING (
    admin_user_id = auth.uid()
    OR public.is_platform_admin()
)
WITH CHECK (
    admin_user_id = auth.uid()
    OR public.is_platform_admin()
);

-- 7) Members policies
DROP POLICY IF EXISTS "AuthV3 Members Select" ON public.members;
CREATE POLICY "AuthV3 Members Select" ON public.members
FOR SELECT TO authenticated
USING (
    user_id = auth.uid()
    OR public.is_group_admin_for(group_id)
    OR public.is_platform_admin()
);

DROP POLICY IF EXISTS "AuthV3 Members Insert" ON public.members;
CREATE POLICY "AuthV3 Members Insert" ON public.members
FOR INSERT TO authenticated
WITH CHECK (
    user_id = auth.uid()
    OR public.is_group_admin_for(group_id)
    OR public.is_platform_admin()
);

DROP POLICY IF EXISTS "AuthV3 Members UpdateDelete" ON public.members;
CREATE POLICY "AuthV3 Members UpdateDelete" ON public.members
FOR ALL TO authenticated
USING (
    user_id = auth.uid()
    OR public.is_group_admin_for(group_id)
    OR public.is_platform_admin()
)
WITH CHECK (
    user_id = auth.uid()
    OR public.is_group_admin_for(group_id)
    OR public.is_platform_admin()
);

-- 8) Contributions policies
DROP POLICY IF EXISTS "AuthV3 Contributions Select" ON public.contributions;
CREATE POLICY "AuthV3 Contributions Select" ON public.contributions
FOR SELECT TO authenticated
USING (
    member_id IN (SELECT id FROM public.members WHERE user_id = auth.uid())
    OR public.is_group_admin_for(group_id)
    OR public.is_platform_admin()
);

DROP POLICY IF EXISTS "AuthV3 Contributions Insert" ON public.contributions;
CREATE POLICY "AuthV3 Contributions Insert" ON public.contributions
FOR INSERT TO authenticated
WITH CHECK (
    public.is_platform_admin()
    OR public.is_group_admin_for(group_id)
    OR member_id IN (SELECT id FROM public.members WHERE user_id = auth.uid() AND group_id = contributions.group_id)
);

-- 9) Payouts policies
DROP POLICY IF EXISTS "AuthV3 Payouts Select" ON public.payouts;
CREATE POLICY "AuthV3 Payouts Select" ON public.payouts
FOR SELECT TO authenticated
USING (
    public.is_group_admin_for(group_id)
    OR public.is_platform_admin()
);

DROP POLICY IF EXISTS "AuthV3 Payouts Insert" ON public.payouts;
CREATE POLICY "AuthV3 Payouts Insert" ON public.payouts
FOR INSERT TO authenticated
WITH CHECK (
    public.is_group_admin_for(group_id)
    OR public.is_platform_admin()
);

DROP POLICY IF EXISTS "AuthV3 Payouts Update" ON public.payouts;
CREATE POLICY "AuthV3 Payouts Update" ON public.payouts
FOR UPDATE TO authenticated
USING (
    public.is_group_admin_for(group_id)
    OR public.is_platform_admin()
)
WITH CHECK (
    public.is_group_admin_for(group_id)
    OR public.is_platform_admin()
);

-- 10) Platform fee policies
DROP POLICY IF EXISTS "AuthV3 Platform Fees Select" ON public.platform_fees;
CREATE POLICY "AuthV3 Platform Fees Select" ON public.platform_fees
FOR SELECT TO authenticated
USING (
    public.is_group_admin_for(group_id)
    OR public.is_platform_admin()
);

DROP POLICY IF EXISTS "AuthV3 Platform Fees Write" ON public.platform_fees;
CREATE POLICY "AuthV3 Platform Fees Write" ON public.platform_fees
FOR ALL TO authenticated
USING (public.is_platform_admin())
WITH CHECK (public.is_platform_admin());

