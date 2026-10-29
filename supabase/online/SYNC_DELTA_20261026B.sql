-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave - DELTA 2026-10-26B  (add the four document-status columns to
-- public.beneficiary_payout_claims)
-- Target: Supabase Cloud (Dashboard SQL editor) and the Local tier.
-- Hand-maintained. SYNC_DELTA_20261014.sql, SYNC_DELTA_20261021.sql,
-- SYNC_DELTA_20261022.sql and SYNC_DELTA_20261026.sql are applied history -
-- do not re-run or edit them.
-- ─────────────────────────────────────────────────────────────────────────────
-- WHAT THIS DOES
--   N.1  adds four TEXT NOT NULL DEFAULT 'pending' columns, each with a CHECK
--        over ('pending','verified','rejected'), to
--        public.beneficiary_payout_claims:
--          face_photo_status, beneficiary_id_status,
--          death_certificate_status, member_id_status
--        and then asks PostgREST to reload its schema cache.
--
-- WHAT THIS DOES NOT DO
--   No data rewrite (existing rows take the column default), no signature or
--   body change to any function, no DROP, no GRANT/REVOKE, no RLS change,
--   no index. The claim status enum the app serialises is lowercase
--   (DocumentStatus @SerialName pending/verified/rejected), matching the CHECK.
--
-- WHY (the defect)
--   Commit 9215bf7 (#9) added the document-verification fields to the domain
--   model (ClaimModels.kt), the Room cache (BeneficiaryClaimEntities.kt,
--   migration V81->V82) and the remote column list
--   (SupabaseClaimDataSourceImpl.CLAIM_COLUMNS_PRIMARY), but the server-side
--   schema never got the columns. Every PostgREST select that names them -
--   getEscalatedClaims, getClaimsForGroup, getClaimsForMember - answers 400
--   "column ... does not exist", so the Platform Admin Payments tab shows
--   "Some disbursement data could not be refreshed." and the outbox replay of
--   claim writes fails the same way. supabase/01_DATABASE_SCHEMA.sql carries
--   the columns now; this delta brings the existing tiers in line.
--
-- ORDER OF WORK
--   1. Run N.0 alone. It writes nothing. Expect present=0 of 4 on a tier that
--      needs this delta. If present=4 of 4, this delta already ran there -
--      skip to N.2. If present is 1-3, the tier is half-migrated: stop and
--      report which columns are missing before running anything else.
--   2. Run N.1. It is idempotent: each column is added only when absent, each
--      CHECK only when its column exists and the constraint does not.
--   3. Run N.2. Expect exactly four rows, all is_nullable=NO,
--      column_default='pending'::text, checked=YES, and the row count from
--      N.0.b unchanged.
--
-- TIER NOTES
--   - Neither tier has these columns as of 2026-10-26 (Local measured via
--     information_schema; the app reproduces the 400 against Cloud).
--   - Do NOT reach either tier by re-applying supabase/01_DATABASE_SCHEMA.sql
--     or by running the generators: they drop and recreate tables.

-- ─────────────────────────────────────────────────────────────────────────────
-- N.0  PRE-FLIGHT - READ ONLY.  Run this first, on its own.
-- ─────────────────────────────────────────────────────────────────────────────

-- N.0.a  Which of the four columns already exist? Expect 0 rows on a tier
--        that needs this delta; 4 rows mean it already ran.
SELECT c.column_name, c.is_nullable, c.column_default
  FROM information_schema.columns c
 WHERE c.table_schema = 'public'
   AND c.table_name   = 'beneficiary_payout_claims'
   AND c.column_name IN ('face_photo_status','beneficiary_id_status',
                         'death_certificate_status','member_id_status')
 ORDER BY 1;

-- N.0.b  Row count baseline for N.2 to compare against. Informational.
SELECT count(*) AS claims_rows FROM public.beneficiary_payout_claims;

-- ─────────────────────────────────────────────────────────────────────────────
-- N.1  THE COLUMNS.  Idempotent. Adds each missing column with its CHECK, and
--      repairs a column-present-but-constraint-absent half state.
-- ─────────────────────────────────────────────────────────────────────────────

DO $$
DECLARE
    c          TEXT;
    con_name   TEXT;
BEGIN
    FOREACH c IN ARRAY ARRAY[
        'face_photo_status',
        'beneficiary_id_status',
        'death_certificate_status',
        'member_id_status'
    ] LOOP
        con_name := 'beneficiary_payout_claims_' || c || '_check';

        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns
             WHERE table_schema = 'public'
               AND table_name   = 'beneficiary_payout_claims'
               AND column_name  = c
        ) THEN
            EXECUTE format(
                'ALTER TABLE public.beneficiary_payout_claims ADD COLUMN %I TEXT NOT NULL DEFAULT %L CONSTRAINT %I CHECK (%I IN (%L, %L, %L))',
                c, 'pending', con_name, c, 'pending', 'verified', 'rejected');
        ELSIF NOT EXISTS (
            SELECT 1 FROM pg_constraint
             WHERE conrelid = 'public.beneficiary_payout_claims'::regclass
               AND conname  = con_name
        ) THEN
            EXECUTE format(
                'ALTER TABLE public.beneficiary_payout_claims ADD CONSTRAINT %I CHECK (%I IN (%L, %L, %L))',
                con_name, c, 'pending', 'verified', 'rejected');
        END IF;
    END LOOP;
END
$$;

-- Let PostgREST pick the new columns up without a restart.
NOTIFY pgrst, 'reload schema';

-- ─────────────────────────────────────────────────────────────────────────────
-- N.2  VERIFICATION - READ ONLY.  Run after N.1.
-- ─────────────────────────────────────────────────────────────────────────────

-- N.2.a  Expect exactly four rows: is_nullable=NO,
--        column_default='pending'::text, checked=YES.
SELECT c.column_name, c.is_nullable, c.column_default,
       CASE WHEN EXISTS (
           SELECT 1 FROM pg_constraint k
            WHERE k.conrelid = 'public.beneficiary_payout_claims'::regclass
              AND k.conname  = 'beneficiary_payout_claims_' || c.column_name || '_check'
       ) THEN 'YES' ELSE 'NO' END AS checked
  FROM information_schema.columns c
 WHERE c.table_schema = 'public'
   AND c.table_name   = 'beneficiary_payout_claims'
   AND c.column_name IN ('face_photo_status','beneficiary_id_status',
                         'death_certificate_status','member_id_status')
 ORDER BY 1;

-- N.2.b  No claim row may have been lost or rewritten. Expect the N.0.b count.
SELECT count(*) AS claims_rows FROM public.beneficiary_payout_claims;

-- N.2.c  The exact column list the app selects must now resolve. Expect one row
--        per claim with all four statuses defaulting to 'pending'.
SELECT id, face_photo_status, beneficiary_id_status,
       death_certificate_status, member_id_status
  FROM public.beneficiary_payout_claims
 ORDER BY created_at DESC
 LIMIT 3;
