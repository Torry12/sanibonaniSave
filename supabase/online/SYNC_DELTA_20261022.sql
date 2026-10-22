-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave - DELTA 2026-10-22  (NULL group balance, #74)
-- Target: Supabase Cloud (Dashboard SQL editor) and, unchanged, the Local tier.
-- Hand-maintained. SYNC_DELTA_20261014.sql and SYNC_DELTA_20261021.sql are applied
-- history - do not re-run them and do not edit them to describe these changes.
-- ─────────────────────────────────────────────────────────────────────────────
-- WHAT THIS DOES
--   L.1  repairs any group whose balance is NULL by setting it to the sum of that
--        group's own group_ledger amounts. It invents no number: the ledger is the
--        same source recompute_ledger_running_balances_v2 derives balance_after from,
--        and a group with no ledger rows gets 0 - the value its DEFAULT would have
--        supplied at insert. Every repaired row is returned, so the paste output names
--        exactly what changed.
--   L.2  makes public.groups.balance NOT NULL (a no-op if it already is).
--   L.3  replaces the two spend guards with COALESCE(v_group.balance, 0), so a NULL
--        can never make `balance < amount` evaluate to NULL and skip the refusal.
--        Defence in depth: L.2 removes the NULL from the column, L.3 removes the silent
--        pass even if a future schema change reintroduces nullability or a tier runs
--        L.3 without L.1/L.2.
--
-- WHAT THIS DOES NOT DO
--   No DROP, no DELETE, no GRANT/REVOKE, no signature change, no index, no RLS change.
--   The only data write is the L.1 repair, and it touches only rows whose balance is
--   already NULL. Nothing the app calls changes shape, so no client build changes.
--
-- WHY (the defect)
--   groups.balance was nullable. In approve_and_disburse_loan_v2 and complete_payout_v2
--   the guard is `IF v_group.balance < amount THEN RAISE ...`. With a NULL balance that
--   comparison is NULL, which PL/pgSQL treats as false, so the disbursement proceeded.
--   increment_group_balance then runs `balance = balance + p_amount`, and NULL + amount
--   stays NULL, so the exemption was permanent. 20261021 K.0.e measured the count and
--   listed this under NOT IN THIS BATCH; this file closes it.
--
-- ORDER OF WORK
--   1. Run L.0 alone. It writes nothing. Read L.0.b: it lists every group L.1 would
--      repair, with that group's ledger row count and ledger sum. STOP if a listed group
--      has ledger_rows=0 after months of activity, or if L.0.a reports DRIFT.
--   2. Run L.1, then L.2, then L.3 - in that order, all in one paste is fine.
--   3. Run L.4. Expect groups_null_balance=0, balance_nullable=NO, both guard rows
--      true, both replaced bodies MATCH, and all eight untouched bodies MATCH.
--
-- TIER NOTES
--   - Local measured groups_null_balance=0 on 2026-10-22 (20261021 K.0.e), so L.1 has
--     nothing to repair there. The cloud count is whatever L.0.b reports; this file
--     handles both cases without a separate branch.
--   - Do NOT reach either tier by re-applying supabase/01_DATABASE_SCHEMA.sql: it drops
--     and recreates group_ledger and platform_ledger. Run this delta instead.
--
-- FINGERPRINT METHOD (same as the two previous deltas)
--   md5(btrim(regexp_replace(replace(prosrc, E'\r',''), E'[[:space:]]+',' ', 'g')))
--   over prosrc only. L.0.a's two expected hashes are what the tiers carried after
--   20261021: approve_and_disburse_loan_v2 from that paste, complete_payout_v2 unchanged
--   since the 2026-10-14 delta. If L.0.a instead reports the two post hashes printed in
--   L.4.a, this delta already ran on that tier; skip to L.4.

-- ─────────────────────────────────────────────────────────────────────────────
-- L.0  PRE-FLIGHT - READ ONLY.  Run this first, on its own.
-- ─────────────────────────────────────────────────────────────────────────────

-- L.0.a  The two bodies L.3 replaces must still be the ones the masters held before
--        this delta. Expect both 'MATCH'. DRIFT means the tier holds a hot fix this
--        file does not know about - port it into supabase/02_FUNCTIONS_AND_TRIGGERS.sql
--        before pasting anything. ABSENT means the function is not installed.
WITH expected(name, hash) AS (VALUES
  ('approve_and_disburse_loan_v2', 'e42f0f4def47d261fd1e8c8e03569297'),
  ('complete_payout_v2',           'f60b158bf22bdada3279df5c06f20206')
), actual AS (
  SELECT e.name, e.hash AS want,
         (SELECT md5(btrim(regexp_replace(replace(p.prosrc, E'\r', ''), E'[[:space:]]+', ' ', 'g')))
            FROM pg_proc p JOIN pg_namespace s ON s.oid = p.pronamespace
           WHERE s.nspname = 'public' AND p.proname = e.name) AS got
    FROM expected e
)
SELECT name, COALESCE(got, 'ABSENT') AS installed_body,
       CASE WHEN got IS NULL THEN 'ABSENT' WHEN got = want THEN 'MATCH' ELSE 'DRIFT' END AS verdict
  FROM actual ORDER BY name;

-- L.0.b  The decision point. Expect groups_null_balance=0 on Local. On cloud, read every
--        row below before pasting L.1: ledger_sum is exactly the value L.1 will store.
--        A group with ledger_rows=0 becomes 0, which is what its DEFAULT would have
--        given at insert; a group with rows but an implausible sum is a finding to
--        investigate first, not something a repair should paper over.
SELECT 'groups_null_balance=' || count(*) AS b_null_balance
  FROM public.groups WHERE balance IS NULL;

SELECT g.id, g.name, g.created_at,
       (SELECT count(*) FROM public.group_ledger gl WHERE gl.group_id = g.id) AS ledger_rows,
       COALESCE((SELECT SUM(gl.amount) FROM public.group_ledger gl WHERE gl.group_id = g.id), 0) AS ledger_sum
  FROM public.groups g
 WHERE g.balance IS NULL
 ORDER BY g.created_at, g.id;

-- L.0.c  The column's current nullability and default. Expect balance_nullable=YES and
--        balance_default containing 0. If it is already NO, L.2 will say so and do nothing.
SELECT 'balance_nullable=' || COALESCE((
           SELECT is_nullable FROM information_schema.columns
            WHERE table_schema = 'public' AND table_name = 'groups' AND column_name = 'balance'
       ), 'ABSENT') AS c_balance_nullable,
       'balance_default=' || COALESCE((
           SELECT column_default FROM information_schema.columns
            WHERE table_schema = 'public' AND table_name = 'groups' AND column_name = 'balance'
       ), 'NULL') AS c_balance_default;

-- ─────────────────────────────────────────────────────────────────────────────
-- L.1  REPAIR NULL BALANCES FROM THE LEDGER.  Writes only WHERE balance IS NULL.
--      Returns every repaired row, so the paste output is the audit trail.
-- ─────────────────────────────────────────────────────────────────────────────
WITH repaired AS (
    SELECT g.id,
           COALESCE((SELECT SUM(gl.amount) FROM public.group_ledger gl WHERE gl.group_id = g.id), 0) AS ledger_balance
      FROM public.groups g
     WHERE g.balance IS NULL
)
UPDATE public.groups g
   SET balance = r.ledger_balance,
       updated_at = NOW()
  FROM repaired r
 WHERE g.id = r.id
RETURNING g.id, g.name, r.ledger_balance;

-- ─────────────────────────────────────────────────────────────────────────────
-- L.2  MAKE THE COLUMN NOT NULL.  Idempotent. If L.1 somehow left a NULL, this fails
--      and the paste stops rather than leaving a tier that still has the defect.
-- ─────────────────────────────────────────────────────────────────────────────
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
         WHERE table_schema = 'public' AND table_name = 'groups'
           AND column_name = 'balance' AND is_nullable = 'YES'
    ) THEN
        ALTER TABLE public.groups ALTER COLUMN balance SET NOT NULL;
        RAISE NOTICE 'L.2 groups.balance is now NOT NULL';
    ELSE
        RAISE NOTICE 'L.2 groups.balance was already NOT NULL';
    END IF;
END
$$;

-- ─────────────────────────────────────────────────────────────────────────────
-- L.3  THE TWO SPEND GUARDS
--      Text copied straight from the masters (supabase/02_FUNCTIONS_AND_TRIGGERS.sql),
--      so the tier ends up byte-identical to the repository. Signatures are unchanged;
--      each body carries its own SET search_path. The only change from the 20261021
--      bodies is COALESCE(v_group.balance, 0) in the liquidity comparison.
-- ─────────────────────────────────────────────────────────────────────────────

CREATE OR REPLACE FUNCTION public.approve_and_disburse_loan_v2(
    p_loan_id UUID,
    p_admin_id UUID,
    p_payment_method TEXT DEFAULT 'bank'
) RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_loan RECORD;
    v_m_name TEXT;
    v_group RECORD;
BEGIN
    -- Groups-first, the same order complete_payout_v2 and increment_group_balance use: the status
    -- and liquidity guards below must be evaluated with the lock already held.
    PERFORM id FROM public.groups WHERE id = (SELECT group_id FROM public.loans WHERE id = p_loan_id) FOR UPDATE;
    IF NOT FOUND THEN RAISE EXCEPTION '[SYS_001] LOAN_NOT_FOUND'; END IF;

    SELECT * INTO v_loan FROM public.loans WHERE id = p_loan_id;
    IF v_loan.id IS NULL THEN RAISE EXCEPTION '[SYS_001] LOAN_NOT_FOUND'; END IF;
    IF v_loan.status NOT IN ('pending', 'approved') THEN RAISE EXCEPTION '[FIN_002] INVALID_STATUS: Loan cannot be disbursed from status %', v_loan.status; END IF;

    PERFORM public.assert_mfa_for_disbursement_v1(v_loan.amount);

    SELECT * INTO v_group FROM public.groups WHERE id = v_loan.group_id;
    IF COALESCE(v_group.balance, 0) < v_loan.amount THEN RAISE EXCEPTION '[FIN_001] INSUFFICIENT_LIQUIDITY'; END IF;

    SELECT full_name INTO v_m_name FROM public.members WHERE id = v_loan.member_id;

    UPDATE public.loans SET status = 'active', reviewed_by = p_admin_id, reviewed_at = NOW(), start_date = NOW(), updated_at = NOW() WHERE id = p_loan_id;
    PERFORM public.increment_group_balance(v_loan.group_id, -v_loan.amount, 'Loan disbursement to ' || v_m_name || ' (' || p_payment_method || ')', 'loan_disbursement', p_loan_id::text, v_loan.member_id);

    PERFORM public.log_audit_event('LOAN_DISBURSED', p_admin_id::text, v_loan.member_id, v_loan.group_id, jsonb_build_object('loan_id', p_loan_id, 'amount', v_loan.amount), p_loan_id);
END;
$$;

CREATE OR REPLACE FUNCTION public.complete_payout_v2(
    p_payout_id UUID,
    p_admin_id UUID DEFAULT NULL,
    p_payout_reference TEXT DEFAULT NULL
) RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_payout RECORD;
    v_group RECORD;
    v_eff_admin_id UUID := COALESCE(p_admin_id, auth.uid());
BEGIN
    PERFORM id FROM public.groups WHERE id = (SELECT group_id FROM public.payouts WHERE id = p_payout_id) FOR UPDATE;

    SELECT * INTO v_payout FROM public.payouts WHERE id = p_payout_id;
    IF v_payout.id IS NULL THEN RAISE EXCEPTION '[SYS_001] PAYOUT_NOT_FOUND'; END IF;
    IF v_payout.status NOT IN ('group_approved', 'processing') THEN RAISE EXCEPTION '[FIN_002] BAD_STATUS: Payout in % cannot be completed', v_payout.status; END IF;

    PERFORM public.assert_mfa_for_disbursement_v1(v_payout.amount);

    SELECT * INTO v_group FROM public.groups WHERE id = v_payout.group_id;
    IF COALESCE(v_group.balance, 0) < v_payout.amount THEN RAISE EXCEPTION '[FIN_001] NO_CASH: Group balance insufficient for payout.'; END IF;

    UPDATE public.payouts SET status = 'completed', processed_by = v_eff_admin_id, processed_at = NOW(), payout_reference = COALESCE(p_payout_reference, payout_reference), updated_at = NOW() WHERE id = p_payout_id;
    PERFORM public.increment_group_balance(v_payout.group_id, -v_payout.amount, 'Disbursement Ref: ' || COALESCE(p_payout_reference, p_payout_id::text), 'payout', p_payout_id::text, v_payout.member_id);

    PERFORM public.log_audit_event('PAYOUT_COMPLETED', COALESCE(v_eff_admin_id::text, 'system_admin'), v_payout.member_id, v_payout.group_id, jsonb_build_object('payout_id', p_payout_id, 'amount', v_payout.amount), p_payout_id);
END;
$$;

-- ─────────────────────────────────────────────────────────────────────────────
-- L.4  VERIFICATION - READ ONLY.  Run after L.1-L.3.
-- ─────────────────────────────────────────────────────────────────────────────

-- L.4.a  The two replaced bodies. Expect both 'MATCH'.
WITH expected(name, hash) AS (VALUES
  ('approve_and_disburse_loan_v2', '148f26eafcfa11be50a22c6885674932'),
  ('complete_payout_v2',           'c16b444c02ef5ffab1d860a19da6054d')
), actual AS (
  SELECT e.name, e.hash AS want,
         (SELECT md5(btrim(regexp_replace(replace(p.prosrc, E'\r', ''), E'[[:space:]]+', ' ', 'g')))
            FROM pg_proc p JOIN pg_namespace s ON s.oid = p.pronamespace
           WHERE s.nspname = 'public' AND p.proname = e.name) AS got
    FROM expected e
)
SELECT name, COALESCE(got, 'ABSENT') AS installed_body,
       CASE WHEN got IS NULL THEN 'ABSENT' WHEN got = want THEN 'MATCH' ELSE 'DRIFT' END AS verdict
  FROM actual ORDER BY name;

-- L.4.b  No group may hold a NULL balance and the column may not accept one.
--        Expect groups_null_balance=0 and balance_nullable=NO.
SELECT 'groups_null_balance=' || count(*) AS b_null_balance
  FROM public.groups WHERE balance IS NULL;

SELECT 'balance_nullable=' || COALESCE((
           SELECT is_nullable FROM information_schema.columns
            WHERE table_schema = 'public' AND table_name = 'groups' AND column_name = 'balance'
       ), 'ABSENT') AS b_balance_nullable;

-- L.4.c  Functions that must NOT have moved. Expect all 'MATCH': this delta replaced
--        exactly 2 bodies and touched no other prosrc. The eight below are the same
--        eight 20261021 K.4.d verified as untouched on this tier.
WITH expected(name, hash) AS (VALUES
  ('increment_group_balance',           '0e96e06f47613f191008e7df6ef47550'),
  ('record_contribution_v2',            '90ef8d9ad34e7f6d8fb9b9be20de577f'),
  ('record_loan_repayment_v1',          '7ba14d8b9cd1d5f7035e6b03b82c7952'),
  ('reconcile_bank_transaction_v1',     '4cc54a865a9c504b325a36c229b733bf'),
  ('pay_platform_fee_v2',               '42921eb9257bde763294c40e8f30dc8c'),
  ('process_complex_payment_v4',        'd32e03083a3dfb663533a650b3fb2097'),
  ('process_rosca_payout_v2',           '5c9b00d5f769c0d1ea1f5467c939c71c'),
  ('process_rosca_payout_v3',           '138002dafaebd53db091b4cd9948141a')
), actual AS (
  SELECT e.name, e.hash AS want,
         (SELECT md5(btrim(regexp_replace(replace(p.prosrc, E'\r', ''), E'[[:space:]]+', ' ', 'g')))
            FROM pg_proc p JOIN pg_namespace s ON s.oid = p.pronamespace
           WHERE s.nspname = 'public' AND p.proname = e.name) AS got
    FROM expected e
)
SELECT name, COALESCE(got, 'ABSENT') AS installed_body,
       CASE WHEN got IS NULL THEN 'ABSENT' WHEN got = want THEN 'MATCH' ELSE 'DRIFT' END AS verdict
  FROM actual ORDER BY name;

-- L.4.d  The guards fail closed in readable form, not just by hash. Expect true for both.
SELECT p.proname,
       position('COALESCE(v_group.balance, 0)' in p.prosrc) > 0 AS guard_fails_closed
  FROM pg_proc p JOIN pg_namespace s ON s.oid = p.pronamespace
 WHERE s.nspname = 'public'
   AND p.proname IN ('approve_and_disburse_loan_v2', 'complete_payout_v2')
 ORDER BY p.proname;

NOTIFY pgrst, 'reload schema';

-- ─────────────────────────────────────────────────────────────────────────────
-- NOT IN THIS BATCH (deliberate, so the next pass starts from the same list)
--   * Reducing the two triggers that fire per contribution and the two per loan
--     repayment. They own derived totals; removing one silently corrupts them.
--   * Annotating record_loan_repayment_v1 with a deterministic transaction_id from
--     process_complex_payment_v4: the payment-level guard in v4 already covers the
--     retry case, and a per-allocation key would collide if one payment ever allocates
--     twice to the same loan.
--   * anon/authenticated still hold EXECUTE on the rotation and disbursement RPCs
--     through the blanket grant in 03_SECURITY_AND_RLS.sql, and
--     process_rosca_payout_v3 does not prove p_period is the member's scheduled month.
--   * The NULL-balance item from the 20261021 delta's NOT IN THIS BATCH is closed by
--     this file; it is no longer carried forward.
-- ─────────────────────────────────────────────────────────────────────────────
