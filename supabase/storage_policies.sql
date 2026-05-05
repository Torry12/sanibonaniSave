-- ============================================================================
-- STORAGE BUCKET CONFIGURATION & RLS POLICIES
-- Version: 3.0 (Updated April 29, 2026)
-- ============================================================================

-- 1. Create the storage buckets if they don't exist
INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES
    ('avatars', 'avatars', true, 3145728, '{image/*}'),
    ('documents', 'documents', false, 3145728, '{image/*,application/pdf}'),
    ('constitutions', 'constitutions', true, 5242880, '{application/pdf}')
ON CONFLICT (id) DO UPDATE SET
    public = EXCLUDED.public,
    file_size_limit = EXCLUDED.file_size_limit,
    allowed_mime_types = EXCLUDED.allowed_mime_types;

-- 2. Drop existing policies
DROP POLICY IF EXISTS "Platform Admins can do everything" ON storage.objects;
DROP POLICY IF EXISTS "Members can upload own documents" ON storage.objects;
DROP POLICY IF EXISTS "Authorized view of documents" ON storage.objects;
DROP POLICY IF EXISTS "Members can delete own documents" ON storage.objects;
DROP POLICY IF EXISTS "Members can upload avatars" ON storage.objects;
DROP POLICY IF EXISTS "Authenticated users can view avatars" ON storage.objects;
DROP POLICY IF EXISTS "Admins can upload constitutions" ON storage.objects;
DROP POLICY IF EXISTS "Authenticated users can view constitutions" ON storage.objects;

-- 3. PLATFORM ADMIN OVERRIDE
CREATE POLICY "Platform Admins can do everything"
ON storage.objects FOR ALL
TO authenticated
USING (
    EXISTS (
        SELECT 1 FROM public.profiles
        WHERE id = auth.uid() AND role = 'platform_admin'
    )
);

-- 4. DOCUMENTS BUCKET (Private)
-- Path: members/{memberId}/doc_{index}_{timestamp}.{ext}
-- Path: beneficiaries/{memberId}/{beneficiaryId}_{timestamp}.{ext}

CREATE POLICY "Members can upload own documents"
ON storage.objects FOR INSERT
TO authenticated
WITH CHECK (
    bucket_id = 'documents' AND
    (
        -- Member documents
        ((storage.foldername(name))[1] = 'members' AND EXISTS (
            SELECT 1 FROM public.members m WHERE m.user_id = auth.uid() AND m.id::text = (storage.foldername(name))[2]
        ))
        OR
        -- Beneficiary documents (uploaded by member)
        ((storage.foldername(name))[1] = 'beneficiaries' AND EXISTS (
            SELECT 1 FROM public.members m WHERE m.user_id = auth.uid() AND m.id::text = (storage.foldername(name))[2]
        ))
    )
);

CREATE POLICY "Authorized view of documents"
ON storage.objects FOR SELECT
TO authenticated
USING (
    bucket_id = 'documents' AND
    (
        -- Own documents
        EXISTS (
            SELECT 1 FROM public.members m
            WHERE m.user_id = auth.uid()
            AND m.id::text = (storage.foldername(name))[2]
        )
        OR
        -- Group Admin viewing member documents
        EXISTS (
            SELECT 1 FROM public.members m
            JOIN public.groups g ON g.id = m.group_id
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
        SELECT 1 FROM public.members m
        WHERE m.user_id = auth.uid()
        AND m.id::text = (storage.foldername(name))[2]
    )
);

-- 5. AVATARS BUCKET (Public)
CREATE POLICY "Members can upload avatars"
ON storage.objects FOR INSERT
TO authenticated
WITH CHECK (
    bucket_id = 'avatars' AND
    (storage.foldername(name))[1] = 'members' AND
    EXISTS (
        SELECT 1 FROM public.members m WHERE m.user_id = auth.uid() AND m.id::text = (storage.foldername(name))[2]
    )
);

CREATE POLICY "Authenticated users can view avatars"
ON storage.objects FOR SELECT
TO authenticated
USING (bucket_id = 'avatars');

-- 6. CONSTITUTIONS BUCKET (Public)
CREATE POLICY "Admins can upload constitutions"
ON storage.objects FOR INSERT
TO authenticated
WITH CHECK (
    bucket_id = 'constitutions' AND
    EXISTS (
        SELECT 1 FROM public.groups g
        WHERE g.admin_user_id = auth.uid()
        AND g.id::text = (storage.foldername(name))[1]
    )
);

CREATE POLICY "Authenticated users can view constitutions"
ON storage.objects FOR SELECT
TO authenticated
USING (bucket_id = 'constitutions');
