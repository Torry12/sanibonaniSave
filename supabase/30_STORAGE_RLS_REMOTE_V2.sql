-- Storage Policies for Remote Application (Version 1.4)
-- Avoids ownership-restricted commands and complex DO blocks

-- 1. Helper Functions
CREATE OR REPLACE FUNCTION public.storage_member_belongs_to_user(p_path text)
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_member_id_text text;
BEGIN
    v_member_id_text := (storage.foldername(p_path))[2];
    IF v_member_id_text IS NULL THEN RETURN false; END IF;

    RETURN EXISTS (
        SELECT 1 FROM public.members
        WHERE id::text = v_member_id_text
          AND user_id = auth.uid()
    );
END;
$$;

CREATE OR REPLACE FUNCTION public.storage_user_is_member_group_admin(p_path text)
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_member_id_text text;
BEGIN
    v_member_id_text := (storage.foldername(p_path))[2];
    IF v_member_id_text IS NULL THEN RETURN false; END IF;

    RETURN EXISTS (
        SELECT 1 FROM public.groups g
        JOIN public.members m ON g.id = m.group_id
        WHERE m.id::text = v_member_id_text
          AND g.admin_user_id = auth.uid()
    );
END;
$$;

-- 2. Apply Policies
-- AVATARS
DROP POLICY IF EXISTS "Avatars: Public view" ON storage.objects;
CREATE POLICY "Avatars: Public view" ON storage.objects FOR SELECT USING (bucket_id = 'avatars');

DROP POLICY IF EXISTS "Avatars: Authority upload" ON storage.objects;
CREATE POLICY "Avatars: Authority upload" ON storage.objects FOR INSERT TO authenticated WITH CHECK (
    bucket_id = 'avatars' AND
    (storage.foldername(name))[1] = 'members' AND
    (public.storage_member_belongs_to_user(name) OR (auth.jwt() ->> 'email') = 'torrymsimango@gmail.com')
);

DROP POLICY IF EXISTS "Avatars: Authority update" ON storage.objects;
CREATE POLICY "Avatars: Authority update" ON storage.objects FOR UPDATE TO authenticated USING (
    bucket_id = 'avatars' AND
    (storage.foldername(name))[1] = 'members' AND
    (public.storage_member_belongs_to_user(name) OR (auth.jwt() ->> 'email') = 'torrymsimango@gmail.com')
);

-- DOCUMENTS
DROP POLICY IF EXISTS "Documents: Authority view" ON storage.objects;
CREATE POLICY "Documents: Authority view" ON storage.objects FOR SELECT TO authenticated USING (
    bucket_id = 'documents' AND (
        (storage.foldername(name))[1] = 'members' AND (
            public.storage_member_belongs_to_user(name) OR
            public.storage_user_is_member_group_admin(name)
        ) OR
        (auth.jwt() ->> 'email') = 'torrymsimango@gmail.com'
    )
);

DROP POLICY IF EXISTS "Documents: Authority upload" ON storage.objects;
CREATE POLICY "Documents: Authority upload" ON storage.objects FOR INSERT TO authenticated WITH CHECK (
    bucket_id = 'documents' AND
    (storage.foldername(name))[1] = 'members' AND
    (public.storage_member_belongs_to_user(name) OR (auth.jwt() ->> 'email') = 'torrymsimango@gmail.com')
);

-- CONSTITUTIONS
DROP POLICY IF EXISTS "Constitutions: Admin upload" ON storage.objects;
CREATE POLICY "Constitutions: Admin upload" ON storage.objects FOR INSERT TO authenticated WITH CHECK (
    bucket_id = 'constitutions' AND (
        public.is_group_admin((storage.foldername(name))[1]::uuid) OR
        (auth.jwt() ->> 'email') = 'torrymsimango@gmail.com'
    )
);

-- CATCH-ALL FOR PLATFORM ADMIN
DROP POLICY IF EXISTS "Storage: Platform Admin All" ON storage.objects;
CREATE POLICY "Storage: Platform Admin All" ON storage.objects FOR ALL TO authenticated USING (
   (auth.jwt() ->> 'email') = 'torrymsimango@gmail.com'
);
