-- ============================================================================
-- STORAGE BUCKET CONFIGURATION & RLS POLICIES (Simplified Version)
-- Use this if you get "must be owner of table" errors.
-- ============================================================================

-- 1. Create the storage buckets if they don't exist
INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES
    ('avatars', 'avatars', false, 3145728, '{image/*}'),
    ('documents', 'documents', false, 3145728, '{image/*,application/pdf}'),
    ('constitutions', 'constitutions', true, 5242880, '{application/pdf}')
ON CONFLICT (id) DO UPDATE SET
    public = EXCLUDED.public,
    file_size_limit = EXCLUDED.file_size_limit,
    allowed_mime_types = EXCLUDED.allowed_mime_types;

-- 2. Drop existing policies individually to avoid permission issues with system metadata queries
DROP POLICY IF EXISTS "Platform Admins can do everything" ON storage.objects;
DROP POLICY IF EXISTS "Members can upload own documents" ON storage.objects;
DROP POLICY IF EXISTS "Authorized view of documents" ON storage.objects;
DROP POLICY IF EXISTS "Members can delete own documents" ON storage.objects;
DROP POLICY IF EXISTS "Members can upload avatars" ON storage.objects;
DROP POLICY IF EXISTS "Authenticated users can view avatars" ON storage.objects;
DROP POLICY IF EXISTS "Admins can upload constitutions" ON storage.objects;
DROP POLICY IF EXISTS "Authenticated users can view constitutions" ON storage.objects;

-- ============================================================================
-- 3. PLATFORM ADMIN OVERRIDE
-- ============================================================================

CREATE POLICY "Platform Admins can do everything"
ON storage.objects FOR ALL
TO authenticated
USING (
    EXISTS (
        SELECT 1 FROM public.profiles
        WHERE id = auth.uid() AND role IN ('platform_admin', 'group_admin')
    )
);

-- ============================================================================
-- 4. DOCUMENTS BUCKET (Private)
-- ============================================================================

CREATE POLICY "Members can upload own documents"
ON storage.objects FOR INSERT
TO authenticated
WITH CHECK (
    bucket_id = 'documents' AND
    (
        ((storage.foldername(name))[1] = 'members' AND EXISTS (
            SELECT 1 FROM members m WHERE m.user_id = auth.uid() AND m.id::text = (storage.foldername(name))[2]
        ))
        OR
        ((storage.foldername(name))[1] = 'beneficiaries' AND EXISTS (
            SELECT 1 FROM members m WHERE m.user_id = auth.uid() AND m.id::text = (storage.foldername(name))[2]
        ))
    )
);

CREATE POLICY "Authorized view of documents"
ON storage.objects FOR SELECT
TO authenticated
USING (
    bucket_id = 'documents' AND
    (
        EXISTS (
            SELECT 1 FROM members m
            WHERE m.user_id = auth.uid()
            AND m.id::text = (storage.foldername(name))[2]
        )
        OR
        EXISTS (
            SELECT 1 FROM members m
            JOIN groups g ON g.id = m.group_id
            WHERE g.admin_user_id = auth.uid()
            AND m.id::text = (storage.foldername(name))[2]
        )
    )
);

CREATE POLICY "Members can delete own documents"
ON storage.objects FOR DELETE
TO authenticated
USING (
    bucket_id = 'documents' AND
    EXISTS (
        SELECT 1 FROM members m
        WHERE m.user_id = auth.uid()
        AND m.id::text = (storage.foldername(name))[2]
    )
);

-- ============================================================================
-- 5. AVATARS BUCKET (Public)
-- ============================================================================

CREATE POLICY "Members can upload avatars"
ON storage.objects FOR INSERT
TO authenticated
WITH CHECK (
    bucket_id = 'avatars' AND
    (storage.foldername(name))[1] = 'members' AND
    EXISTS (
        SELECT 1 FROM members m WHERE m.user_id = auth.uid() AND m.id::text = (storage.foldername(name))[2]
    )
);

CREATE POLICY "Authenticated users can view avatars"
ON storage.objects FOR SELECT
TO authenticated
USING (bucket_id = 'avatars');

-- ============================================================================
-- 6. CONSTITUTIONS BUCKET (Public)
-- ============================================================================

CREATE POLICY "Admins can upload constitutions"
ON storage.objects FOR INSERT
TO authenticated
WITH CHECK (
    bucket_id = 'constitutions
    ' AND
    EXISTS (
        SELECT 1 FROM groups g
        WHERE g.admin_user_id = auth.uid()
        AND g.id::text = (storage.foldername(name))[1]
    )
);

CREATE POLICY "Authenticated users can view constitutions"
ON storage.objects FOR SELECT
TO authenticated
USING (bucket_id = 'constitutions');
