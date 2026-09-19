-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — STORAGE RLS POLICIES
-- Version: 1.0 (Initial Setup - July 2026)
-- ─────────────────────────────────────────────────────────────────────────────

-- Ensure buckets are created (if not already by seed data)
INSERT INTO storage.buckets (id, name, public)
VALUES
    ('avatars', 'avatars', true),
    ('constitutions', 'constitutions', true),
    ('documents', 'documents', false),
    ('loan_contracts', 'loan_contracts', false)
ON CONFLICT (id) DO NOTHING;

-- Enable RLS on storage.objects
ALTER TABLE storage.objects ENABLE ROW LEVEL SECURITY;

-- 1. AVATARS (Public bucket, but only owner can upload/delete)
DROP POLICY IF EXISTS "Avatars: Public view" ON storage.objects;
CREATE POLICY "Avatars: Public view" ON storage.objects FOR SELECT USING (bucket_id = 'avatars');

DROP POLICY IF EXISTS "Avatars: Owner upload" ON storage.objects;
CREATE POLICY "Avatars: Owner upload" ON storage.objects FOR INSERT TO authenticated WITH CHECK (bucket_id = 'avatars' AND (storage.foldername(name))[1] = 'members' AND (storage.foldername(name))[2] = auth.uid()::text);

DROP POLICY IF EXISTS "Avatars: Owner delete" ON storage.objects;
CREATE POLICY "Avatars: Owner delete" ON storage.objects FOR DELETE TO authenticated USING (bucket_id = 'avatars' AND (storage.foldername(name))[1] = 'members' AND (storage.foldername(name))[2] = auth.uid()::text);


-- 2. CONSTITUTIONS (Public view, Admin can upload)
DROP POLICY IF EXISTS "Constitutions: Public view" ON storage.objects;
CREATE POLICY "Constitutions: Public view" ON storage.objects FOR SELECT USING (bucket_id = 'constitutions');

DROP POLICY IF EXISTS "Constitutions: Admin upload" ON storage.objects;
CREATE POLICY "Constitutions: Admin upload" ON storage.objects FOR INSERT TO authenticated WITH CHECK (
    bucket_id = 'constitutions' AND
    public.is_group_admin((storage.foldername(name))[1]::uuid)
);


-- 3. DOCUMENTS (Private bucket, Member/Admin access)
-- Path format: members/{memberId}/doc_{index}_{timestamp}.ext
DROP POLICY IF EXISTS "Documents: Member/Admin view" ON storage.objects;
CREATE POLICY "Documents: Member/Admin view" ON storage.objects FOR SELECT TO authenticated USING (
    bucket_id = 'documents' AND (
        (storage.foldername(name))[1] = 'members' AND (storage.foldername(name))[2] = auth.uid()::text OR
        (storage.foldername(name))[1] = 'members' AND public.is_group_admin((SELECT group_id FROM public.members WHERE id = (storage.foldername(name))[2])) OR
        public.is_platform_admin()
    )
);

DROP POLICY IF EXISTS "Documents: Member upload" ON storage.objects;
CREATE POLICY "Documents: Member upload" ON storage.objects FOR INSERT TO authenticated WITH CHECK (
    bucket_id = 'documents' AND (storage.foldername(name))[1] = 'members' AND (storage.foldername(name))[2] = auth.uid()::text
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
    bucket_id = 'loan_contracts' AND
    public.is_group_admin((storage.foldername(name))[2]::uuid)
);

-- 5. PLATFORM ADMIN OVERRIDE
DROP POLICY IF EXISTS "Storage: Platform Admin All" ON storage.objects;
CREATE POLICY "Storage: Platform Admin All" ON storage.objects FOR ALL TO authenticated USING (public.is_platform_admin());
