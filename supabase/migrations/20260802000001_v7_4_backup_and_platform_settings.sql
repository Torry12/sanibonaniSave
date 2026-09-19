-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — BACKUP SYSTEM & PLATFORM SETTINGS
-- Version: 1.0 (August 2, 2026)
-- ─────────────────────────────────────────────────────────────────────────────

-- 1. BACKUPS STORAGE BUCKET (From 42_BACKUP_SYSTEM_SETUP.sql)
INSERT INTO storage.buckets (id, name, public)
VALUES ('backups', 'backups', false)
ON CONFLICT (id) DO NOTHING;

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

GRANT ALL ON TABLE storage.objects TO service_role;
GRANT ALL ON TABLE storage.buckets TO service_role;


-- 2. PLATFORM SETTINGS (From 45_PLATFORM_ACCOUNT_PROVISIONING.sql)
INSERT INTO public.platform_settings (key, value)
VALUES
    ('platform_bank_name_code', 1),
    ('platform_account_number', 62889900112),
    ('platform_branch_code', 250655)
ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value;


-- 3. STORAGE RLS HARDENING (From 30_STORAGE_RLS.sql patches)
-- Ensure platform admins can always bypass storage RLS
CREATE POLICY "Storage: Platform Admin All" ON storage.objects
FOR ALL TO authenticated
USING (public.is_platform_admin())
WITH CHECK (public.is_platform_admin());
