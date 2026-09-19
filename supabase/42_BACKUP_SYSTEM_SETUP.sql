-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — BACKUP SYSTEM SETUP
-- Version: 1.0 (Cloud Snapshots & Storage)
-- ─────────────────────────────────────────────────────────────────────────────

-- 1. BACKUPS STORAGE BUCKET
INSERT INTO storage.buckets (id, name, public)
VALUES ('backups', 'backups', false)
ON CONFLICT (id) DO NOTHING;

-- 2. STORAGE POLICIES (Platform Admin Only)
-- Only users with the 'platform_admin' role can manage backups.
-- Note: 'platform_admin' is checked via the profiles table or JWT metadata.

CREATE POLICY "Platform Admins can manage backups"
ON storage.objects FOR ALL
TO authenticated
USING (
  bucket_id = 'backups' AND (
    (SELECT role FROM public.profiles WHERE id = auth.uid()) = 'platform_admin'
  )
)
WITH CHECK (
  bucket_id = 'backups' AND (
    (SELECT role FROM public.profiles WHERE id = auth.uid()) = 'platform_admin'
  )
);

-- 3. EDGE FUNCTION PERMISSIONS
-- Ensure the service_role can always manage this bucket (implicitly true, but good practice).
GRANT ALL ON TABLE storage.objects TO service_role;
GRANT ALL ON TABLE storage.buckets TO service_role;
