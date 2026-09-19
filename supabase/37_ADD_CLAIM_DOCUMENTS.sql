-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — ADD CLAIM DOCUMENT URLS
-- Version: 1.0 (July 2026)
-- ─────────────────────────────────────────────────────────────────────────────

ALTER TABLE public.beneficiary_payout_claims
ADD COLUMN IF NOT EXISTS beneficiary_id_url TEXT,
ADD COLUMN IF NOT EXISTS death_certificate_url TEXT,
ADD COLUMN IF NOT EXISTS member_id_url TEXT;

COMMENT ON COLUMN public.beneficiary_payout_claims.beneficiary_id_url IS 'URL to the scanned ID document of the beneficiary.';
COMMENT ON COLUMN public.beneficiary_payout_claims.death_certificate_url IS 'URL to the death certificate of the deceased beneficiary.';
COMMENT ON COLUMN public.beneficiary_payout_claims.member_id_url IS 'URL to the scanned ID document of the group member.';
