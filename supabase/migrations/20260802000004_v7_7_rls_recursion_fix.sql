-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — RLS RECURSION & VISIBILITY FIX
-- Version: 1.0 (August 2, 2026)
-- ─────────────────────────────────────────────────────────────────────────────

-- 1. HARDEN PLATFORM ADMIN CHECK (Non-Recursive)
CREATE OR REPLACE FUNCTION public.is_platform_admin()
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, auth
AS $$
DECLARE
  v_role text;
  v_email text;
BEGIN
  -- 1. Check JWT claims first (Bypass table hits entirely if possible)
  v_role := auth.jwt() -> 'app_metadata' ->> 'role';
  IF v_role = 'platform_admin' THEN RETURN true; END IF;

  -- 2. Hardcoded email bypass (Case-insensitive)
  v_email := LOWER(COALESCE(auth.jwt() ->> 'email', ''));
  IF v_email = 'torrymsimango@gmail.com' THEN RETURN true; END IF;

  -- 3. Fallback to profiles table
  RETURN EXISTS (
    SELECT 1 FROM public.profiles
    WHERE id = auth.uid()
    AND role = 'platform_admin'
  );
END;
$$;


-- 2. HARDEN GROUP ACCESS HELPERS (SECURITY DEFINER stops RLS recursion)
CREATE OR REPLACE FUNCTION public.is_group_admin(p_group_id UUID)
RETURNS BOOLEAN
LANGUAGE sql
SECURITY DEFINER
SET search_path = public
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
SET search_path = public
STABLE
AS $$
    SELECT EXISTS (
        SELECT 1 FROM public.members
        WHERE group_id = p_group_id AND user_id = auth.uid()
    );
$$;


-- 3. SIMPLIFIED POLICIES (Avoid redundant checks)

-- Members Table: Simplified to use a direct ID comparison for "own" and helper for "admin"
DROP POLICY IF EXISTS "Members: View own" ON public.members;
DROP POLICY IF EXISTS "Members: Admin view group" ON public.members;
CREATE POLICY "Members: View own" ON public.members FOR SELECT TO authenticated USING (user_id = auth.uid());
CREATE POLICY "Members: Admin view group" ON public.members FOR SELECT TO authenticated USING (public.is_group_admin(group_id));

-- Group Ledger: Use non-recursive helper
DROP POLICY IF EXISTS "Group Ledger: View joined" ON public.group_ledger;
DROP POLICY IF EXISTS "Group Ledger: Admin view group" ON public.group_ledger;
CREATE POLICY "Group Ledger: View joined" ON public.group_ledger FOR SELECT TO authenticated USING (public.is_group_member(group_id));
CREATE POLICY "Group Ledger: Admin view group" ON public.group_ledger FOR SELECT TO authenticated USING (public.is_group_admin(group_id));


-- 4. RE-APPLY UNIVERSAL BYPASS (FORCE REFRESH)
DO $$
DECLARE v_table text;
BEGIN
    FOR v_table IN (SELECT tablename FROM pg_tables WHERE schemaname = 'public') LOOP
        EXECUTE format('DROP POLICY IF EXISTS "Platform Admin Bypass" ON public.%I', v_table);
        EXECUTE format('CREATE POLICY "Platform Admin Bypass" ON public.%I FOR ALL TO authenticated USING (public.is_platform_admin()) WITH CHECK (public.is_platform_admin())', v_table);
    END LOOP;
END $$;
