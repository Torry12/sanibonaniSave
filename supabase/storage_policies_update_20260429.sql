-- storage_policies_update_20260429.sql
-- Fixes typo in constitutions bucket policy (bucket_id = 'constitutions')

-- Update policy for constitutions bucket (Admins can upload constitutions)
DROP POLICY IF EXISTS "Admins can upload constitutions" ON storage.objects;

CREATE POLICY "Admins can upload constitutions"
ON storage.objects FOR INSERT
TO authenticated
WITH CHECK (
    bucket_id = 'constitutions' AND
    EXISTS (
        SELECT 1 FROM groups g
        WHERE g.admin_user_id = auth.uid()
        AND g.id::text = (storage.foldername(name))[1]
    )
);

