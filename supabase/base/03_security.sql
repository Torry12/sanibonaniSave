-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — MASTER RLS POLICIES & PERMISSIONS
-- Version: 7.1 (Organized Layout - June 2026)
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
    RETURN
        COALESCE((auth.jwt() -> 'app_metadata' ->> 'role') = 'platform_admin', false)
        OR COALESCE((auth.jwt() -> 'user_metadata' ->> 'role') = 'platform_admin', false)
        OR EXISTS (
            SELECT 1
            FROM public.profiles p
            WHERE p.id = auth.uid()
              AND p.role = 'platform_admin'
        )
        OR COALESCE(auth.jwt() ->> 'email', '') = 'torrymsimango@gmail.com';
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

-- Profiles
DROP POLICY IF EXISTS "Profiles: View own" ON public.profiles;
CREATE POLICY "Profiles: View own" ON public.profiles FOR SELECT TO authenticated USING (auth.uid() = id);

-- Groups
DROP POLICY IF EXISTS "Groups: Discover public" ON public.groups;
CREATE POLICY "Groups: Discover public" ON public.groups FOR SELECT TO anon, authenticated USING (is_public = true);

DROP POLICY IF EXISTS "Groups: View joined" ON public.groups;
CREATE POLICY "Groups: View joined" ON public.groups FOR SELECT TO authenticated USING (admin_user_id = auth.uid() OR public.is_group_member(id));

-- Members
DROP POLICY IF EXISTS "Members: View own" ON public.members;
CREATE POLICY "Members: View own" ON public.members FOR SELECT TO authenticated USING (user_id = auth.uid());

DROP POLICY IF EXISTS "Members: View group peers" ON public.members;
CREATE POLICY "Members: View group peers" ON public.members FOR SELECT TO authenticated USING (public.is_group_member(group_id));

-- Member Behavior Track
DROP POLICY IF EXISTS "Behavior: View own" ON public.member_behavior_track;
CREATE POLICY "Behavior: View own" ON public.member_behavior_track
FOR SELECT TO authenticated
USING (member_id IN (SELECT id FROM public.members WHERE user_id = auth.uid()));

DROP POLICY IF EXISTS "Behavior: Admin view group" ON public.member_behavior_track;
CREATE POLICY "Behavior: Admin view group" ON public.member_behavior_track
FOR SELECT TO authenticated
USING (public.is_group_admin(group_id));

-- 5. UNIVERSAL PLATFORM ADMIN BYPASS
DO $$
DECLARE v_table text;
BEGIN
    FOR v_table IN (SELECT tablename FROM pg_tables WHERE schemaname = 'public') LOOP
        EXECUTE format('DROP POLICY IF EXISTS "Platform Admin Bypass" ON public.%I', v_table);
        EXECUTE format('CREATE POLICY "Platform Admin Bypass" ON public.%I FOR ALL TO authenticated USING (public.is_platform_admin()) WITH CHECK (public.is_platform_admin())', v_table);
    END LOOP;
END $$;
