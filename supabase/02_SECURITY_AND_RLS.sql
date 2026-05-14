-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — MASTER RLS POLICIES & PERMISSIONS
-- Version: 4.0 (Consolidated May 2026)
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
RETURNS BOOLEAN AS $$
BEGIN
    RETURN
        COALESCE((auth.jwt() -> 'app_metadata' ->> 'role') = 'platform_admin', false)
        OR COALESCE((auth.jwt() -> 'user_metadata' ->> 'role') = 'platform_admin', false)
        OR EXISTS (SELECT 1 FROM public.profiles WHERE id = auth.uid() AND role = 'platform_admin');
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

CREATE OR REPLACE FUNCTION public.is_group_admin(p_group_id UUID)
RETURNS BOOLEAN AS $$
BEGIN
    RETURN EXISTS (SELECT 1 FROM public.groups WHERE id = p_group_id AND admin_user_id = auth.uid());
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

CREATE OR REPLACE FUNCTION public.is_group_member(p_group_id UUID)
RETURNS BOOLEAN AS $$
BEGIN
    RETURN EXISTS (SELECT 1 FROM public.members WHERE group_id = p_group_id AND user_id = auth.uid());
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- 3. TABLE GRANTS
GRANT USAGE ON SCHEMA public TO anon, authenticated, service_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO anon, authenticated;
GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA public TO anon, authenticated;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO anon, authenticated;

-- 4. CORE RLS POLICIES

-- Profiles
DROP POLICY IF EXISTS "Profiles: View own" ON public.profiles;
DROP POLICY IF EXISTS "Profiles: Update own" ON public.profiles;
CREATE POLICY "Profiles: View own" ON public.profiles FOR SELECT TO authenticated USING (auth.uid() = id);
CREATE POLICY "Profiles: Update own" ON public.profiles FOR UPDATE TO authenticated USING (auth.uid() = id);

-- Groups
DROP POLICY IF EXISTS "Groups: Discover public" ON public.groups;
DROP POLICY IF EXISTS "Groups: View joined" ON public.groups;
DROP POLICY IF EXISTS "Groups: Create" ON public.groups;
CREATE POLICY "Groups: Discover public" ON public.groups FOR SELECT TO anon, authenticated USING (is_public = true);
CREATE POLICY "Groups: View joined" ON public.groups FOR SELECT TO authenticated USING (admin_user_id = auth.uid() OR public.is_group_member(id));
CREATE POLICY "Groups: Create" ON public.groups FOR INSERT TO authenticated WITH CHECK (admin_user_id = auth.uid());

-- Members
DROP POLICY IF EXISTS "Members: View own" ON public.members;
DROP POLICY IF EXISTS "Members: Admin view group" ON public.members;
CREATE POLICY "Members: View own" ON public.members FOR SELECT TO authenticated USING (user_id = auth.uid());
CREATE POLICY "Members: Admin view group" ON public.members FOR SELECT TO authenticated USING (public.is_group_admin(group_id));

-- Member Documents
DROP POLICY IF EXISTS "Documents: Member view own" ON public.member_documents;
DROP POLICY IF EXISTS "Documents: Admin view group" ON public.member_documents;
DROP POLICY IF EXISTS "Documents: Member upload own" ON public.member_documents;
CREATE POLICY "Documents: Member view own" ON public.member_documents FOR SELECT TO authenticated USING (member_id IN (SELECT id FROM public.members WHERE user_id = auth.uid()));
CREATE POLICY "Documents: Admin view group" ON public.member_documents FOR SELECT TO authenticated USING (public.is_group_admin(group_id));
CREATE POLICY "Documents: Member upload own" ON public.member_documents FOR INSERT TO authenticated WITH CHECK (member_id IN (SELECT id FROM public.members WHERE user_id = auth.uid()));

-- Beneficiary Payout Claims
DROP POLICY IF EXISTS "Claims: Member view own" ON public.beneficiary_payout_claims;
DROP POLICY IF EXISTS "Claims: Admin view group" ON public.beneficiary_payout_claims;
DROP POLICY IF EXISTS "Claims: Member submit own" ON public.beneficiary_payout_claims;
CREATE POLICY "Claims: Member view own" ON public.beneficiary_payout_claims FOR SELECT TO authenticated USING (member_id IN (SELECT id FROM public.members WHERE user_id = auth.uid()));
CREATE POLICY "Claims: Admin view group" ON public.beneficiary_payout_claims FOR SELECT TO authenticated USING (public.is_group_admin(group_id));
CREATE POLICY "Claims: Member submit own" ON public.beneficiary_payout_claims FOR INSERT TO authenticated WITH CHECK (member_id IN (SELECT id FROM public.members WHERE user_id = auth.uid()));

-- 5. UNIVERSAL PLATFORM ADMIN BYPASS
DO $$
DECLARE v_table text;
BEGIN
    FOR v_table IN (SELECT tablename FROM pg_tables WHERE schemaname = 'public') LOOP
        EXECUTE format('DROP POLICY IF EXISTS "Platform Admin Bypass" ON public.%I', v_table);
        EXECUTE format('CREATE POLICY "Platform Admin Bypass" ON public.%I FOR ALL TO authenticated USING (public.is_platform_admin()) WITH CHECK (public.is_platform_admin())', v_table);
    END LOOP;
END $$;

-- 6. STORAGE BUCKETS & POLICIES
INSERT INTO storage.buckets (id, name, public)
VALUES
    ('avatars', 'avatars', true),
    ('documents', 'documents', false),
    ('constitutions', 'constitutions', false),
    ('loan_contracts', 'loan_contracts', false)
ON CONFLICT DO NOTHING;

DROP POLICY IF EXISTS "Storage: Platform Admin Full Access" ON storage.objects;
CREATE POLICY "Storage: Platform Admin Full Access" ON storage.objects FOR ALL TO authenticated USING (public.is_platform_admin());

-- Documents Bucket (Members, Beneficiaries)
DROP POLICY IF EXISTS "Storage: Member Upload Docs" ON storage.objects;
DROP POLICY IF EXISTS "Storage: Member View Own Docs" ON storage.objects;
DROP POLICY IF EXISTS "Storage: Admin View Group Docs" ON storage.objects;
CREATE POLICY "Storage: Member Upload Docs" ON storage.objects FOR INSERT TO authenticated WITH CHECK (bucket_id = 'documents' AND (storage.foldername(name))[1] = 'members');
CREATE POLICY "Storage: Member View Own Docs" ON storage.objects FOR SELECT TO authenticated USING (bucket_id = 'documents' AND (storage.foldername(name))[1] = 'members');
CREATE POLICY "Storage: Admin View Group Docs" ON storage.objects FOR SELECT TO authenticated USING (bucket_id = 'documents');

-- Constitutions Bucket
DROP POLICY IF EXISTS "Storage: Admin Manage Group Constitution" ON storage.objects;
DROP POLICY IF EXISTS "Storage: Member View Group Constitution" ON storage.objects;
CREATE POLICY "Storage: Admin Manage Group Constitution" ON storage.objects FOR ALL TO authenticated USING (bucket_id = 'constitutions' AND public.is_group_admin((storage.foldername(name))[1]::uuid));
CREATE POLICY "Storage: Member View Group Constitution" ON storage.objects FOR SELECT TO authenticated USING (bucket_id = 'constitutions' AND public.is_group_member((storage.foldername(name))[1]::uuid));

-- Loan Contracts Bucket
DROP POLICY IF EXISTS "Storage: Admin Manage Loan Contracts" ON storage.objects;
DROP POLICY IF EXISTS "Storage: Member View Loan Contract" ON storage.objects;
CREATE POLICY "Storage: Admin Manage Loan Contracts" ON storage.objects FOR ALL TO authenticated USING (bucket_id = 'loan_contracts' AND public.is_group_admin((storage.foldername(name))[1]::uuid));
CREATE POLICY "Storage: Member View Loan Contract" ON storage.objects FOR SELECT TO authenticated USING (bucket_id = 'loan_contracts' AND public.is_group_member((storage.foldername(name))[1]::uuid));
