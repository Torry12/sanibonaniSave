-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave - DELTA 2026-10-21  (financial-integrity batch #42)
-- Target: Supabase Cloud (Dashboard SQL editor) and, unchanged, the Local tier.
-- Hand-maintained, like its predecessor SYNC_DELTA_20261014.sql, which is applied
-- history - do not re-run that file and do not edit it to describe these changes.
-- ─────────────────────────────────────────────────────────────────────────────
-- WHAT THIS DOES  (three kinds of change, no others)
--   K.1  creates 8 missing indexes that back idempotency guards and per-write
--        aggregates inside the money paths.
--   K.2  replaces 5 function bodies with the corrected versions now in the masters.
--   K.3  pins SET search_path with ALTER FUNCTION on the functions in its list that the
--        tier reports as SECURITY DEFINER and still unpinned. How many that turns out to
--        be is a fact about the live tier, measured in K.0.f - not a number promised here.
--
-- WHAT THIS DOES NOT DO
--   No DROP of any object, no DELETE, no UPDATE of stored data, no GRANT or REVOKE,
--   no CREATE TABLE, no RLS change. Nothing the app calls changes signature or return
--   key names, so no client build needs to change with this paste.
--   record_platform_revenue_v2 and recalculate_all_group_financials_v3 gain an extra
--   'idempotent' key / a truthful 'discrepancy_found' status; both are additive or use
--   a spelling the Kotlin enum already accepts (@JsonNames("discrepancy_found")).
--
-- WHY THESE FIVE
--   approve_and_disburse_loan_v2     read the loan and the group balance BEFORE taking
--                                    any lock, so two admins could both pass the
--                                    liquidity guard and drive the balance negative.
--   record_platform_revenue_v2       SUMed the whole platform_ledger with no lock and no
--                                    idempotency guard -> concurrent fee payments stored
--                                    the same balance_after, and a retry double-counted.
--   record_platform_revenue_v1       no caller anywhere, but still installed and still
--                                    EXECUTE-granted, so it could interleave with v2 on
--                                    the same unguarded running total.
--   recompute_ledger_running_balances_v2  one UPDATE per ledger row, called once per
--                                    payment through process_complex_payment_v4; now a
--                                    single set-based pass that only writes rows whose
--                                    running balance actually moved.
--   recalculate_all_group_financials_v3   hard-coded status='balanced' and
--                                    discrepancy=0 into the row it wrote, so the
--                                    reconciliation history could never show drift and
--                                    LedgerConsistencyWorker's alert could never fire.
--
-- ORDER OF WORK
--   1. Run K.0 alone. It writes nothing. STOP on any DRIFT / non-zero surprise.
--   2. Run K.1, then K.2, then K.3 - in that order, all in one paste is fine.
--      K.1 uses plain CREATE INDEX, which holds a brief SHARE lock on each table and
--      blocks writes while it builds; run it in a quiet window.
--   3. Run K.4 to confirm. Expect every verdict MATCH, targets_unpinned=0, new_indexes=8.
--
-- MULTI-TIER
--   Nothing in here is cloud-specific, so Local and Online reach the identical end state
--   by running the same K.0 -> K.1 -> K.2 -> K.3 -> K.4 sequence. Run it on Local too:
--   the repository masters already hold these fixes, but re-applying
--   supabase/01_DATABASE_SCHEMA.sql on Local is NOT the equivalent route - that script
--   drops and recreates group_ledger and platform_ledger (01:341 and 01:317), so a
--   masters re-apply would erase the Local data. The NOTIFY in K.4 is a no-op where
--   PostgREST is not watching.
--
-- FINGERPRINT METHOD (same normalisation as the 2026-10-14 delta)
--   md5(btrim(regexp_replace(replace(prosrc, E'\r',''), E'[[:space:]]+',' ', 'g')))
--   over prosrc only, i.e. the body between the two $$ quotes. A signature, a
--   SECURITY DEFINER flag or a SET search_path clause is not in prosrc, which is why
--   section K.3 changes no stored hash and why every expectation inherited from the
--   previous delta stays valid for the 44 functions it does not replace.

-- ─────────────────────────────────────────────────────────────────────────────
-- K.0  PRE-FLIGHT - READ ONLY.  Run this first, on its own. STOP if anything is
--      unexpected; each check states what it expects immediately above itself.
-- ─────────────────────────────────────────────────────────────────────────────

-- K.0.a  The 5 functions K.2 replaces must still be exactly what the masters said before
--        this batch.  Expect: all five verdicts 'MATCH'.  A 'DRIFT' row means the cloud
--        holds a hot fix the repository does not know about - port it into
--        supabase/02_FUNCTIONS_AND_TRIGGERS.sql before pasting anything here.
WITH expected(name, hash) AS (VALUES
  ('approve_and_disburse_loan_v2', '573fa6839c728087ac25b59a3e092d24'),
  ('record_platform_revenue_v1', '16f0200ea7aba0d088429050fa635a0a'),
  ('record_platform_revenue_v2', 'e177966e5f75908a8db09a27ded35944'),
  ('recompute_ledger_running_balances_v2', '916f8f39209f9d0da04e348b4b7ca02a'),
  ('recalculate_all_group_financials_v3', '686f8b4b424576d0a16f05625574b821')
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

-- K.0.b  Revenue rows already double-keyed by transaction_id.  Expect 0.
--        Non-zero is history this delta cannot repair: the new guard answers those keys
--        with the first row it finds instead of inserting a third. Count them and resolve
--        by hand if you want balance_after to be exact before the guard goes live.
SELECT 'platform_ledger_duplicate_tx=' || COALESCE(SUM(d.extra), 0)::text AS b_duplicate_tx_ids
  FROM (SELECT count(*) - 1 AS extra
          FROM public.platform_ledger
         WHERE transaction_id IS NOT NULL
         GROUP BY transaction_id HAVING count(*) > 1) d;

-- K.0.c  None of the 8 index names may already exist.  Expect 0.
--        CREATE INDEX IF NOT EXISTS skips silently on a name clash, so a non-zero count
--        here means K.1 would look successful while installing nothing.
SELECT 'colliding_index_names=' || count(*) AS c_name_clashes
  FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
 WHERE n.nspname = 'public' AND c.relkind IN ('i', 'I')
   AND c.relname = ANY(ARRAY[
  'idx_group_ledger_tx',
  'idx_contributions_group_tx',
  'idx_payments_group_tx',
  'idx_platform_ledger_tx',
  'idx_loan_repayments_loan_id',
  'idx_reconciliations_group_created',
  'idx_loans_group_id',
  'idx_members_id_number'
   ]);

-- K.0.d  K.2 makes recalculate_all_group_financials_v3 write 'discrepancy_found'.
--        Expect true; the definition is printed too so the constraint can be read directly.
--        If it is false, do not paste K.2 - the reconciliation INSERT would fail on every
--        payment, because 01_SCHEMA.sql's CHECK only lists the four legal statuses.
SELECT 'recon_status_allows_discrepancy_found=' || EXISTS (
         SELECT 1 FROM pg_constraint
          WHERE conrelid = 'public.reconciliations'::regclass AND contype = 'c'
            AND pg_get_constraintdef(oid) LIKE '%discrepancy_found%'
       )::text AS d_status_domain,
       (SELECT string_agg(pg_get_constraintdef(oid), ' | ')
          FROM pg_constraint
         WHERE conrelid = 'public.reconciliations'::regclass AND contype = 'c'
           AND pg_get_constraintdef(oid) LIKE '%status%') AS d_status_definition;

-- K.0.e  Groups with a NULL balance.  Expect 0.
--        K.2 treats a NULL balance as 0 when reporting drift, which is the safe reading,
--        but a non-zero count here is the separate open item (the spend guards in
--        approve_and_disburse_loan_v2 and complete_payout_v2 let NULL sail past the
--        comparison) and is worth chasing on its own.
SELECT 'groups_null_balance=' || count(*) AS e_null_balance FROM public.groups WHERE balance IS NULL;

-- K.0.f  How many of the listed pin targets the tier still reports as SECURITY DEFINER
--        and unpinned. This is a measurement of the live tier, not a gate: any value from
--        0 up to the list size is a legitimate starting point, and a low count simply means
--        the tier already carries most of the pins. Do NOT stop on a small number here.
--        The post-condition that does matter is targets_unpinned=0 in K.4.b, after K.3.
--        A count above the list size is impossible by construction, so read K.4.b, not
--        this row, for the pass/fail.
SELECT 'targets_unpinned=' || count(*) AS f_targets_unpinned
  FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace
 WHERE n.nspname = 'public' AND p.prosecdef
   AND p.proname = ANY(ARRAY[
  'is_aal2',
  'is_platform_admin',
  'is_group_admin',
  'is_group_member',
  'can_access_member_storage',
  'increment_group_balance',
  'record_platform_revenue_v2',
  'internal_post_contribution',
  'record_contribution_v2',
  'record_loan_repayment_v1',
  'recompute_ledger_running_balances_v2',
  'recalculate_all_group_financials_v3',
  'generate_platform_audit_report_v1',
  'generate_group_audit_report_v1',
  'generate_member_audit_report_v1',
  'fn_member_status_audit',
  'fn_member_document_audit',
  'fn_trigger_reconciliation_audit',
  'pay_platform_fee_v2',
  'automate_monthly_billing_v1',
  'get_platform_summary_stats_v1'
   ])
   AND COALESCE(array_to_string(p.proconfig, ','), '') NOT LIKE '%search_path%';

-- ─────────────────────────────────────────────────────────────────────────────
-- K.1  FINANCIAL HOT-PATH INDEXES
--      Every one backs an idempotency guard or a per-write aggregate that runs
--      inside a money path, so the sequential scan it removes is paid on every
--      payment, contribution and repayment. Copied verbatim from
--      supabase/01_DATABASE_SCHEMA.sql section 20.1.
--      loan_repayments is deliberately absent: that table already has
--      UNIQUE(group_id, transaction_id), which serves the guard read.
-- ─────────────────────────────────────────────────────────────────────────────
-- 20.1 FINANCIAL HOT-PATH INDEXES
-- Every one of these backs an idempotency guard or a per-write aggregate that runs inside a
-- money path, so a sequential scan here is paid on every payment, contribution and repayment.
CREATE INDEX IF NOT EXISTS idx_group_ledger_tx ON public.group_ledger(group_id, transaction_id);
CREATE INDEX IF NOT EXISTS idx_contributions_group_tx ON public.contributions(group_id, transaction_id);
CREATE INDEX IF NOT EXISTS idx_payments_group_tx ON public.payments(group_id, transaction_id);
CREATE INDEX IF NOT EXISTS idx_platform_ledger_tx ON public.platform_ledger(transaction_id);
CREATE INDEX IF NOT EXISTS idx_loan_repayments_loan_id ON public.loan_repayments(loan_id);
CREATE INDEX IF NOT EXISTS idx_reconciliations_group_created ON public.reconciliations(group_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_loans_group_id ON public.loans(group_id);
CREATE INDEX IF NOT EXISTS idx_members_id_number ON public.members(id_number);


-- ─────────────────────────────────────────────────────────────────────────────
-- K.2  THE FIVE CORRECTED FUNCTIONS
--      Text copied straight from the masters, so the tier this lands on has byte-
--      identical prosrc to the repository. Signatures are unchanged; each body also
--      carries its own SET search_path, which is why K.3 then has 18 rather than 21
--      targets left.
--      Behaviour to expect after this section:
--        * a duplicate record_platform_revenue_v2 call (same transaction_id) returns the
--          original row plus 'idempotent': true instead of adding a second ledger row;
--        * recalculate_all_group_financials_v3 returns a real 'discrepancy' and writes
--          status='discrepancy_found' whenever the group's believed balance and its own
--          ledger disagree - which is what arms LedgerConsistencyWorker's repair path;
--        * recompute_ledger_running_balances_v2 raises [SYS_001] GROUP_NOT_FOUND for a
--          group id that does not exist, where before it silently wrote nothing.
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
    IF v_group.balance < v_loan.amount THEN RAISE EXCEPTION '[FIN_001] INSUFFICIENT_LIQUIDITY'; END IF;

    SELECT full_name INTO v_m_name FROM public.members WHERE id = v_loan.member_id;

    UPDATE public.loans SET status = 'active', reviewed_by = p_admin_id, reviewed_at = NOW(), start_date = NOW(), updated_at = NOW() WHERE id = p_loan_id;
    PERFORM public.increment_group_balance(v_loan.group_id, -v_loan.amount, 'Loan disbursement to ' || v_m_name || ' (' || p_payment_method || ')', 'loan_disbursement', p_loan_id::text, v_loan.member_id);

    PERFORM public.log_audit_event('LOAN_DISBURSED', p_admin_id::text, v_loan.member_id, v_loan.group_id, jsonb_build_object('loan_id', p_loan_id, 'amount', v_loan.amount), p_loan_id);
END;
$$;

CREATE OR REPLACE FUNCTION public.record_platform_revenue_v1(
    p_amount NUMERIC,
    p_description TEXT,
    p_category TEXT,
    p_transaction_id TEXT DEFAULT NULL,
    p_group_id UUID DEFAULT NULL
) RETURNS VOID AS $$
DECLARE
    v_last_balance NUMERIC;
BEGIN
    PERFORM pg_advisory_xact_lock(hashtext('platform_ledger')::bigint);

    IF p_transaction_id IS NOT NULL THEN
        IF EXISTS (SELECT 1 FROM public.platform_ledger WHERE transaction_id = p_transaction_id LIMIT 1) THEN
            RETURN;
        END IF;
    END IF;

    SELECT balance_after INTO v_last_balance FROM public.platform_ledger ORDER BY created_at DESC LIMIT 1;
    INSERT INTO public.platform_ledger (transaction_id, amount, balance_after, description, category, group_id)
    VALUES (p_transaction_id, p_amount, COALESCE(v_last_balance, 0) + p_amount, p_description, p_category, p_group_id);
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, pg_temp;

CREATE OR REPLACE FUNCTION public.record_platform_revenue_v2(
    p_amount NUMERIC,
    p_description TEXT,
    p_category TEXT,
    p_transaction_id TEXT DEFAULT NULL,
    p_group_id UUID DEFAULT NULL
) RETURNS JSONB AS $$
DECLARE
    v_last_balance NUMERIC;
    v_new_balance NUMERIC;
    v_existing RECORD;
BEGIN
    -- Serialises every writer of this running total: two concurrent fee payments would otherwise
    -- read the same SUM and each store it as their own balance_after.
    PERFORM pg_advisory_xact_lock(hashtext('platform_ledger')::bigint);

    IF p_transaction_id IS NOT NULL THEN
        SELECT * INTO v_existing FROM public.platform_ledger WHERE transaction_id = p_transaction_id LIMIT 1;
        IF FOUND THEN
            RETURN jsonb_build_object('amount', v_existing.amount, 'balance_after', v_existing.balance_after, 'idempotent', true);
        END IF;
    END IF;

    SELECT COALESCE(SUM(amount), 0) INTO v_last_balance FROM public.platform_ledger;
    v_new_balance := v_last_balance + p_amount;

    INSERT INTO public.platform_ledger (transaction_id, amount, balance_after, description, category, group_id)
    VALUES (p_transaction_id, p_amount, v_new_balance, p_description, p_category, p_group_id);

    RETURN jsonb_build_object('amount', p_amount, 'balance_after', v_new_balance);
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, pg_temp;

CREATE OR REPLACE FUNCTION public.recompute_ledger_running_balances_v2(p_group_id UUID)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_bal NUMERIC;
    v_count INT;
BEGIN
    PERFORM id FROM public.groups WHERE id = p_group_id FOR UPDATE;
    IF NOT FOUND THEN RAISE EXCEPTION '[SYS_001] GROUP_NOT_FOUND'; END IF;

    -- One set-based pass. The previous cursor issued one UPDATE per ledger row, so every payment
    -- rewritten the group's whole history; only rows whose running balance actually moved are
    -- touched now.
    UPDATE public.group_ledger gl
       SET balance_after = sub.running_balance
      FROM (
        SELECT id, SUM(amount) OVER (ORDER BY created_at ASC, id ASC) AS running_balance
        FROM public.group_ledger
        WHERE group_id = p_group_id
      ) sub
     WHERE gl.id = sub.id
       AND gl.balance_after IS DISTINCT FROM sub.running_balance;

    SELECT COALESCE(SUM(amount), 0), COUNT(*) INTO v_bal, v_count
    FROM public.group_ledger WHERE group_id = p_group_id;

    UPDATE public.groups SET balance = v_bal WHERE id = p_group_id;
    RETURN jsonb_build_object('final_balance', v_bal, 'count', v_count);
END;
$$;

CREATE OR REPLACE FUNCTION public.recalculate_all_group_financials_v3(p_group_id UUID)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_stats JSONB;
    v_recon_id UUID;
    v_report JSONB;
    v_recorded_balance NUMERIC;
    v_ledger_sum NUMERIC;
    v_diff NUMERIC;
    v_status TEXT;
    v_notes TEXT;
BEGIN
    PERFORM 1 FROM public.groups WHERE id = p_group_id FOR UPDATE;
    IF NOT FOUND THEN RAISE EXCEPTION '[SYS_001] GROUP_NOT_FOUND'; END IF;

    SELECT balance INTO v_recorded_balance FROM public.groups WHERE id = p_group_id;
    v_recorded_balance := COALESCE(v_recorded_balance, 0);

    v_recon_id := gen_random_uuid();
    SELECT public.recompute_ledger_running_balances_v2(p_group_id) INTO v_stats;

    -- The balance the group believed it had before this reconstruction, against what its own
    -- ledger adds up to. Reporting 'balanced' unconditionally made this row pure theatre and
    -- kept LedgerConsistencyWorker's discrepancy alert permanently armed off.
    v_ledger_sum := (v_stats->>'final_balance')::NUMERIC;
    v_diff := ROUND(v_recorded_balance - v_ledger_sum, 2);
    v_status := CASE WHEN ABS(v_diff) <= 0.01 THEN 'balanced' ELSE 'discrepancy_found' END;
    v_notes := CASE WHEN v_status = 'balanced'
        THEN 'Deep ledger reconstruction performed via system repair.'
        ELSE 'Deep ledger reconstruction: repaired a ' || ABS(v_diff)::TEXT || ' drift between the recorded group balance and the ledger sum.'
    END;

    -- Insert a record into reconciliations for forensic tracking
    INSERT INTO public.reconciliations (
        id, group_id, period_start, period_end, status,
        ledger_balance, actual_balance, discrepancy, notes, created_at
    ) VALUES (
        v_recon_id, p_group_id, NOW() - INTERVAL '365 days', NOW(), v_status,
        v_ledger_sum, v_recorded_balance, v_diff,
        v_notes, NOW()
    );

    v_report := jsonb_build_object(
        'reconstruction_id', v_recon_id::text,
        'group_id', p_group_id::text,
        'entries_reconstructed', (v_stats->>'count')::int,
        'final_balance', (v_stats->>'final_balance')::text,
        'reconciliation_report', jsonb_build_object(
            'id', v_recon_id::text,
            'group_id', p_group_id::text,
            'period_start', (EXTRACT(EPOCH FROM (NOW() - INTERVAL '365 days')) * 1000)::bigint,
            'period_end', (EXTRACT(EPOCH FROM NOW()) * 1000)::bigint,
            'status', v_status,
            'ledger_balance', v_ledger_sum::NUMERIC(12,2)::text,
            'actual_balance', v_recorded_balance::NUMERIC(12,2)::text,
            'discrepancy', v_diff::NUMERIC(12,2)::text,
            'notes', v_notes,
            'created_at', NOW()::text
        ),
        'timestamp', NOW()::text
    );

    RETURN v_report;
END;
$$;


-- ─────────────────────────────────────────────────────────────────────────────
-- K.3  PIN search_path ON THE LISTED FUNCTIONS THAT ARE SECURITY DEFINER
--      ALTER FUNCTION, not CREATE OR REPLACE: search_path is a function attribute and
--      is not part of prosrc, so this section changes no stored hash and cannot alter
--      any behaviour. The signatures are read from pg_catalog rather than transcribed,
--      so an overload or a changed argument list cannot make the paste fail.
--      Idempotent: the WHERE clause only selects functions that lack the pin, so
--      re-running it is a no-op. It also selects only p.prosecdef rows, so a name in the
--      list that the tier reports as SECURITY INVOKER is skipped rather than failing the
--      paste; that is deliberate - invoker functions already resolve objects through the
--      caller's path and are not the search_path risk this section targets.
--      Safe because a body-level scan of every name below found no unqualified reference
--      that public, pg_temp would fail to resolve: no bare auth table name (no "FROM
--      users" / "FROM sessions" - the auth references that do exist in this repository are
--      written as auth.uid() and auth.<table>, which resolve regardless of search_path)
--      and no bare pgcrypto call. In fact none of the listed bodies call pgcrypto at all;
--      the only crypt / gen_salt sites in the repository are the setup and seed scripts,
--      and every one of them is already written as extensions.crypt / extensions.gen_salt.
-- ─────────────────────────────────────────────────────────────────────────────
DO $$
DECLARE
    r   RECORD;
    n   INT := 0;
BEGIN
    FOR r IN
        SELECT p.oid::regprocedure::text AS sig
          FROM pg_proc p JOIN pg_namespace s ON s.oid = p.pronamespace
         WHERE s.nspname = 'public' AND p.prosecdef
           AND p.proname = ANY(ARRAY[
  'is_aal2',
  'is_platform_admin',
  'is_group_admin',
  'is_group_member',
  'can_access_member_storage',
  'increment_group_balance',
  'record_platform_revenue_v2',
  'internal_post_contribution',
  'record_contribution_v2',
  'record_loan_repayment_v1',
  'recompute_ledger_running_balances_v2',
  'recalculate_all_group_financials_v3',
  'generate_platform_audit_report_v1',
  'generate_group_audit_report_v1',
  'generate_member_audit_report_v1',
  'fn_member_status_audit',
  'fn_member_document_audit',
  'fn_trigger_reconciliation_audit',
  'pay_platform_fee_v2',
  'automate_monthly_billing_v1',
  'get_platform_summary_stats_v1'
           ])
           AND COALESCE(array_to_string(p.proconfig, ','), '') NOT LIKE '%search_path%'
         ORDER BY p.proname
    LOOP
        EXECUTE format('ALTER FUNCTION %s SET search_path = public, pg_temp', r.sig);
        n := n + 1;
    END LOOP;
    RAISE NOTICE 'K.3 pinned search_path on % function(s)', n;
END
$$;

-- ─────────────────────────────────────────────────────────────────────────────
-- K.4  VERIFICATION - READ ONLY.  Run after K.1-K.3.
-- ─────────────────────────────────────────────────────────────────────────────

-- K.4.a  The five replaced bodies.  Expect all five 'MATCH'.
WITH expected(name, hash) AS (VALUES
  ('approve_and_disburse_loan_v2', 'e42f0f4def47d261fd1e8c8e03569297'),
  ('record_platform_revenue_v1', '92b3c4af0d00a0e85100f4b53c259186'),
  ('record_platform_revenue_v2', '69b90494975e790a284b09669a643a4b'),
  ('recompute_ledger_running_balances_v2', '5ce092bd4b98c9143af08318986b124e'),
  ('recalculate_all_group_financials_v3', '35ed7f1aa21cf1c3d718d05bc9838ed4')
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

-- K.4.b  Nothing in the pin list may be left unpinned.  Expect targets_unpinned=0.
SELECT 'targets_unpinned=' || count(*) AS b_targets_unpinned
  FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace
 WHERE n.nspname = 'public' AND p.prosecdef
   AND p.proname = ANY(ARRAY[
  'is_aal2',
  'is_platform_admin',
  'is_group_admin',
  'is_group_member',
  'can_access_member_storage',
  'increment_group_balance',
  'record_platform_revenue_v2',
  'internal_post_contribution',
  'record_contribution_v2',
  'record_loan_repayment_v1',
  'recompute_ledger_running_balances_v2',
  'recalculate_all_group_financials_v3',
  'generate_platform_audit_report_v1',
  'generate_group_audit_report_v1',
  'generate_member_audit_report_v1',
  'fn_member_status_audit',
  'fn_member_document_audit',
  'fn_trigger_reconciliation_audit',
  'pay_platform_fee_v2',
  'automate_monthly_billing_v1',
  'get_platform_summary_stats_v1'
   ])
   AND COALESCE(array_to_string(p.proconfig, ','), '') NOT LIKE '%search_path%';

-- K.4.c  All eight indexes present.  Expect new_indexes=8.
SELECT 'new_indexes=' || count(*) AS c_new_indexes
  FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
 WHERE n.nspname = 'public' AND c.relname = ANY(ARRAY[
  'idx_group_ledger_tx',
  'idx_contributions_group_tx',
  'idx_payments_group_tx',
  'idx_platform_ledger_tx',
  'idx_loan_repayments_loan_id',
  'idx_reconciliations_group_created',
  'idx_loans_group_id',
  'idx_members_id_number'
   ]);

-- K.4.d  Functions that must NOT have moved.  Expect all 'MATCH': this delta replaced
--        5 bodies and touched no other prosrc.
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

-- K.4.e  Still-open gaps this delta does not close (recorded, not fixed, so a clean
--        run here does not read as "all resolved").  Expect definitions_in_db = 0 for
--        all three, exactly as section I.2 of the 2026-10-14 delta reported.
SELECT p.name AS undefined_rpc_called_by_the_app,
       COALESCE((SELECT count(*) FROM pg_proc x
                  JOIN pg_namespace s ON s.oid = x.pronamespace
                 WHERE s.nspname = 'public' AND x.proname = p.name), 0) AS definitions_in_db
  FROM (VALUES ('close_poll_v1'), ('pay_burial_claim_v1'), ('record_disbursement_v1')) p(name)
 ORDER BY p.name;

NOTIFY pgrst, 'reload schema';

-- ─────────────────────────────────────────────────────────────────────────────
-- NOT IN THIS BATCH (deliberate, so the next pass starts from the same list)
--   * Reducing the two triggers that fire per contribution and the two per loan
--     repayment. They own derived totals; removing one silently corrupts them.
--   * Annotating record_loan_repayment_v1 with a deterministic transaction_id from
--     process_complex_payment_v4: the payment-level guard in v4 already covers the
--     retry case, and a per-allocation key would collide if one payment ever allocates
--     twice to the same loan.
--   * The spend guards passing a NULL group balance (K.0.e).
--   * anon/authenticated still hold EXECUTE on the rotation and disbursement RPCs
--     through the blanket grant in 03_SECURITY_AND_RLS.sql, and
--     process_rosca_payout_v3 does not prove p_period is the member's scheduled month.
-- ─────────────────────────────────────────────────────────────────────────────
