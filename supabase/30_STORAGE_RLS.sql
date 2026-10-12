-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — STORAGE BUCKETS & RLS POLICIES (Master)
-- Version: 2.1 (Full Admin Visibility & Parity - August 2026)
-- ─────────────────────────────────────────────────────────────────────────────

-- 1. PROVISION BUCKETS
-- Ensuring all critical buckets exist for the platform.
INSERT INTO storage.buckets (id, name, public)
VALUES
    ('avatars', 'avatars', true),
    ('constitutions', 'constitutions', true),
    ('documents', 'documents', false),
    ('loan_contracts', 'loan_contracts', false),
    ('backups', 'backups', false)
ON CONFLICT (id) DO NOTHING;

-- 2. ENABLE RLS
-- We attempt to enable RLS; failure is ignored if already enabled or permission restricted.
-- ALTER TABLE storage.buckets ENABLE ROW LEVEL SECURITY;
-- ALTER TABLE storage.objects ENABLE ROW LEVEL SECURITY;

-- 3. BUCKET POLICIES (Visibility)
-- Authenticated users need to see buckets to interact with them.
DROP POLICY IF EXISTS "Buckets: Authenticated view" ON storage.buckets;
CREATE POLICY "Buckets: Authenticated view" ON storage.buckets FOR SELECT TO authenticated USING (TRUE);

-- 4. OBJECT POLICIES (Standard Roles)

-- Avatars (Public Read, Authority Manage)
DROP POLICY IF EXISTS "Avatars: Public view" ON storage.objects;
CREATE POLICY "Avatars: Public view" ON storage.objects FOR SELECT USING (bucket_id = 'avatars');

DROP POLICY IF EXISTS "Avatars: Authority manage" ON storage.objects;
CREATE POLICY "Avatars: Authority manage" ON storage.objects FOR ALL TO authenticated USING (
    bucket_id = 'avatars' AND public.can_access_member_storage(name)
);

-- Documents (Private, Authority Manage)
DROP POLICY IF EXISTS "Documents: Authority manage" ON storage.objects;
CREATE POLICY "Documents: Authority manage" ON storage.objects FOR ALL TO authenticated USING (
    bucket_id = 'documents' AND public.can_access_member_storage(name)
);

-- Constitutions (Public Read, Admin Manage)
DROP POLICY IF EXISTS "Constitutions: Public view" ON storage.objects;
CREATE POLICY "Constitutions: Public view" ON storage.objects FOR SELECT USING (bucket_id = 'constitutions');

DROP POLICY IF EXISTS "Constitutions: Admin manage" ON storage.objects;
CREATE POLICY "Constitutions: Admin manage" ON storage.objects FOR ALL TO authenticated USING (
    bucket_id = 'constitutions' AND (
        public.is_group_admin((storage.foldername(name))[1]::uuid) OR
        public.is_platform_admin()
    )
);

-- Loan Contracts (Private, Admin/Member Manage)
DROP POLICY IF EXISTS "Loan Contracts: Member/Admin view" ON storage.objects;
CREATE POLICY "Loan Contracts: Member/Admin view" ON storage.objects FOR SELECT TO authenticated USING (
    bucket_id = 'loan_contracts' AND (
        public.is_group_admin((storage.foldername(name))[2]::uuid) OR
        public.is_group_member((storage.foldername(name))[2]::uuid) OR
        public.is_platform_admin()
    )
);

-- 5. UNIVERSAL PLATFORM ADMIN BYPASS (The Gold Standard)
-- Platform Admin can do anything in any bucket.
DROP POLICY IF EXISTS "Storage: Platform Admin Buckets" ON storage.buckets;
CREATE POLICY "Storage: Platform Admin Buckets" ON storage.buckets FOR ALL TO authenticated USING (public.is_platform_admin());

DROP POLICY IF EXISTS "Storage: Platform Admin All" ON storage.objects;
CREATE POLICY "Storage: Platform Admin All" ON storage.objects FOR ALL TO authenticated USING (public.is_platform_admin());
