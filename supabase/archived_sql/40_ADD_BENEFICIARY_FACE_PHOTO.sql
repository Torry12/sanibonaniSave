-- ─────────────────────────────────────────────────────────────────────────────
-- MIGRATION: BENEFICIARY FACIAL IMAGE SUPPORT
-- ─────────────────────────────────────────────────────────────────────────────
-- Adds support for storing facial images of beneficiaries to enhance verification.
-- Updates storage policies to allow members to upload documents to the
-- 'beneficiaries/' folder within the 'documents' bucket.

-- 1. EXTEND BENEFICIARIES TABLE
-- Add face_photo_url to store the link to the uploaded image in storage.
ALTER TABLE public.beneficiaries
ADD COLUMN IF NOT EXISTS face_photo_url TEXT;

-- 2. EXTEND BENEFICIARY PAYOUT CLAIMS TABLE
-- Add face_photo_url to record a snapshot of the facial image at the time of claim.
ALTER TABLE public.beneficiary_payout_claims
ADD COLUMN IF NOT EXISTS face_photo_url TEXT;

-- 3. UPDATE STORAGE POLICIES
-- The existing policies for the 'documents' bucket only permitted paths starting with 'members/'.
-- We need to explicitly allow 'beneficiaries/' paths for both uploads and viewing.

-- Allow authenticated members to upload to the beneficiaries folder.
-- Note: Logic in the app uses 'beneficiaries/{member_id}/{beneficiary_id}_{timestamp}.ext'
DROP POLICY IF EXISTS "Storage: Member Upload Beneficiary Docs" ON storage.objects;
CREATE POLICY "Storage: Member Upload Beneficiary Docs" ON storage.objects
FOR INSERT TO authenticated
WITH CHECK (
    bucket_id = 'documents' AND
    (storage.foldername(name))[1] = 'beneficiaries'
);

-- Allow authenticated members to view beneficiary documents.
DROP POLICY IF EXISTS "Storage: Member View Beneficiary Docs" ON storage.objects;
CREATE POLICY "Storage: Member View Beneficiary Docs" ON storage.objects
FOR SELECT TO authenticated
USING (
    bucket_id = 'documents' AND
    (storage.foldername(name))[1] = 'beneficiaries'
);

-- NOTE: The existing "Storage: Admin View Group Docs" policy already allows SELECT on
-- all files in the 'documents' bucket, so admins can already view these new files.

-- 3. ENSURE UPDATED_AT TRIGGER (Optional but recommended for consistency)
-- If the trigger doesn't exist, this ensures updated_at is refreshed on change.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'set_beneficiaries_updated_at') THEN
        CREATE TRIGGER set_beneficiaries_updated_at
        BEFORE UPDATE ON public.beneficiaries
        FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();
    END IF;
END $$;

COMMENT ON COLUMN public.beneficiaries.face_photo_url IS 'URL to the facial photo of the beneficiary for identity verification.';
COMMENT ON COLUMN public.beneficiary_payout_claims.face_photo_url IS 'Snapshot of the beneficiary facial photo at the time the claim was submitted.';
