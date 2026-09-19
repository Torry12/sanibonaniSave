-- Robust Storage Policies (Remote Applied Version 1.6)
-- Optimized for remote Management API

-- 1. AVATARS (Public bucket)
DROP POLICY IF EXISTS "Avatars: Public view" ON storage.objects;
CREATE POLICY "Avatars: Public view" ON storage.objects FOR SELECT USING (bucket_id = 'avatars');

DROP POLICY IF EXISTS "Avatars: Authority upload" ON storage.objects;
CREATE POLICY "Avatars: Authority upload" ON storage.objects FOR INSERT TO authenticated WITH CHECK (
    bucket_id = 'avatars' AND
    (storage.foldername(name))[1] = 'members' AND (
        EXISTS (SELECT 1 FROM public.members WHERE id::text = (storage.foldername(name))[2] AND user_id = auth.uid()) OR
        EXISTS (SELECT 1 FROM public.groups g JOIN public.members m ON g.id = m.group_id WHERE m.id::text = (storage.foldername(name))[2] AND g.admin_user_id = auth.uid()) OR
        (auth.jwt() ->> 'email') = 'torrymsimango@gmail.com'
    )
);

DROP POLICY IF EXISTS "Avatars: Authority update" ON storage.objects;
CREATE POLICY "Avatars: Authority update" ON storage.objects FOR UPDATE TO authenticated USING (
    bucket_id = 'avatars' AND
    (storage.foldername(name))[1] = 'members' AND (
        EXISTS (SELECT 1 FROM public.members WHERE id::text = (storage.foldername(name))[2] AND user_id = auth.uid()) OR
        EXISTS (SELECT 1 FROM public.groups g JOIN public.members m ON g.id = m.group_id WHERE m.id::text = (storage.foldername(name))[2] AND g.admin_user_id = auth.uid()) OR
        (auth.jwt() ->> 'email') = 'torrymsimango@gmail.com'
    )
);

-- 2. DOCUMENTS (Private bucket)
DROP POLICY IF EXISTS "Documents: Authority view" ON storage.objects;
CREATE POLICY "Documents: Authority view" ON storage.objects FOR SELECT TO authenticated USING (
    bucket_id = 'documents' AND (
        (storage.foldername(name))[1] = 'members' AND (
            EXISTS (SELECT 1 FROM public.members WHERE id::text = (storage.foldername(name))[2] AND user_id = auth.uid()) OR
            EXISTS (SELECT 1 FROM public.groups g JOIN public.members m ON g.id = m.group_id WHERE m.id::text = (storage.foldername(name))[2] AND g.admin_user_id = auth.uid())
        ) OR
        (auth.jwt() ->> 'email') = 'torrymsimango@gmail.com'
    )
);

DROP POLICY IF EXISTS "Documents: Authority upload" ON storage.objects;
CREATE POLICY "Documents: Authority upload" ON storage.objects FOR INSERT TO authenticated WITH CHECK (
    bucket_id = 'documents' AND
    (storage.foldername(name))[1] = 'members' AND (
        EXISTS (SELECT 1 FROM public.members WHERE id::text = (storage.foldername(name))[2] AND user_id = auth.uid()) OR
        EXISTS (SELECT 1 FROM public.groups g JOIN public.members m ON g.id = m.group_id WHERE m.id::text = (storage.foldername(name))[2] AND g.admin_user_id = auth.uid()) OR
        (auth.jwt() ->> 'email') = 'torrymsimango@gmail.com'
    )
);

-- 3. CATCH-ALL
DROP POLICY IF EXISTS "Storage: Platform Admin All" ON storage.objects;
CREATE POLICY "Storage: Platform Admin All" ON storage.objects FOR ALL TO authenticated USING (
   (auth.jwt() ->> 'email') = 'torrymsimango@gmail.com'
);
