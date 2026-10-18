-- =============================================================================
-- SanibonaniSave - ONLINE (Supabase Cloud) SYNC DELTA
-- Generated: 2026-10-14   Source: regenerated GROUND_ZERO v2.2 baseline
-- =============================================================================
-- PURPOSE
--   Brings an existing Online tier up to the schema and financial logic that the Local
--   (Docker) tier was verified against on 2026-10-14, without a destructive reset.
-- SAFETY
--   Every statement is additive or idempotent: CREATE ... IF NOT EXISTS,
--   CREATE OR REPLACE FUNCTION, and DROP TRIGGER IF EXISTS + CREATE TRIGGER pairs.
--   This file contains no DROP TABLE, TRUNCATE, DELETE and no 07_RESET_DATABASE.sql.
--   The only objects removed are in section E, which creates the replacement
--   member-count trigger before dropping the duplicate it supersedes, so no
--   invariant is ever left unmaintained.
--   Section F reconciles constraints that CREATE TABLE IF NOT EXISTS cannot change on
--   a table that already exists; every statement there is guarded and self-skipping.
-- NOT INCLUDED
--   Seed data (SEED_*.sql) and the platform admin bootstrap. A running Online project
--   must never be re-seeded from this script.
--   Section G is conditional: read its G.0 report before deciding whether G.1 is needed.
-- EXPECTED RESULT: 0 errors. An error means Online differs from what this delta assumed;
--   run section V and compare against Local before retrying.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- A. MISSING COLUMNS (4) - additive; already present is a no-op
-- -----------------------------------------------------------------------------
ALTER TABLE public.contributions
  ADD COLUMN IF NOT EXISTS late_fee_amount  NUMERIC(10,2) DEFAULT 0 CHECK (late_fee_amount >= 0),
  ADD COLUMN IF NOT EXISTS principal_amount NUMERIC(10,2) DEFAULT 0 CHECK (principal_amount >= 0);

ALTER TABLE public.payouts
  ADD COLUMN IF NOT EXISTS metadata JSONB DEFAULT '{}'::jsonb;

ALTER TABLE public.platform_settings
  ADD COLUMN IF NOT EXISTS value_text TEXT;

-- -----------------------------------------------------------------------------
-- B. MISSING TABLES (4) - definitions copied verbatim from 01_DATABASE_SCHEMA.sql
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.bank_connections (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id               UUID REFERENCES public.groups(id) ON DELETE CASCADE NOT NULL,
    provider_id            TEXT REFERENCES public.financial_providers(id) NOT NULL,
    status                 TEXT NOT NULL CHECK (status IN ('active', 'expired', 'disconnected', 'pending')),
    account_name           TEXT,
    account_number_hidden  TEXT,
    linked_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at             TIMESTAMPTZ,
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(group_id)
);

CREATE TABLE IF NOT EXISTS public.platform_audit_reports (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    generated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    liquidity         NUMERIC(15,2) NOT NULL DEFAULT 0,
    revenue           NUMERIC(15,2) NOT NULL DEFAULT 0,
    risk              JSONB NOT NULL DEFAULT '{}'::jsonb,
    groups            JSONB NOT NULL DEFAULT '{}'::jsonb,
    top_level_stats   JSONB NOT NULL DEFAULT '{}'::jsonb,
    metadata          JSONB DEFAULT '{}'::jsonb
);

CREATE TABLE IF NOT EXISTS public.audit_reports (
    id                TEXT PRIMARY KEY, -- format: [type]_[id], e.g. member_abc, group_123, platform
    report_type       TEXT NOT NULL CHECK (report_type IN ('member', 'group', 'platform')),
    target_id         UUID,
    content_json      TEXT NOT NULL,
    generated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS public.pending_operations (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type           TEXT NOT NULL,
    payload        TEXT NOT NULL,
    group_id       UUID REFERENCES public.groups(id) ON DELETE SET NULL,
    created_at     BIGINT NOT NULL, -- Epoch ms to match Room
    retry_count    INTEGER DEFAULT 0,
    last_error     TEXT,
    server_synced  BOOLEAN DEFAULT FALSE
);

-- -----------------------------------------------------------------------------
-- C. FINANCIAL BRAIN FIX: pay_platform_fee_v2 idempotency guard
--    Folded from migrations/43_platform_fee_payment_idempotency.sql into
--    02_FUNCTIONS_AND_TRIGGERS.sql. A repeated p_tx_id now returns
--    status='idempotent' instead of deducting group balance and booking platform
--    revenue twice.
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.pay_platform_fee_v2(
    p_group_id UUID,
    p_amount NUMERIC,
    p_fee_type TEXT, -- 'monthly', 'registration'
    p_tx_id TEXT DEFAULT NULL,
    p_from_group_fund BOOLEAN DEFAULT TRUE
) RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_current_bal NUMERIC;
    v_allocated NUMERIC := 0;
    v_fee RECORD;
    v_group_name TEXT;
BEGIN
    -- Row lock first: serialises concurrent submissions of the same tx_id.
    SELECT name, balance INTO v_group_name, v_current_bal FROM public.groups WHERE id = p_group_id FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION '[SYS_001] GROUP_NOT_FOUND: %', p_group_id;
    END IF;

    -- ── Idempotency guard ────────────────────────────────────────────────
    -- A NULL tx_id cannot be deduplicated, so fall through to normal processing;
    -- callers are expected to always supply a deterministic reference.
    IF p_tx_id IS NOT NULL AND (
        EXISTS (SELECT 1 FROM public.group_ledger   WHERE group_id = p_group_id AND transaction_id = p_tx_id)
        OR EXISTS (SELECT 1 FROM public.platform_ledger WHERE transaction_id = p_tx_id)
        OR EXISTS (SELECT 1 FROM public.platform_fees  WHERE group_id = p_group_id AND transaction_id = p_tx_id)
    ) THEN
        RETURN jsonb_build_object(
            'paid', p_amount,
            'remaining_allocated', p_amount,
            'transaction_id', p_tx_id,
            'status', 'idempotent'
        );
    END IF;

    IF p_from_group_fund THEN
        IF v_current_bal < p_amount THEN
            RAISE EXCEPTION '[FIN_002] INSUFFICIENT_FUNDS: Group balance is %', v_current_bal;
        END IF;

        -- Deduct from group
        PERFORM public.increment_group_balance(
            p_group_id,
            -p_amount,
            'Platform ' || p_fee_type || ' fee payment',
            'platform_fee',
            p_tx_id
        );
    END IF;

    -- Record platform revenue
    PERFORM public.record_platform_revenue_v2(
        p_amount,
        'Fee payment from ' || v_group_name,
        p_fee_type || '_fee',
        p_tx_id,
        p_group_id
    );

    -- Waterfall allocation for 'monthly' fees
    IF p_fee_type = 'monthly' THEN
        FOR v_fee IN (
            SELECT id, amount_due, amount_paid
            FROM public.platform_fees
            WHERE group_id = p_group_id AND status != 'paid' AND fee_type = 'monthly'
            ORDER BY due_date ASC
        ) LOOP
            IF p_amount - v_allocated <= 0 THEN EXIT; END IF;

            DECLARE
                v_to_pay NUMERIC := LEAST(v_fee.amount_due - v_fee.amount_paid, p_amount - v_allocated);
            BEGIN
                UPDATE public.platform_fees
                SET amount_paid = amount_paid + v_to_pay,
                    status = CASE WHEN (amount_paid + v_to_pay) >= amount_due THEN 'paid' ELSE status END,
                    paid_at = NOW(),
                    transaction_id = COALESCE(transaction_id, p_tx_id)
                WHERE id = v_fee.id;

                v_allocated := v_allocated + v_to_pay;
            END;
        END LOOP;
    ELSIF p_fee_type = 'registration' THEN
        UPDATE public.groups SET registration_paid = TRUE WHERE id = p_group_id;
    END IF;

    -- Update group standing
    IF NOT EXISTS (SELECT 1 FROM public.platform_fees WHERE group_id = p_group_id AND status != 'paid') THEN
        UPDATE public.groups SET fee_status = 'paid', is_platform_suspended = FALSE WHERE id = p_group_id;
    END IF;

    RETURN jsonb_build_object(
        'paid', p_amount,
        'remaining_allocated', p_amount - v_allocated,
        'transaction_id', p_tx_id,
        'status', 'processed'
    );
END;
$$;

-- -----------------------------------------------------------------------------
-- D. AGGREGATE & GOVERNANCE MAINTENANCE (back-ported from the baseline)
--    Maintains members.total_paid / total_contributions, loans.total_repaid,
--    group_poll_options.vote_count, member_behavior_track and the voting audit
--    trail. Without these the Online tier lets those columns drift from the
--    ledgers they are derived from.
--    handle_new_user is defined but not attached to auth.users, matching Local:
--    that attachment is a per-project Supabase dashboard object.
-- -----------------------------------------------------------------------------
-- 10. AGGREGATE & GOVERNANCE SYNC TRIGGERS
-- Back-ported from the GROUND_ZERO baseline so master-built tiers maintain derived
-- totals server-side. groups.current_members is owned by tr_sync_group_member_count
-- above, so the baseline's duplicate counter trigger was intentionally not ported.

CREATE OR REPLACE FUNCTION public.fn_sync_single_member_financials(p_member_id UUID)
RETURNS VOID AS $$
BEGIN
    UPDATE public.members SET
        total_paid = (
            SELECT COALESCE(SUM(amount), 0)
            FROM public.contributions
            WHERE member_id = p_member_id AND status = 'paid'
            AND type IN ('contribution', 'joining_fee', 'registration_contribution', 'loan_settlement_extraction')
        ),
        total_contributions = (
            SELECT COUNT(*)
            FROM public.contributions
            WHERE member_id = p_member_id AND status = 'paid'
            AND type IN ('contribution', 'registration_contribution')
        ),
        updated_at = NOW()
    WHERE id = p_member_id;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

CREATE OR REPLACE FUNCTION public.fn_trigger_sync_member_financials() RETURNS TRIGGER AS $$
DECLARE
    v_m_id UUID;
BEGIN
    v_m_id := COALESCE(NEW.member_id, OLD.member_id);
    IF v_m_id IS NOT NULL THEN
        PERFORM public.fn_sync_single_member_financials(v_m_id);
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

DROP TRIGGER IF EXISTS tr_sync_member_financials_contrib ON public.contributions;
CREATE TRIGGER tr_sync_member_financials_contrib AFTER INSERT OR UPDATE OR DELETE ON public.contributions FOR EACH ROW EXECUTE FUNCTION public.fn_trigger_sync_member_financials();

CREATE OR REPLACE FUNCTION public.fn_sync_single_loan_totals(p_loan_id UUID)
RETURNS VOID AS $$
DECLARE
    v_rep NUMERIC;
    v_target NUMERIC;
BEGIN
    SELECT COALESCE(SUM(amount), 0) INTO v_rep FROM public.loan_repayments WHERE loan_id = p_loan_id;
    SELECT total_to_repay INTO v_target FROM public.loans WHERE id = p_loan_id;
    UPDATE public.loans SET total_repaid = v_rep, status = CASE WHEN v_rep >= v_target - 0.01 THEN 'completed' WHEN v_rep > 0 THEN 'partially_paid' ELSE status END, updated_at = NOW() WHERE id = p_loan_id;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

CREATE OR REPLACE FUNCTION public.fn_trigger_sync_loan_repayment_total() RETURNS TRIGGER AS $$
BEGIN
    IF (TG_OP = 'INSERT' OR TG_OP = 'UPDATE') THEN PERFORM public.fn_sync_single_loan_totals(NEW.loan_id); END IF;
    IF (TG_OP = 'DELETE' OR (TG_OP = 'UPDATE' AND OLD.loan_id IS DISTINCT FROM NEW.loan_id)) THEN PERFORM public.fn_sync_single_loan_totals(OLD.loan_id); END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

DROP TRIGGER IF EXISTS tr_sync_loan_repayment_total ON public.loan_repayments;
CREATE TRIGGER tr_sync_loan_repayment_total AFTER INSERT OR UPDATE OR DELETE ON public.loan_repayments FOR EACH ROW EXECUTE FUNCTION public.fn_trigger_sync_loan_repayment_total();

CREATE OR REPLACE FUNCTION public.fn_member_behavior_init() RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO public.member_behavior_track (member_id, group_id, behavior_score, fraud_risk_level)
    VALUES (NEW.id, NEW.group_id, 100.0, 'low')
    ON CONFLICT (member_id) DO NOTHING;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, pg_temp;

DROP TRIGGER IF EXISTS tr_member_behavior_init ON public.members;
CREATE TRIGGER tr_member_behavior_init AFTER INSERT ON public.members FOR EACH ROW EXECUTE FUNCTION public.fn_member_behavior_init();

CREATE OR REPLACE FUNCTION public.fn_member_financial_sync_on_change() RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        -- No sync needed for deleted member (cascaded) or just clear?
        -- Usually we keep it.
    ELSE
        IF pg_trigger_depth() < 2 THEN
            PERFORM public.fn_sync_single_member_financials(NEW.id);
        END IF;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, pg_temp;

DROP TRIGGER IF EXISTS tr_member_financial_sync_member ON public.members;
CREATE TRIGGER tr_member_financial_sync_member AFTER INSERT OR UPDATE OF status ON public.members FOR EACH ROW EXECUTE FUNCTION public.fn_member_financial_sync_on_change();

CREATE OR REPLACE FUNCTION public.fn_trigger_update_behavior_metrics() RETURNS TRIGGER AS $$
DECLARE
    v_m_id UUID;
    v_g_id UUID;
    v_score NUMERIC(5,2) := 100.0;
    v_risk TEXT := 'low';
    v_late_count INT;
BEGIN
    v_m_id := COALESCE(NEW.member_id, OLD.member_id);
    v_g_id := COALESCE(NEW.group_id, OLD.group_id);

    -- 1. Simple heuristic: Deduct 10 points for each overdue contribution
    SELECT COUNT(*) INTO v_late_count FROM public.contributions WHERE member_id = v_m_id AND status = 'overdue';
    v_score := (v_score - (v_late_count * 10.0))::NUMERIC(5,2);

    IF v_score < 40 THEN v_risk := 'high';
    ELSIF v_score < 70 THEN v_risk := 'medium';
    END IF;

    UPDATE public.member_behavior_track
    SET behavior_score = GREATEST(0, v_score),
        fraud_risk_level = v_risk,
        updated_at = NOW()
    WHERE member_id = v_m_id;

    RETURN NULL;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

DROP TRIGGER IF EXISTS tr_update_behavior_metrics ON public.contributions;
CREATE TRIGGER tr_update_behavior_metrics AFTER INSERT OR UPDATE ON public.contributions FOR EACH ROW EXECUTE FUNCTION public.fn_trigger_update_behavior_metrics();

DROP TRIGGER IF EXISTS tr_update_behavior_metrics_loans ON public.loan_repayments;
CREATE TRIGGER tr_update_behavior_metrics_loans AFTER INSERT OR UPDATE ON public.loan_repayments FOR EACH ROW EXECUTE FUNCTION public.fn_trigger_update_behavior_metrics();

CREATE OR REPLACE FUNCTION public.fn_trigger_sync_poll_vote_count() RETURNS TRIGGER AS $$
BEGIN
    IF (TG_OP = 'INSERT') THEN UPDATE public.group_poll_options SET vote_count = vote_count + 1 WHERE id = NEW.option_id;
    ELSIF (TG_OP = 'DELETE') THEN UPDATE public.group_poll_options SET vote_count = vote_count - 1 WHERE id = OLD.option_id;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

DROP TRIGGER IF EXISTS tr_sync_poll_vote_count ON public.group_poll_votes;
CREATE TRIGGER tr_sync_poll_vote_count AFTER INSERT OR DELETE ON public.group_poll_votes FOR EACH ROW EXECUTE FUNCTION public.fn_trigger_sync_poll_vote_count();

CREATE OR REPLACE FUNCTION public.fn_trigger_sync_poll_status() RETURNS TRIGGER AS $$
BEGIN
    -- This would normally be handled by a cron job, but we can hook into updates
    IF NEW.ends_at <= NOW() AND NEW.status = 'open' THEN
        NEW.status := 'closed';
        PERFORM public.log_audit_event('POLL_AUTO_CLOSED', 'system_gov', NULL, NEW.group_id, jsonb_build_object('poll_id', NEW.id), NEW.id);
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS tr_sync_poll_status ON public.group_polls;
CREATE TRIGGER tr_sync_poll_status BEFORE UPDATE ON public.group_polls FOR EACH ROW EXECUTE FUNCTION public.fn_trigger_sync_poll_status();

CREATE OR REPLACE FUNCTION public.fn_trigger_voting_audit() RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        IF TG_TABLE_NAME = 'group_polls' THEN
            PERFORM public.log_audit_event('POLL_CREATED', NEW.created_by_member_id::text, NULL, NEW.group_id, jsonb_build_object('poll_id', NEW.id, 'title', NEW.title), NEW.id);
        ELSIF TG_TABLE_NAME = 'group_poll_votes' THEN
            PERFORM public.log_audit_event('VOTE_CAST', NEW.member_id::text, NEW.member_id, NEW.group_id, jsonb_build_object('poll_id', NEW.poll_id), NEW.poll_id);
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, pg_temp;

DROP TRIGGER IF EXISTS tr_poll_audit ON public.group_polls;
CREATE TRIGGER tr_poll_audit AFTER INSERT ON public.group_polls FOR EACH ROW EXECUTE FUNCTION public.fn_trigger_voting_audit();

DROP TRIGGER IF EXISTS tr_vote_audit ON public.group_poll_votes;
CREATE TRIGGER tr_vote_audit AFTER INSERT ON public.group_poll_votes FOR EACH ROW EXECUTE FUNCTION public.fn_trigger_voting_audit();

-- 10.1 SIGNUP BOOTSTRAP
-- Defined for baseline parity. No trigger is attached to auth.users here, matching the
-- baseline: Supabase provisions that attachment out of band per project.
CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS TRIGGER AS $$
DECLARE
    v_full_name TEXT;
BEGIN
  v_full_name := COALESCE(NEW.raw_user_meta_data ->> 'full_name', 'New User');
  IF char_length(v_full_name) < 3 THEN v_full_name := v_full_name || ' (User)'; END IF;
  INSERT INTO public.profiles (id, full_name, email, role)
  VALUES (NEW.id, v_full_name, NEW.email, COALESCE(NEW.raw_user_meta_data ->> 'role', 'member'));
  RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, pg_temp;

-- -----------------------------------------------------------------------------
-- E. MEMBER-COUNT OWNER, then the duplicate it replaces
--    A tier built from the pre-2026-10-14 baseline has fn_member_counter_sync only
--    and NO tr_sync_group_member_count. Creating the master version first (E.1)
--    guarantees groups.current_members always has exactly one owner, so the drop in
--    E.2 can never leave the counter unmaintained. Both are count-based recompute,
--    so running them side by side would be harmless but redundant.
--    Re-creating either is enough to undo E.2.
-- -----------------------------------------------------------------------------
-- E.1 master implementation (verbatim from 02_FUNCTIONS_AND_TRIGGERS.sql section 8)
CREATE OR REPLACE FUNCTION public.fn_sync_group_member_count()
RETURNS TRIGGER AS $$
BEGIN
    UPDATE public.groups SET current_members = (SELECT count(*) FROM public.members WHERE group_id = COALESCE(NEW.group_id, OLD.group_id) AND status != 'suspended')
    WHERE id = COALESCE(NEW.group_id, OLD.group_id);
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS tr_sync_group_member_count ON public.members;
CREATE TRIGGER tr_sync_group_member_count AFTER INSERT OR UPDATE OR DELETE ON public.members FOR EACH ROW EXECUTE FUNCTION public.fn_sync_group_member_count();

-- E.2 drop the baseline's duplicate
DROP TRIGGER IF EXISTS tr_member_counter_sync ON public.members;
DROP FUNCTION IF EXISTS public.fn_member_counter_sync();

-- -----------------------------------------------------------------------------
-- F. CONSTRAINT AND NULLABILITY RECONCILIATION
--    Found by rehearsing this delta on a tier built from the previous baseline:
--    CREATE TABLE IF NOT EXISTS cannot change an existing table, so the three
--    differences below need explicit statements. Each is guarded.
-- -----------------------------------------------------------------------------
-- F.1 platform_fees UNIQUE(group_id, month_reference): in 01_DATABASE_SCHEMA.sql:588,
--     absent from the previous baseline. Monthly-fee provisioning and the fee
--     idempotency in section C both lean on it. Only added when no duplicates exist.
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint
                 WHERE conname = 'platform_fees_group_id_month_reference_key'
                   AND conrelid = 'public.platform_fees'::regclass) THEN
    IF EXISTS (SELECT 1 FROM public.platform_fees
               GROUP BY group_id, month_reference HAVING count(*) > 1) THEN
      RAISE NOTICE 'F.1 SKIPPED: duplicate (group_id, month_reference) rows exist. Deduplicate platform_fees, then re-run.';
    ELSE
      ALTER TABLE public.platform_fees
        ADD CONSTRAINT platform_fees_group_id_month_reference_key UNIQUE (group_id, month_reference);
    END IF;
  END IF;
END $$;

-- F.2 platform_settings.value is nullable in 01_DATABASE_SCHEMA.sql:570 but was NOT
--     NULL in the previous baseline. Relaxing the constraint loses no data.
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.columns
             WHERE table_schema = 'public' AND table_name = 'platform_settings'
               AND column_name = 'value' AND is_nullable = 'NO') THEN
    ALTER TABLE public.platform_settings ALTER COLUMN value DROP NOT NULL;
  END IF;
END $$;

-- F.3 groups: the previous baseline lacked the three claim/reserve columns. If the
--     table predates them, CREATE TABLE IF NOT EXISTS silently skipped them.
ALTER TABLE public.groups
  ADD COLUMN IF NOT EXISTS burial_claim_amount    NUMERIC(10,2) DEFAULT 10000.00 CHECK (burial_claim_amount >= 0),
  ADD COLUMN IF NOT EXISTS min_members_for_claims INTEGER DEFAULT 10 CHECK (min_members_for_claims >= 0),
  ADD COLUMN IF NOT EXISTS min_reserve_percentage NUMERIC(5,2) DEFAULT 20.00 CHECK (min_reserve_percentage >= 0);

-- -----------------------------------------------------------------------------
-- G. DISBURSEMENT / RECONCILIATION / SIMULATION RPCs (11)
--   These existed only in supabase/CONSOLIDATED_SCHEMA_FOR_DASHBOARD.sql and in the
--   previous baseline, never in a master script, so a masters-only rebuild dropped
--   them. They are now restored in 02_FUNCTIONS_AND_TRIGGERS.sql section 10.3 and in
--   GROUND_ZERO v2.2. The app calls complete_payout_v2 and approve_and_disburse_loan_v2
--   from production data sources, and rule 6 requires simulate_bulk_activity_v2.
--
--   Online almost certainly already has all 11 (it was provisioned from the dashboard
--   script). RUN G.0 FIRST:
--     rpc_missing=0 AND rpc_hash_differs=none     -> SKIP G.1; nothing to do.
--     any name absent                             -> run G.1 (adds only those calls).
--     rpc_hash_differs is not none                 -> STOP. Diff that body against master
--       section 10.3 before overwriting a possibly newer cloud hot fix.
--   Expected values produced from the verified Local tier (same expression as G.0):
--     approve_and_disburse_loan_v2       573fa6839c728087ac25b59a3e092d24
--     assert_mfa_for_disbursement_v1     1e415b9e4f7e58375cae704f70c98e8d
--     complete_payout_v2                 f60b158bf22bdada3279df5c06f20206
--     consolidate_group_ledger_v1        d597d93c5eb64a624dbe31029c62d1b6
--     create_ledger_checkpoint_v1        e9b37b504357c5a919fd10e1452ed2f8
--     perform_group_reconciliation_v1    acbf37119472b1eebcb97d08380fbb4d
--     process_rosca_payout_v2            5c9b00d5f769c0d1ea1f5467c939c71c
--     record_loan_settlement_extraction_v1 8df873a1796b98959dbcf939e0c5bd08
--     record_platform_revenue_v1         16f0200ea7aba0d088429050fa635a0a
--     search_members_v1                  cdebc423ace65bf00c38f0f4a1272bb7
--     simulate_bulk_activity_v2          c61b3bb56eb80d2528117eaf2d3a9baa
-- -----------------------------------------------------------------------------

-- G.0 presence + body fingerprint (read-only, self-verifying)
--   body_hash_matches = 11 and rpc_missing = 0  -> the tier already equals master section
--   10.3 exactly; SKIP G.1.
--   rpc_missing > 0                            -> run G.1 (adds the missing calls).
--   rpc_hash_differs lists a name              -> the tier has a DIFFERENT version of that
--     routine. Do NOT paste G.1 blindly: diff it against
--     02_FUNCTIONS_AND_TRIGGERS.sql section 10.3 first, because G.1 would overwrite it.
--   The fingerprint ignores line endings and all whitespace runs, so it is stable across
--   Docker and Dashboard pastes of the same source.
WITH expected(name, hash) AS (VALUES
  ('approve_and_disburse_loan_v2', '573fa6839c728087ac25b59a3e092d24'),
  ('assert_mfa_for_disbursement_v1', '1e415b9e4f7e58375cae704f70c98e8d'),
  ('complete_payout_v2', 'f60b158bf22bdada3279df5c06f20206'),
  ('consolidate_group_ledger_v1', 'd597d93c5eb64a624dbe31029c62d1b6'),
  ('create_ledger_checkpoint_v1', 'e9b37b504357c5a919fd10e1452ed2f8'),
  ('perform_group_reconciliation_v1', 'acbf37119472b1eebcb97d08380fbb4d'),
  ('process_rosca_payout_v2', '5c9b00d5f769c0d1ea1f5467c939c71c'),
  ('record_loan_settlement_extraction_v1', '8df873a1796b98959dbcf939e0c5bd08'),
  ('record_platform_revenue_v1', '16f0200ea7aba0d088429050fa635a0a'),
  ('search_members_v1', 'cdebc423ace65bf00c38f0f4a1272bb7'),
  ('simulate_bulk_activity_v2', 'c61b3bb56eb80d2528117eaf2d3a9baa')
), actual AS (
  SELECT e.name, e.hash AS want,
         (SELECT md5(btrim(regexp_replace(replace(p.prosrc, E'', ''), E'[[:space:]]+', ' ', 'g')))
            FROM pg_proc p JOIN pg_namespace s ON s.oid = p.pronamespace
           WHERE s.nspname = 'public' AND p.proname = e.name) AS got
    FROM expected e
)
-- The CTE is shared, so all three metrics come from one statement.
SELECT 'rpc_missing='   || count(*) FILTER (WHERE got IS NULL),
       'rpc_hash_differs=' || coalesce(string_agg(name, ',' ORDER BY name)
                            FILTER (WHERE got IS NOT NULL AND got <> want), 'none'),
       'body_hash_matches=' || count(*) FILTER (WHERE got = want)
  FROM actual;

-- G.1 the 11 definitions, verbatim from 02_FUNCTIONS_AND_TRIGGERS.sql section 10.3
-- 10.3.1 DISBURSEMENT SECURITY

CREATE OR REPLACE FUNCTION public.assert_mfa_for_disbursement_v1(p_amount NUMERIC)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
BEGIN
    IF ABS(p_amount) >= 10000.00 THEN
        IF NOT public.is_aal2() THEN
            RAISE EXCEPTION '[SEC_001] MFA_REQUIRED' USING
                DETAIL = 'High-value disbursements (>= R10,000) require Multi-Factor Authentication (AAL2).',
                HINT = 'mfa_challenge_required';
        END IF;
    END IF;
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
    SELECT balance_after INTO v_last_balance FROM public.platform_ledger ORDER BY created_at DESC LIMIT 1;
    INSERT INTO public.platform_ledger (transaction_id, amount, balance_after, description, category, group_id)
    VALUES (p_transaction_id, p_amount, COALESCE(v_last_balance, 0) + p_amount, p_description, p_category, p_group_id);
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, pg_temp;


CREATE OR REPLACE FUNCTION public.record_loan_settlement_extraction_v1(
    p_member_id UUID,
    p_group_id UUID,
    p_amount NUMERIC,
    p_loan_id UUID
) RETURNS VOID AS $$
DECLARE
    v_m_name TEXT;
BEGIN
    SELECT full_name INTO v_m_name FROM public.members WHERE id = p_member_id;

    INSERT INTO public.contributions (member_id, group_id, amount, due_date, paid_at, status, type, transaction_id)
    VALUES (p_member_id, p_group_id, -p_amount, CURRENT_DATE, NOW(), 'paid', 'loan_settlement_extraction', 'ext-' || p_loan_id::text);

    PERFORM public.record_loan_repayment_v1(p_loan_id, p_member_id, p_group_id, p_amount, 'wallet');

    INSERT INTO public.group_ledger (group_id, member_id, amount, balance_after, description, category, transaction_id)
    VALUES (p_group_id, p_member_id, 0, (SELECT balance FROM public.groups WHERE id = p_group_id), 'Loan settlement extraction for ' || v_m_name, 'loan_settlement', 'settle-' || p_loan_id::text);

    PERFORM public.log_audit_event('LOAN_FORCED_SETTLEMENT', 'system', p_member_id, p_group_id, jsonb_build_object('loan_id', p_loan_id, 'amount', p_amount), p_loan_id);
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, pg_temp;


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



-- 10.3.2 DISBURSEMENTS

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
    IF v_group.balance < v_payout.amount THEN RAISE EXCEPTION '[FIN_001] NO_CASH: Group balance insufficient for payout.'; END IF;

    UPDATE public.payouts SET status = 'completed', processed_by = v_eff_admin_id, processed_at = NOW(), payout_reference = COALESCE(p_payout_reference, payout_reference), updated_at = NOW() WHERE id = p_payout_id;
    PERFORM public.increment_group_balance(v_payout.group_id, -v_payout.amount, 'Disbursement Ref: ' || COALESCE(p_payout_reference, p_payout_id::text), 'payout', p_payout_id::text, v_payout.member_id);

    PERFORM public.log_audit_event('PAYOUT_COMPLETED', COALESCE(v_eff_admin_id::text, 'system_admin'), v_payout.member_id, v_payout.group_id, jsonb_build_object('payout_id', p_payout_id, 'amount', v_payout.amount), p_payout_id);
END;
$$;


CREATE OR REPLACE FUNCTION public.process_rosca_payout_v2(
    p_group_id UUID,
    p_member_id UUID,
    p_total_pot NUMERIC,
    p_loan_deductions JSONB,
    p_payout_amount NUMERIC,
    p_payout_data JSONB
) RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_loan RECORD;
    v_p_id UUID;
BEGIN
    PERFORM id FROM public.groups WHERE id = p_group_id FOR UPDATE;

    FOR v_loan IN (SELECT * FROM jsonb_to_recordset(p_loan_deductions) AS x(loan_id uuid, amount numeric)) LOOP
        IF v_loan.amount > 0 THEN
            PERFORM public.increment_group_balance(p_group_id, -v_loan.amount, 'ROSCA Offset', 'rosca_payout_offset', 'off-' || v_loan.loan_id::text, p_member_id);
            PERFORM public.record_loan_repayment_v1(v_loan.loan_id, p_member_id, p_group_id, v_loan.amount, 'wallet');
        END IF;
    END LOOP;

    IF p_payout_amount > 0 THEN
        INSERT INTO public.payouts (group_id, member_id, amount, bank_name, account_no, branch_code, status, payout_type)
        VALUES (p_group_id, p_member_id, p_payout_amount, p_payout_data->>'bank_name', p_payout_data->>'account_no', p_payout_data->>'branch_code', 'pending', 'rosca') RETURNING id INTO v_p_id;
    END IF;

    PERFORM public.log_audit_event('ROSCA_PAYOUT_INITIATED', 'system_rosca', p_member_id, p_group_id, jsonb_build_object('pot', p_total_pot, 'net_payout', p_payout_amount), v_p_id);

    RETURN jsonb_build_object('success', true, 'payout_id', v_p_id);
END;
$$;


CREATE OR REPLACE FUNCTION public.perform_group_reconciliation_v1(p_group_id UUID)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_ledger_bal NUMERIC;
    v_actual_bal NUMERIC;
    v_diff NUMERIC;
    v_record RECORD;
BEGIN
    SELECT COALESCE(SUM(amount), 0) INTO v_ledger_bal FROM public.group_ledger WHERE group_id = p_group_id;
    SELECT balance INTO v_actual_bal FROM public.groups WHERE id = p_group_id;
    v_diff := v_actual_bal - v_ledger_bal;
    INSERT INTO public.reconciliations (group_id, period_start, period_end, status, ledger_balance, actual_balance, discrepancy, notes)
    VALUES (p_group_id, NOW() - interval '30 days', NOW(), CASE WHEN ABS(v_diff) < 0.01 THEN 'balanced' ELSE 'discrepancy_found' END, v_ledger_bal, v_actual_bal, v_diff, 'Auto-recon triggered')
    RETURNING * INTO v_record;
    RETURN to_jsonb(v_record);
END;
$$;


CREATE OR REPLACE FUNCTION public.consolidate_group_ledger_v1(p_group_id UUID)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_total INT := 0;
BEGIN
    -- 1. Contributions
    INSERT INTO public.group_ledger (group_id, transaction_id, amount, balance_after, description, category, member_id, created_at)
    SELECT c.group_id, CAST(c.id AS TEXT), c.amount, 0, initcap(replace(c.type, '_', ' ')) || ' from ' || m.full_name, c.type, c.member_id, c.paid_at
    FROM public.contributions c JOIN public.members m ON c.member_id = m.id
    WHERE c.group_id = p_group_id AND c.status = 'paid'
    ON CONFLICT (group_id, transaction_id) DO NOTHING;
    v_total := v_total + (SELECT count(*) FROM public.group_ledger WHERE group_id = p_group_id AND transaction_id LIKE '%-%' AND created_at > NOW() - interval '5 minutes');

    -- 2. Repayments
    INSERT INTO public.group_ledger (group_id, transaction_id, amount, balance_after, description, category, member_id, created_at)
    SELECT lr.group_id, CAST(lr.id AS TEXT), lr.amount, 0, 'Loan repayment from ' || m.full_name, 'loan_repayment', lr.member_id, lr.paid_at
    FROM public.loan_repayments lr JOIN public.members m ON lr.member_id = m.id
    WHERE lr.group_id = p_group_id
    ON CONFLICT (group_id, transaction_id) DO NOTHING;

    -- 3. Disbursements (Payouts)
    INSERT INTO public.group_ledger (group_id, transaction_id, amount, balance_after, description, category, member_id, created_at)
    SELECT p.group_id, CAST(p.id AS TEXT), -p.amount, 0, 'Disbursement Ref: ' || COALESCE(p.payout_reference, p.id::text), 'payout', p.member_id, p.processed_at
    FROM public.payouts p WHERE p.group_id = p_group_id AND p.status = 'completed'
    ON CONFLICT (group_id, transaction_id) DO NOTHING;

    -- 4. Loan Disbursements
    INSERT INTO public.group_ledger (group_id, transaction_id, amount, balance_after, description, category, member_id, created_at)
    SELECT loan.group_id, CAST(loan.id AS TEXT), -loan.amount, 0, 'Loan disbursement to ' || m.full_name, 'loan_disbursement', loan.member_id, COALESCE(loan.reviewed_at, loan.created_at)
    FROM public.loans loan JOIN public.members m ON loan.member_id = m.id
    WHERE loan.group_id = p_group_id AND loan.status NOT IN ('pending', 'rejected', 'cancelled')
    ON CONFLICT (group_id, transaction_id) DO NOTHING;

    -- 5. Burial Claims
    INSERT INTO public.group_ledger (group_id, transaction_id, amount, balance_after, description, category, member_id, created_at)
    SELECT bc.group_id, CAST(bc.id AS TEXT), -bc.claim_amount, 0, 'Burial claim: ' || bc.beneficiary_name, 'claim', bc.member_id, bc.reviewed_at
    FROM public.beneficiary_payout_claims bc WHERE bc.group_id = p_group_id AND bc.status = 'paid'
    ON CONFLICT (group_id, transaction_id) DO NOTHING;

    RETURN jsonb_build_object('entries_reconstructed', v_total);
END;
$$;


-- 10.3.3 LEDGER CHECKPOINTS

CREATE OR REPLACE FUNCTION public.create_ledger_checkpoint_v1(p_group_id UUID)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_latest_entry RECORD;
    v_checkpoint_id UUID;
BEGIN
    -- 1. Find the absolute latest ledger entry for this group
    SELECT * INTO v_latest_entry
    FROM public.group_ledger
    WHERE group_id = p_group_id
    ORDER BY created_at DESC, id DESC
    LIMIT 1;

    IF NOT FOUND THEN
        RAISE EXCEPTION '[SYS_001] NO_LEDGER_ENTRIES: Cannot create checkpoint for an empty ledger.';
    END IF;

    -- 2. Create the checkpoint
    INSERT INTO public.ledger_checkpoints (group_id, ledger_entry_id, balance)
    VALUES (p_group_id, v_latest_entry.id, v_latest_entry.balance_after)
    ON CONFLICT (group_id, ledger_entry_id) DO UPDATE SET balance = EXCLUDED.balance
    RETURNING id INTO v_checkpoint_id;

    PERFORM public.log_audit_event('CHECKPOINT_CREATED', 'system', NULL, p_group_id, jsonb_build_object('ledger_entry_id', v_latest_entry.id, 'balance', v_latest_entry.balance_after), v_checkpoint_id);

    RETURN v_checkpoint_id;
END;
$$;


-- 10.3.4 ADVANCED SEARCH

CREATE OR REPLACE FUNCTION public.search_members_v1(p_query TEXT)
RETURNS SETOF JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
BEGIN
    RETURN QUERY
    SELECT jsonb_build_object(
        'member_id', m.id,
        'member_name', m.full_name,
        'group_id', m.group_id,
        'total_loan_requests', COALESCE(bt.total_loans_requested, 0),
        'overdue_loans', COALESCE(bt.overdue_loans, 0),
        'outstanding_amount', COALESCE(bt.total_outstanding_amount, 0),
        'completion_ratio', COALESCE(bt.loan_completion_rate, 0) / 100.0,
        'risk_band', COALESCE(bt.fraud_risk_level, 'low')
    )
    FROM public.members m
    LEFT JOIN public.member_behavior_track bt ON m.id = bt.member_id
    WHERE m.full_name ILIKE '%' || p_query || '%'
       OR m.id_number ILIKE '%' || p_query || '%'
    LIMIT 20;
END;
$$;


-- 10.3.5 SIMULATION TOOLS

CREATE OR REPLACE FUNCTION public.simulate_bulk_activity_v2(
    p_group_id UUID,
    p_months INT DEFAULT 6,
    p_member_count INT DEFAULT 10
) RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_member_id UUID;
    v_month_idx INT;
    v_member_idx INT;
    v_contrib_amt NUMERIC;
    v_due_date DATE;
    v_res JSONB;
BEGIN
    SELECT monthly_contribution INTO v_contrib_amt FROM public.groups WHERE id = p_group_id;
    FOR v_member_idx IN 1..p_member_count LOOP
        INSERT INTO public.members (group_id, full_name, email, status, joined_at, member_key)
        VALUES (p_group_id, 'Sim Member ' || v_member_idx, 'sim.' || v_member_idx || '.' || p_group_id || '@example.com', 'active', now() - (p_months || ' months')::interval, 'SIM-KEY-' || p_group_id || '-' || v_member_idx)
        ON CONFLICT (group_id, member_key) DO NOTHING;
    END LOOP;
    FOR v_month_idx IN 1..p_months LOOP
        v_due_date := (now() - ((p_months - v_month_idx) || ' months')::interval)::date;
        FOR v_member_id IN SELECT id FROM public.members WHERE group_id = p_group_id LOOP
            IF random() < 0.85 THEN
                PERFORM public.record_contribution_v2(v_member_id, p_group_id, CASE WHEN random() < 0.9 THEN v_contrib_amt ELSE v_contrib_amt / 2 END, v_due_date, v_due_date + interval '12 hours', 'paid', 'sim-bulk-' || v_member_id || '-' || v_month_idx, 'contribution');
            END IF;
        END LOOP;
    END LOOP;
    SELECT public.recalculate_all_group_financials_v3(p_group_id) INTO v_res;
    RETURN jsonb_build_object('status', 'success', 'months_simulated', p_months);
END;
$$;
-- -----------------------------------------------------------------------------
-- V. VERIFY (read-only). Expected on a healthy tier:
--   fee_v2_guard          = true
--   agg_triggers          = 10
--   count_sync_triggers   = 1      (tr_sync_group_member_count only)
--   new_tables            = 4
--   new_columns           = 4
--   total_paid_drift      = 0      (non-zero = member totals out of line with ledger)
--   current_members_drift = 0
--   fee_unique_ok         = 1
-- -----------------------------------------------------------------------------
SELECT 'fee_v2_guard='||coalesce((SELECT (prosrc LIKE '%idempotent%') FROM pg_proc p
  JOIN pg_namespace n ON n.oid=p.pronamespace WHERE n.nspname='public' AND proname='pay_platform_fee_v2'), false)::text AS c1;
SELECT 'agg_triggers='||count(*) AS c2 FROM pg_trigger t
  JOIN pg_class c ON c.oid=t.tgrelid JOIN pg_namespace n ON n.oid=c.relnamespace
  WHERE NOT t.tgisinternal AND n.nspname='public' AND t.tgname IN
  ('tr_sync_member_financials_contrib','tr_sync_loan_repayment_total','tr_member_behavior_init',
   'tr_member_financial_sync_member','tr_update_behavior_metrics','tr_update_behavior_metrics_loans',
   'tr_sync_poll_vote_count','tr_sync_poll_status','tr_poll_audit','tr_vote_audit');
SELECT 'count_sync_triggers='||count(*) AS c3 FROM pg_trigger t
  JOIN pg_class c ON c.oid=t.tgrelid JOIN pg_namespace n ON n.oid=c.relnamespace
  WHERE NOT t.tgisinternal AND n.nspname='public' AND c.relname='members'
  AND t.tgname IN ('tr_sync_group_member_count','tr_member_counter_sync');
SELECT 'new_tables='||count(*) AS c4 FROM information_schema.tables WHERE table_schema='public'
  AND table_name IN ('bank_connections','platform_audit_reports','audit_reports','pending_operations');
SELECT 'new_columns='||count(*) AS c5 FROM information_schema.columns WHERE table_schema='public'
  AND (table_name, column_name) IN (('contributions','late_fee_amount'),('contributions','principal_amount'),
  ('payouts','metadata'),('platform_settings','value_text'));
SELECT 'total_paid_drift='||count(*) AS c6 FROM public.members m WHERE coalesce(m.total_paid,0) <>
  coalesce((SELECT sum(c.amount) FROM public.contributions c WHERE c.member_id=m.id AND c.status='paid'
  AND c.type IN ('contribution','joining_fee','registration_contribution','loan_settlement_extraction')),0);
SELECT 'current_members_drift='||count(*) AS c7 FROM public.groups g WHERE g.current_members <>
  (SELECT count(*) FROM public.members m WHERE m.group_id=g.id AND m.status <> 'suspended');
SELECT 'fee_unique_ok='||count(*) AS c8 FROM pg_constraint WHERE conname =
  'platform_fees_group_id_month_reference_key' AND conrelid='public.platform_fees'::regclass;
NOTIFY pgrst, 'reload schema';
