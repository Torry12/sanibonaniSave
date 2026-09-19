-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — STORAGE RLS POLICIES FIX
-- Version: 1.3 (Unified Admin & Owner Logic)
-- ─────────────────────────────────────────────────────────────────────────────

-- Ensure buckets are created
INSERT INTO storage.buckets (id, name, public)
VALUES
    ('avatars', 'avatars', true),
    ('constitutions', 'constitutions', true),
    ('documents', 'documents', false),
    ('loan_contracts', 'loan_contracts', false)
ON CONFLICT (id) DO NOTHING;

-- Enable RLS on storage.objects
ALTER TABLE storage.objects ENABLE ROW LEVEL SECURITY;

/**
 * HELPER: Checks if the current user has authority over the member ID in the path.
 * Authority = Being the member themselves OR being the admin of their group.
 */
CREATE OR REPLACE FUNCTION public.can_access_member_storage(p_path text)
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_member_id_text text;
BEGIN
    v_member_id_text := (storage.foldername(p_path))[2];
    IF v_member_id_text IS NULL THEN RETURN false; END IF;

    -- Platform Admins always have access
    IF public.is_platform_admin() THEN RETURN true; END IF;

    RETURN EXISTS (
        SELECT 1 FROM public.members m
        LEFT JOIN public.groups g ON m.group_id = g.id
        WHERE m.id::text = v_member_id_text
          AND (m.user_id = auth.uid() OR g.admin_user_id = auth.uid())
    );
END;
$$;


-- 1. AVATARS (Public bucket, but restricted modifications)
DROP POLICY IF EXISTS "Avatars: Public view" ON storage.objects;
CREATE POLICY "Avatars: Public view" ON storage.objects FOR SELECT USING (bucket_id = 'avatars');

DROP POLICY IF EXISTS "Avatars: Authority upload" ON storage.objects;
CREATE POLICY "Avatars: Authority upload" ON storage.objects FOR INSERT TO authenticated WITH CHECK (
    bucket_id = 'avatars' AND
    (storage.foldername(name))[1] = 'members' AND
    public.can_access_member_storage(name)
);

DROP POLICY IF EXISTS "Avatars: Authority update" ON storage.objects;
CREATE POLICY "Avatars: Authority update" ON storage.objects FOR UPDATE TO authenticated USING (
    bucket_id = 'avatars' AND
    (storage.foldername(name))[1] = 'members' AND
    public.can_access_member_storage(name)
);

DROP POLICY IF EXISTS "Avatars: Authority delete" ON storage.objects;
CREATE POLICY "Avatars: Authority delete" ON storage.objects FOR DELETE TO authenticated USING (
    bucket_id = 'avatars' AND
    (storage.foldername(name))[1] = 'members' AND
    public.can_access_member_storage(name)
);


-- 2. CONSTITUTIONS (Public view, Admin can upload)
DROP POLICY IF EXISTS "Constitutions: Public view" ON storage.objects;
CREATE POLICY "Constitutions: Public view" ON storage.objects FOR SELECT USING (bucket_id = 'constitutions');

DROP POLICY IF EXISTS "Constitutions: Admin upload" ON storage.objects;
CREATE POLICY "Constitutions: Admin upload" ON storage.objects FOR INSERT TO authenticated WITH CHECK (
    bucket_id = 'constitutions' AND (
        public.is_group_admin((storage.foldername(name))[1]::uuid) OR
        public.is_platform_admin()
    )
);


-- 3. DOCUMENTS (Private bucket, Member/Admin access)
DROP POLICY IF EXISTS "Documents: Authority view" ON storage.objects;
CREATE POLICY "Documents: Authority view" ON storage.objects FOR SELECT TO authenticated USING (
    bucket_id = 'documents' AND (
        (storage.foldername(name))[1] = 'members' AND public.can_access_member_storage(name)
    )
);

DROP POLICY IF EXISTS "Documents: Authority upload" ON storage.objects;
CREATE POLICY "Documents: Authority upload" ON storage.objects FOR INSERT TO authenticated WITH CHECK (
    bucket_id = 'documents' AND
    (storage.foldername(name))[1] = 'members' AND
    public.can_access_member_storage(name)
);


-- 4. LOAN CONTRACTS (Private bucket, Member/Admin access)
-- Path format: groups/{groupId}/loans/{loanId}/contract.pdf
DROP POLICY IF EXISTS "Loan Contracts: Member/Admin view" ON storage.objects;
CREATE POLICY "Loan Contracts: Member/Admin view" ON storage.objects FOR SELECT TO authenticated USING (
    bucket_id = 'loan_contracts' AND (
        public.is_group_admin((storage.foldername(name))[2]::uuid) OR
        public.is_group_member((storage.foldername(name))[2]::uuid) OR
        public.is_platform_admin()
    )
);

DROP POLICY IF EXISTS "Loan Contracts: Admin upload" ON storage.objects;
CREATE POLICY "Loan Contracts: Admin upload" ON storage.objects FOR INSERT TO authenticated WITH CHECK (
    bucket_id = 'loan_contracts' AND (
        public.is_group_admin((storage.foldername(name))[2]::uuid) OR
        public.is_platform_admin()
    )
);

-- 5. CATCH-ALL FOR PLATFORM ADMIN
DROP POLICY IF EXISTS "Storage: Platform Admin All" ON storage.objects;
CREATE POLICY "Storage: Platform Admin All" ON storage.objects FOR ALL TO authenticated USING (public.is_platform_admin());
