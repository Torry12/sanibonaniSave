-- SYNC_DELTA_20261106.sql
-- TRIGGER-AND-GUARD DELTA. Four things, all in 02_FUNCTIONS_AND_TRIGGERS.sql and its grant module
-- 03_SECURITY_AND_RLS.sql:
--   1. five aggregate-sync trigger bodies recomputed from the source tables on BOTH sides of the row,
--      with four trigger event sets widened to the writes the app actually performs;
--   2. the duplicated ledger-wrapper guards (record_disbursement_v1 / record_liquidity_v1) hoisted
--      into one assert_ledger_wrapper_input_v1, which is then withdrawn from anon and authenticated;
--   3. two superseded RPCs dropped outright - record_platform_revenue_v1 and
--      record_loan_settlement_extraction_v1;
--   4. a targeted repair pass (P.3) for rows the old triggers already left wrong, since a fixed
--      trigger only heals from the next write onwards.
--
-- The defects, each reachable from shipped client code:
--   * tr_sync_poll_vote_count was AFTER INSERT OR DELETE and incremented/decremented one side.
--     VotingRepositoryImpl.castVote UPSERTs group_poll_votes on its UNIQUE(poll_id, member_id) key,
--     so a member changing their option is an UPDATE that answered nothing at all: the option left
--     behind kept a vote it no longer had and the option taken did not gain one. Same function also
--     mis-counted when a DELETE raced a partial row. The body is now a derived COUNT(*) on both the
--     NEW and the OLD option, and the trigger fires on UPDATE too.
--   * tr_vote_audit was AFTER INSERT only, so the same re-pointed vote left no server-side record of
--     itself. It now fires AFTER INSERT OR UPDATE OF option_id and logs VOTE_CHANGED with the
--     previous option in the payload.
--   * fn_sync_group_member_count, fn_trigger_sync_member_financials and
--     fn_trigger_update_behavior_metrics each read COALESCE(NEW.<key>, OLD.<key>) - one side of the
--     row. Re-parenting a member, re-assigning a contribution or moving a contribution between members
--     therefore recounted only the destination: the origin kept a member it no longer has, a total_paid
--     for money now credited to somebody else, and a behaviour penalty for contributions that are no
--     longer its own. All three now take the same two-IF shape fn_trigger_sync_loan_repayment_total
--     has always used, keyed on IS DISTINCT FROM.
--   * the two behaviour-metrics triggers were AFTER INSERT OR UPDATE, while the sync layer purges
--     orphaned contributions and repayments on every verified read - a DELETE rescored nobody. Both
--     event sets now include DELETE, and the body loops over every member the row touched.
--
-- What this delta does NOT relax:
--   * the money. It writes no ledger row and touches no groups.balance. P.0.f / P.2.e / P.3.f measure
--     the same totals three times to prove it; the only data P.3 may write is an aggregate that is
--     already demonstrably wrong.
--   * entitlement. Both wrappers keep raising the same [SEC_002] code for the same caller as before;
--     the guard moved, it did not weaken. assert_ledger_wrapper_input_v1 is revoked from PUBLIC, anon
--     and authenticated and granted to service_role only, because the blanket
--     "GRANT EXECUTE ON ALL FUNCTIONS ... TO anon, authenticated, service_role" at
--     03_SECURITY_AND_RLS.sql:38 and the ALTER DEFAULT PRIVILEGES at :41 would otherwise make every
--     new function - this one included - anonymously callable over HTTP. The wrappers themselves are
--     unchanged in that respect: a group admin still reaches them through the app.
--
-- Tier state before the run: NOT measured by the author. No credential was used to write this file
-- and nothing here has been executed on either tier. The expected P.0.a hashes are the bodies the
-- masters carried before this refactor (git HEAD of supabase/02_FUNCTIONS_AND_TRIGGERS.sql); a tier
-- current through SYNC_DELTA_20261104 reads those five hashes, no helper, and both doomed RPCs
-- present. Run P.0 on the tier you are about to write and confirm it answers that way before P.1.
--
-- Re-runnable: every P.1 statement is CREATE OR REPLACE, DROP ... IF EXISTS, or an idempotent
-- REVOKE/GRANT pair, so applying the section twice leaves the same definitions. P.3 is a no-op on a
-- tier whose aggregates already agree with their source rows.
--
-- Rollback: R.1 at the bottom restores the four trigger event sets. The function bodies roll back by
-- re-applying the master as it stood before this delta - `git show HEAD:supabase/02_FUNCTIONS_AND_TRIGGERS.sql`
-- at the commit that precedes it - and the eight old hashes in R.1 are what proves you restored the
-- right text. The two dropped RPCs are deliberately NOT restored: their existence is the defect (see
-- P.5), and a tier that needs one back is a tier that has not been read.

-- ──────────────────────────────────────────────────────────────────────────────
-- P.0  PRE-FLIGHT - READ ONLY. Run each on its own, on the tier you are about to write.
-- ──────────────────────────────────────────────────────────────────────────────

-- P.0.a  The eight body fingerprints this delta replaces or removes, plus the one it adds.
--        Expect, on a tier current through 20261104:
--          fn_sync_group_member_count         644fa3272a8c4228f897fb0af573434d
--          fn_trigger_sync_member_financials  9638bd2649f48f3efa425999b2c455ff
--          fn_trigger_update_behavior_metrics 43cf492560b8144147ad00061a9756e7
--          fn_trigger_sync_poll_vote_count    61a74696c9fb714734cf8daa6d2cbf9e
--          fn_trigger_voting_audit            0ad1a5043119c542094500ae3d45fd93
--          record_disbursement_v1             729042ffce49ecfe608e19b7621f0462
--          record_liquidity_v1                f01eff7792049d78589f87453a46e38e
--          record_platform_revenue_v1         92b3c4af0d00a0e85100f4b53c259186   (about to be dropped)
--          record_loan_settlement_extraction_v1 8df873a1796b98959dbcf939e0c5bd08 (about to be dropped)
--          assert_ledger_wrapper_input_v1     0 rows - it does not exist yet.
--        If the first five already read the NEW hashes listed in P.2.a, this delta has run here; skip P.1.
select p.proname || '|'
    || md5(btrim(regexp_replace(replace(p.prosrc, E'\r', ''), E'[[:space:]]+', ' ', 'g'))) as line
  from pg_proc p join pg_namespace n on n.oid = p.pronamespace
 where n.nspname = 'public'
   and p.proname in ('fn_sync_group_member_count',
                     'fn_trigger_sync_member_financials',
                     'fn_trigger_update_behavior_metrics',
                     'fn_trigger_sync_poll_vote_count',
                     'fn_trigger_voting_audit',
                     'record_disbursement_v1',
                     'record_liquidity_v1',
                     'record_platform_revenue_v1',
                     'record_loan_settlement_extraction_v1',
                     'assert_ledger_wrapper_input_v1')
 order by 1;

-- P.0.b  Overloads. Every name below must answer 1: a CREATE OR REPLACE against a name with two
--        signatures creates a third instead of replacing the one the app calls. Expect 1 for all
--        eight, and assert_ledger_wrapper_input_v1 absent from this result.
select p.proname, count(*) as overloads
  from pg_proc p join pg_namespace n on n.oid = p.pronamespace
 where n.nspname = 'public'
   and p.proname in ('fn_sync_group_member_count',
                     'fn_trigger_sync_member_financials',
                     'fn_trigger_update_behavior_metrics',
                     'fn_trigger_sync_poll_vote_count',
                     'fn_trigger_voting_audit',
                     'record_disbursement_v1',
                     'record_liquidity_v1',
                     'assert_ledger_wrapper_input_v1')
 group by 1 order by 1;

-- P.0.c  The trigger event sets as the tier has them today. Expect exactly the old text:
--          tr_sync_poll_vote_count        AFTER INSERT OR DELETE
--          tr_vote_audit                  AFTER INSERT
--          tr_update_behavior_metrics     AFTER INSERT OR UPDATE
--          tr_update_behavior_metrics_loans AFTER INSERT OR UPDATE
--        and unchanged, listed here so a surprise on either of them is not missed:
--          tr_sync_group_member_count     AFTER INSERT OR UPDATE OR DELETE
--          tr_sync_member_financials_contrib AFTER INSERT OR UPDATE OR DELETE
--        A seventh trigger on any of these tables that this delta does not know about means stop and
--        read it before writing.
select c.relname, t.tgname, pg_get_triggerdef(t.oid) as definition
  from pg_trigger t
  join pg_class c on c.oid = t.tgrelid
  join pg_namespace n on n.oid = c.relnamespace
 where n.nspname = 'public'
   and c.relname in ('group_poll_votes', 'members', 'contributions', 'loan_repayments')
   and not t.tgisinternal
 order by 1, 2;

-- P.0.d  Who can still execute the two RPCs being dropped, and what depends on them. Both are
--        already almost unreachable, which is the evidence that nothing calls them:
--        record_platform_revenue_v1's proacl should name only the owner and service_role (the block
--        at 03_SECURITY_AND_RLS.sql:107-125 withdrew it from PUBLIC, anon and authenticated), and
--        record_loan_settlement_extraction_v1's should name the owner alone - the retire block that
--        20261029 added took it from every role including service_role, so it has been an
--        unreachable body on both tiers since. A proacl of NULL on either one means the withdraw
--        never ran here; that changes nothing about the DROP, but it does mean this tier is not the
--        one the record describes. The view check must answer 0 rows: a view that selects from a
--        dropped function fails at next refresh, not at DROP time.
select proname, proacl from pg_proc
 where proname in ('record_platform_revenue_v1', 'record_loan_settlement_extraction_v1');

select viewname from pg_views
 where definition ilike '%record_platform_revenue_v1%'
    or definition ilike '%record_loan_settlement_extraction_v1%';

-- P.0.e  The live drift the old triggers leave behind, counted rather than assumed. These are the
--        rows P.3 repairs; a healthy tier answers all zeros and P.3 writes nothing.
--        Recorded here because the number changes what the delta means: non-zero is proof the old
--        triggers fired at all, zero on a tier with real votes is not.
select (select count(*) from public.group_poll_options)                                  as options_total,
       (select count(*) from public.group_poll_options o
         where o.vote_count is distinct from
               (select count(*) from public.group_poll_votes v where v.option_id = o.id)) as options_stale,
       (select count(*) from public.groups)                                               as groups_total,
       (select count(*) from public.groups g
         where g.current_members is distinct from
               (select count(*) from public.members m
                 where m.group_id = g.id and m.status <> 'suspended'))                     as groups_stale,
       (select count(*) from public.members m
         where m.total_paid is distinct from
               (select coalesce(sum(c.amount), 0) from public.contributions c
                 where c.member_id = m.id and c.status = 'paid'
                   and c.type in ('contribution','joining_fee','registration_contribution',
                                  'loan_settlement_extraction')))                          as members_total_paid_stale,
       (select count(*) from public.members m
         where m.total_contributions is distinct from
               (select count(*) from public.contributions c
                 where c.member_id = m.id and c.status = 'paid'
                   and c.type in ('contribution','registration_contribution')))            as members_contribution_count_stale,
       (select count(*) from public.member_behavior_track t
         where t.behavior_score is distinct from
               (greatest(0, 100.0 - 10.0 * (select count(*) from public.contributions c
                                             where c.member_id = t.member_id
                                               and c.status = 'overdue')))::numeric(5,2))  as behavior_score_stale,
       (select count(*) from public.members m
         where not exists (select 1 from public.member_behavior_track t
                            where t.member_id = m.id))                                     as members_without_behavior_row;

--      The last column is reported, not repaired: tr_member_behavior_init only inserts on a member
--      INSERT, so a member seeded or imported before that trigger existed has no row for the
--      behaviour trigger to score. P.5 carries it as an open item.

-- P.0.f  The money, which this delta must not move. Record all six numbers; P.2.e and P.3.f repeat
--        them and any difference is a reason to stop.
select (select count(*) from public.groups)                                        as groups_total,
       (select count(*) from public.group_ledger)                                  as ledger_rows,
       (select count(*) from public.platform_ledger)                               as platform_rows,
       (select coalesce(sum(balance), 0)::numeric(14,2) from public.groups)        as group_balance_total,
       (select coalesce(sum(amount), 0)::numeric(14,2) from public.platform_ledger) as platform_revenue_total,
       (select count(*) from public.groups g where g.balance <> coalesce(
          (select sum(l.amount) from public.group_ledger l where l.group_id = g.id), 0)) as balance_drifts;

-- P.0.g  The role that will run P.1 owns the functions (CREATE OR REPLACE needs ownership) and may
--        drop them. Expect postgres / true / true.
select current_user as role,
       (select has_function_privilege(current_user,
            'public.record_disbursement_v1(uuid,numeric,text,text,text,uuid)', 'EXECUTE')) as can_execute_wrapper,
       (select count(*) from pg_proc p join pg_namespace n on n.oid = p.pronamespace
         where n.nspname = 'public' and p.proname = 'increment_group_balance') as primitive_present;

-- ──────────────────────────────────────────────────────────────────────────────
-- P.1  WRITE. Verbatim from supabase/02_FUNCTIONS_AND_TRIGGERS.sql and
--      supabase/03_SECURITY_AND_RLS.sql as of this delta. Run the statements in order; P.1.c must
--      come after P.1.b, because the helper is created anon-executable by the default privileges and
--      stays that way until the REVOKE runs.

-- P.1.a  The five aggregate-sync bodies. 02_FUNCTIONS_AND_TRIGGERS.sql sections 8, 10.1 and 10.1.1.

CREATE OR REPLACE FUNCTION public.fn_sync_group_member_count()
RETURNS TRIGGER AS $$
BEGIN
    -- Both sides of the row, not COALESCE(NEW.group_id, OLD.group_id): moving a member between
    -- groups is an UPDATE, and a one-sided recount leaves the origin reporting a member it no
    -- longer has. fn_trigger_sync_loan_repayment_total already handles its old key this way.
    IF (TG_OP = 'INSERT' OR TG_OP = 'UPDATE') THEN
        UPDATE public.groups SET current_members = (SELECT count(*) FROM public.members WHERE group_id = NEW.group_id AND status != 'suspended')
        WHERE id = NEW.group_id;
    END IF;
    IF (TG_OP = 'DELETE' OR (TG_OP = 'UPDATE' AND OLD.group_id IS DISTINCT FROM NEW.group_id)) THEN
        UPDATE public.groups SET current_members = (SELECT count(*) FROM public.members WHERE group_id = OLD.group_id AND status != 'suspended')
        WHERE id = OLD.group_id;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION public.fn_trigger_sync_member_financials() RETURNS TRIGGER AS $$
BEGIN
    -- Both sides of the row. COALESCE(NEW.member_id, OLD.member_id) answered only where the
    -- contribution landed, so re-assigning one left the origin member with a total_paid that
    -- still counted money now credited to somebody else.
    IF (TG_OP = 'INSERT' OR TG_OP = 'UPDATE') THEN
        PERFORM public.fn_sync_single_member_financials(NEW.member_id);
    END IF;
    IF (TG_OP = 'DELETE' OR (TG_OP = 'UPDATE' AND OLD.member_id IS DISTINCT FROM NEW.member_id)) THEN
        PERFORM public.fn_sync_single_member_financials(OLD.member_id);
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

CREATE OR REPLACE FUNCTION public.fn_trigger_update_behavior_metrics() RETURNS TRIGGER AS $$
DECLARE
    v_m_id UUID;
    v_score NUMERIC(5,2);
    v_risk TEXT;
    v_late_count INT;
BEGIN
    -- Every member this row touched, on every event that can change their overdue count. The body
    -- used to read COALESCE(NEW.member_id, OLD.member_id), so a re-assigned row rescored only the
    -- member who gained it and left the other member carrying the penalty for contributions that
    -- are no longer theirs - and DELETE was not in the event set at all, while the sync layer
    -- purges orphaned contributions on every verified read.
    FOR v_m_id IN
        SELECT id FROM (
            SELECT NEW.member_id AS id WHERE TG_OP IN ('INSERT', 'UPDATE')
            UNION
            SELECT OLD.member_id WHERE TG_OP IN ('DELETE', 'UPDATE') AND OLD.member_id IS DISTINCT FROM NEW.member_id
        ) touched
         WHERE id IS NOT NULL
    LOOP
        v_score := 100.0;
        v_risk := 'low';

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
    END LOOP;

    RETURN NULL;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

CREATE OR REPLACE FUNCTION public.fn_trigger_sync_poll_vote_count() RETURNS TRIGGER AS $$
BEGIN
    -- Recounted from COUNT(*) on both touched options rather than incremented on one side, and
    -- fired on UPDATE as well: castVote UPSERTs the vote on its (poll_id, member_id) key, so a
    -- member changing their option is an UPDATE that used to answer nothing at all, and the
    -- option they left kept a vote it no longer had.
    IF (TG_OP = 'INSERT' OR TG_OP = 'UPDATE') THEN
        UPDATE public.group_poll_options SET vote_count = (SELECT count(*) FROM public.group_poll_votes WHERE option_id = NEW.option_id) WHERE id = NEW.option_id;
    END IF;
    IF (TG_OP = 'DELETE' OR (TG_OP = 'UPDATE' AND OLD.option_id IS DISTINCT FROM NEW.option_id)) THEN
        UPDATE public.group_poll_options SET vote_count = (SELECT count(*) FROM public.group_poll_votes WHERE option_id = OLD.option_id) WHERE id = OLD.option_id;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

CREATE OR REPLACE FUNCTION public.fn_trigger_voting_audit() RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        IF TG_TABLE_NAME = 'group_polls' THEN
            PERFORM public.log_audit_event('POLL_CREATED', NEW.created_by_member_id::text, NULL, NEW.group_id, jsonb_build_object('poll_id', NEW.id, 'title', NEW.title), NEW.id);
        ELSIF TG_TABLE_NAME = 'group_poll_votes' THEN
            PERFORM public.log_audit_event('VOTE_CAST', NEW.member_id::text, NEW.member_id, NEW.group_id, jsonb_build_object('poll_id', NEW.poll_id), NEW.poll_id);
        END IF;
    ELSIF TG_OP = 'UPDATE' AND TG_TABLE_NAME = 'group_poll_votes'
          AND OLD.option_id IS DISTINCT FROM NEW.option_id THEN
        -- A re-pointed vote arrives as an UPDATE on the (poll_id, member_id) key, so without this
        -- branch a member who changed their mind left no server-side record of voting at all.
        PERFORM public.log_audit_event('VOTE_CHANGED', NEW.member_id::text, NEW.member_id, NEW.group_id,
            jsonb_build_object('poll_id', NEW.poll_id, 'previous_option_id', OLD.option_id, 'option_id', NEW.option_id), NEW.poll_id);
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, pg_temp;

-- P.1.b  The wrapper guards. The helper first (the wrappers reference it), then the two wrappers
--        reduced to the call. 02_FUNCTIONS_AND_TRIGGERS.sql section 10.3.2.3.

CREATE OR REPLACE FUNCTION public.assert_ledger_wrapper_input_v1(
    p_group_id UUID,
    p_amount NUMERIC,
    p_description TEXT,
    p_category TEXT,
    p_noun TEXT,
    p_access_denied_code TEXT
) RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
BEGIN
    -- A ledger injection is a decision about the group's money, so it is an admin action: unlike
    -- the contribution path there is no is_own_member allowance here, and a member calling it for
    -- their own group is refused.
    IF auth.role() IS DISTINCT FROM 'service_role'
       AND NOT (public.is_platform_admin() OR public.is_group_admin(p_group_id)) THEN
        RAISE EXCEPTION '[SEC_002] %', p_access_denied_code;
    END IF;

    IF p_amount IS NULL OR p_amount::TEXT IN ('NaN', 'Infinity', '-Infinity')
       OR p_amount <= 0 OR p_amount <> ROUND(p_amount, 2) THEN
        RAISE EXCEPTION '[FIN_003] INVALID_AMOUNT: % must be positive and have at most two decimal places', p_noun;
    END IF;
    IF p_description IS NULL OR BTRIM(p_description) = '' THEN
        RAISE EXCEPTION '[FIN_003] INVALID_DESCRIPTION: A ledger description is required';
    END IF;
    -- group_ledger.category has no CHECK constraint, so the vocabulary is whatever the writers
    -- pass; only a blank is refused here, not an unlisted value.
    IF p_category IS NULL OR BTRIM(p_category) = '' THEN
        RAISE EXCEPTION '[FIN_003] INVALID_CATEGORY: A ledger category is required';
    END IF;
END;
$$;

CREATE OR REPLACE FUNCTION public.record_disbursement_v1(
    p_group_id UUID,
    p_amount NUMERIC,
    p_description TEXT,
    p_category TEXT,
    p_transaction_id TEXT DEFAULT NULL,
    p_member_id UUID DEFAULT NULL
) RETURNS NUMERIC
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
BEGIN
    PERFORM public.assert_ledger_wrapper_input_v1(
        p_group_id, p_amount, p_description, p_category, 'Disbursement', 'DISBURSEMENT_ACCESS_DENIED');

    -- Positive on the wire, negative in the ledger: the client's own guard is "Disbursement amount
    -- must be positive", and the returned balance is what GroupRepositoryImpl caches as Money.
    -- The primitive owns the FOR UPDATE group lock, the idempotency key and the insufficient-funds
    -- check ([FIN_002] INSUFFICIENT_FUNDS), so this wrapper adds the caller check and the sign and
    -- nothing else.
    RETURN public.increment_group_balance(
        p_group_id,
        -p_amount,
        p_description,
        p_category,
        p_transaction_id,
        p_member_id
    );
END;
$$;

CREATE OR REPLACE FUNCTION public.record_liquidity_v1(
    p_group_id UUID,
    p_amount NUMERIC,
    p_description TEXT,
    p_category TEXT,
    p_transaction_id TEXT DEFAULT NULL,
    p_member_id UUID DEFAULT NULL
) RETURNS NUMERIC
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
BEGIN
    -- The inflow twin of record_disbursement_v1. 20261029 withdrew increment_group_balance from
    -- `authenticated`, which closed the only HTTP route by which a signed-in group admin could
    -- book money that is not a contribution: bank interest, a dividend, an opening balance, or
    -- the simulation harness's seed liquidity. Seeding through record_contribution_v2 instead
    -- would not do, because a contribution row also moves totalContributions, which is the unit
    -- denominator in CalculateInvestmentClubValuationUseCase - the injected capital would stop
    -- being capital and start being a unit purchase.
    PERFORM public.assert_ledger_wrapper_input_v1(
        p_group_id, p_amount, p_description, p_category, 'Liquidity', 'LIQUIDITY_ACCESS_DENIED');

    -- Positive here and positive in the ledger: this is an inflow, and the wrapper keeps the
    -- caller-facing sign convention the client's guards already assume.
    -- The primitive owns the FOR UPDATE group lock, the idempotency key and the ledger write, so
    -- this wrapper adds the caller check and the sign and nothing else.
    RETURN public.increment_group_balance(
        p_group_id,
        p_amount,
        p_description,
        p_category,
        p_transaction_id,
        p_member_id
    );
END;
$$;

-- P.1.c  Withdraw the new helper from the roles the blanket grant handed it to. Same shape as the
--        increment_group_balance block it sits beside in 03_SECURITY_AND_RLS.sql:101-120.
REVOKE EXECUTE ON FUNCTION public.assert_ledger_wrapper_input_v1(UUID, NUMERIC, TEXT, TEXT, TEXT, TEXT)
FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.assert_ledger_wrapper_input_v1(UUID, NUMERIC, TEXT, TEXT, TEXT, TEXT)
TO service_role;

-- P.1.d  The four widened trigger event sets (master lines 1270, 1309, 1248, 1251). The two event
--        sets that were already complete - tr_sync_group_member_count and
--        tr_sync_member_financials_contrib, lines 790 and 1151 - are NOT re-issued here: their bodies
--        changed and their schedules did not, and dropping a working trigger to recreate an identical
--        definition only opens a window in which a write goes unsynchronised. DROP IF EXISTS makes
--        each pair below re-runnable.
DROP TRIGGER IF EXISTS tr_sync_poll_vote_count ON public.group_poll_votes;
CREATE TRIGGER tr_sync_poll_vote_count AFTER INSERT OR UPDATE OR DELETE ON public.group_poll_votes FOR EACH ROW EXECUTE FUNCTION public.fn_trigger_sync_poll_vote_count();

DROP TRIGGER IF EXISTS tr_vote_audit ON public.group_poll_votes;
CREATE TRIGGER tr_vote_audit AFTER INSERT OR UPDATE OF option_id ON public.group_poll_votes FOR EACH ROW EXECUTE FUNCTION public.fn_trigger_voting_audit();

DROP TRIGGER IF EXISTS tr_update_behavior_metrics ON public.contributions;
CREATE TRIGGER tr_update_behavior_metrics AFTER INSERT OR UPDATE OR DELETE ON public.contributions FOR EACH ROW EXECUTE FUNCTION public.fn_trigger_update_behavior_metrics();

DROP TRIGGER IF EXISTS tr_update_behavior_metrics_loans ON public.loan_repayments;
CREATE TRIGGER tr_update_behavior_metrics_loans AFTER INSERT OR UPDATE OR DELETE ON public.loan_repayments FOR EACH ROW EXECUTE FUNCTION public.fn_trigger_update_behavior_metrics();

-- P.1.e  Drop the two superseded RPCs. record_platform_revenue_v1 computes platform_ledger
--        balance_after a different way from record_platform_revenue_v2, so the two writers of one
--        table disagree about what a running total is. record_loan_settlement_extraction_v1 is the
--        pre-v2 settlement path: it inserts the negative loan_settlement_extraction contribution and
--        calls record_loan_repayment_v1 for the same money, then writes its own group_ledger row
--        with amount 0 - an entry that describes an outflow and moves nothing, while
--        settle_expired_loan_v2 books the identical extraction through increment_group_balance at
--        the real -v_remaining (02_FUNCTIONS_AND_TRIGGERS.sql:1493-1506). So its ledger and its
--        contributions disagree with each other, which is exactly what consolidate_group_ledger_v1
--        would then "repair" into a different number than the cached balance, and calling it over an
--        already-settled loan repays the loan twice. Neither has a caller: grepping every .rpc(...)
--        and postgrest[...] target in :data, every seed file, and every master answers zero for both
--        names. CASCADE is not needed - P.0.d proved nothing depends on them - so the plain DROP is
--        the one that fails loudly instead of silently taking a view with it.
DROP FUNCTION IF EXISTS public.record_platform_revenue_v1(NUMERIC, TEXT, TEXT, TEXT, UUID);
DROP FUNCTION IF EXISTS public.record_loan_settlement_extraction_v1(UUID, UUID, NUMERIC, UUID);

-- P.1.z  PostgREST caches both signatures and bodies; without this the app keeps calling what it
--        loaded at startup, and a dropped RPC keeps answering as if it were still there.
select pg_notify('pgrst', 'reload schema');

-- ──────────────────────────────────────────────────────────────────────────────
-- P.2  VERIFY - read-only, after P.1.

-- P.2.a  Expect the eight new hashes and the absence of the two dropped names:
--          assert_ledger_wrapper_input_v1     99d82801aeca70c2a5c77a8b62c610f4
--          fn_sync_group_member_count         48385aff724c5557fe435fc55ac61c14
--          fn_trigger_sync_member_financials  4f1dfefd8c904b0943c0b438317e9e81
--          fn_trigger_update_behavior_metrics b8f61e743b0416d02835e6200a10edb2
--          fn_trigger_sync_poll_vote_count    68e4ecde080a603812f59a3b208651fe
--          fn_trigger_voting_audit            0d7c5181d37d751bc5c46432b55cc3f1
--          record_disbursement_v1             affc47b8bce7565ccd1ad8d6f6697159
--          record_liquidity_v1                86eae9682f52455454a1ea08ad50cd28
--        These are computed from the master the same way the query normalises prosrc (CRs dropped,
--        every whitespace run collapsed, trimmed), so a match proves the paste did not mangle the SQL
--        and a mismatch is a transcription error, not a tier-state problem.
select p.proname || '|'
    || md5(btrim(regexp_replace(replace(p.prosrc, E'\r', ''), E'[[:space:]]+', ' ', 'g'))) as line
  from pg_proc p join pg_namespace n on n.oid = p.pronamespace
 where n.nspname = 'public'
   and p.proname in ('fn_sync_group_member_count',
                     'fn_trigger_sync_member_financials',
                     'fn_trigger_update_behavior_metrics',
                     'fn_trigger_sync_poll_vote_count',
                     'fn_trigger_voting_audit',
                     'record_disbursement_v1',
                     'record_liquidity_v1',
                     'assert_ledger_wrapper_input_v1',
                     'record_platform_revenue_v1',
                     'record_loan_settlement_extraction_v1')
 order by 1;

-- P.2.b  The event sets, which are part of the fix and not carried by the body hashes. Expect the
--        four new ones exactly as P.1.d wrote them, and the two unchanged ones still intact.
select c.relname, t.tgname, pg_get_triggerdef(t.oid) as definition
  from pg_trigger t
  join pg_class c on c.oid = t.tgrelid
  join pg_namespace n on n.oid = c.relnamespace
 where n.nspname = 'public'
   and c.relname in ('group_poll_votes', 'members', 'contributions', 'loan_repayments')
   and not t.tgisinternal
 order by 1, 2;

-- P.2.c  The helper is not reachable over HTTP, proved with has_function_privilege rather than by
--        calling it - never probe a SECURITY DEFINER function as anon on Local, it crashes the
--        postmaster. Expect false / false / true for the helper (anon, authenticated, service_role).
select has_function_privilege('anon',
        'public.assert_ledger_wrapper_input_v1(uuid,numeric,text,text,text,text)', 'EXECUTE')          as anon,
       has_function_privilege('authenticated',
        'public.assert_ledger_wrapper_input_v1(uuid,numeric,text,text,text,text)', 'EXECUTE')          as authenticated,
       has_function_privilege('service_role',
        'public.assert_ledger_wrapper_input_v1(uuid,numeric,text,text,text,text)', 'EXECUTE')          as service_role,
       has_function_privilege('authenticated',
        'public.record_disbursement_v1(uuid,numeric,text,text,text,uuid)', 'EXECUTE')                  as wrapper_still_signed_in,
       has_function_privilege('anon',
        'public.record_disbursement_v1(uuid,numeric,text,text,text,uuid)', 'EXECUTE')                  as wrapper_anon;

--      The fourth column must stay true and the fifth stay false: the wrapper is the signed-in
--      admin's route - section 2d of 03_SECURITY_AND_RLS.sql withdrew it from anon and left it to
--      authenticated and service_role, and this delta changes neither - and the helper behind it is
--      called with the wrapper owner's rights, so tightening the wrapper here would break the app's
--      disbursement and the harness's seed liquidity alike while widening it would move money.

-- P.2.d  Both dropped names answer 0 rows, and the vocabulary that moved into the helper is still
--        present on the reachable path. Expect 0 / 0 / 8 - this is section D of
--        maintenance/TRIGGER_AGGREGATE_PROBE.sql, run read-only here so a tier can be checked
--        without a transaction that writes.
select (select count(*) from pg_proc where proname = 'record_platform_revenue_v1') as dead_revenue_v1,
       (select count(*) from pg_proc where proname = 'record_loan_settlement_extraction_v1') as dead_settlement_v1,
       (select count(*) from (
            select 1 from pg_proc x, (values
                ('assert_ledger_wrapper_input_v1', '[SEC_002] %'),
                ('assert_ledger_wrapper_input_v1', 'INVALID_AMOUNT: % must be positive and have at most two decimal places'),
                ('assert_ledger_wrapper_input_v1', 'INVALID_DESCRIPTION: A ledger description is required'),
                ('assert_ledger_wrapper_input_v1', 'INVALID_CATEGORY: A ledger category is required'),
                ('record_disbursement_v1',         '''DISBURSEMENT_ACCESS_DENIED'''),
                ('record_disbursement_v1',         '''Disbursement'''),
                ('record_liquidity_v1',            '''LIQUIDITY_ACCESS_DENIED'''),
                ('record_liquidity_v1',            '''Liquidity''')
            ) need(fn, token)
             where x.proname = need.fn and position(need.token in x.prosrc) > 0
        ) found);

-- P.2.e  The money, unchanged: same six numbers as P.0.f. Replacing bodies and dropping two unused
--        RPCs moves nothing. Any difference stops the run.
select (select count(*) from public.groups)                                        as groups_total,
       (select count(*) from public.group_ledger)                                  as ledger_rows,
       (select count(*) from public.platform_ledger)                               as platform_rows,
       (select coalesce(sum(balance), 0)::numeric(14,2) from public.groups)        as group_balance_total,
       (select coalesce(sum(amount), 0)::numeric(14,2) from public.platform_ledger) as platform_revenue_total,
       (select count(*) from public.groups g where g.balance <> coalesce(
          (select sum(l.amount) from public.group_ledger l where l.group_id = g.id), 0)) as balance_drifts;

-- P.2.f  Re-run P.0.e verbatim. Expect the SAME drift numbers: a fixed trigger heals from the next
--        write onward, so existing wrong rows are still wrong until P.3. If they already all read 0,
--        skip P.3 - this tier has no aggregate to repair.

-- ──────────────────────────────────────────────────────────────────────────────
-- P.3  REPAIR - WRITES, and the only data this delta changes. Each statement touches rows whose
--      cached aggregate disagrees with its source rows, so on a healthy tier every one of them
--      matches zero rows and writes nothing. Run when the tier is quiet. Two of them are direct
--      aggregate UPDATEs, one calls the live member-financials primitive, one re-scores through the
--      same heuristic the behaviour trigger now applies. group_poll_options, member_behavior_track
--      and platform/group ledgers carry no triggers, so none of these statements cascades; the one
--      exception is P.3.c, whose members UPDATE re-fires tr_sync_group_member_count for that
--      member's group - an idempotent recount, and the same one the trigger would have done.

-- P.3.a  Poll options: write the derived recount.
update public.group_poll_options o
   set vote_count = sub.n
  from (select o2.id, (select count(*) from public.group_poll_votes v where v.option_id = o2.id) as n
          from public.group_poll_options o2) sub
 where o.id = sub.id
   and o.vote_count is distinct from sub.n;

-- P.3.b  Groups: same predicate the fixed fn_sync_group_member_count uses, including its
--        status <> 'suspended' filter.
update public.groups g
   set current_members = sub.n
  from (select g2.id,
               (select count(*) from public.members m
                 where m.group_id = g2.id and m.status != 'suspended') as n
          from public.groups g2) sub
 where g.id = sub.id
   and g.current_members is distinct from sub.n;

-- P.3.c  Members: repaired through the live primitive rather than a copy of its aggregates, so a
--        drift here is fixed exactly the way the trigger would have fixed it. Members whose cached
--        totals already agree are not touched, which is why this does not churn updated_at across
--        the member table the way an unconditional replay would.
--        First the count, then the calls. The call statement keeps the function in a top-level
--        target list on purpose: a planner may prune an unreferenced column out of a subquery, and a
--        pruned column here would silently skip the repair. The grid it returns is one empty row per
--        member corrected, so the row count IS the answer to the query above.
select count(*) as members_to_repair from public.members m
 where m.total_paid is distinct from
       (select coalesce(sum(c.amount), 0) from public.contributions c
         where c.member_id = m.id and c.status = 'paid'
           and c.type in ('contribution','joining_fee','registration_contribution','loan_settlement_extraction'))
    or m.total_contributions is distinct from
       (select count(*) from public.contributions c
         where c.member_id = m.id and c.status = 'paid'
           and c.type in ('contribution','registration_contribution'));

select public.fn_sync_single_member_financials(m.id)
  from public.members m
 where m.total_paid is distinct from
       (select coalesce(sum(c.amount), 0) from public.contributions c
         where c.member_id = m.id and c.status = 'paid'
           and c.type in ('contribution','joining_fee','registration_contribution','loan_settlement_extraction'))
    or m.total_contributions is distinct from
       (select count(*) from public.contributions c
         where c.member_id = m.id and c.status = 'paid'
           and c.type in ('contribution','registration_contribution'));

-- P.3.d  Behaviour scores. There is no primitive for this one - the heuristic lives inside the
--        trigger body - so this statement restates it. That duplication is deliberate and bounded:
--        it is the only place outside 02_FUNCTIONS_AND_TRIGGERS.sql where the 10-points-per-overdue
--        rule is written, and P.5 asks for a recompute function so neither path has to.
--        It updates member_behavior_track rows that exist; a member with no track row stays
--        unscored, which P.0.e counted and P.5 carries.
with truth as (
    select t.member_id,
           (greatest(0, 100.0 - 10.0 * (select count(*) from public.contributions c
                                         where c.member_id = t.member_id and c.status = 'overdue')))::numeric(5,2) as score
      from public.member_behavior_track t
)
update public.member_behavior_track bt
   set behavior_score = truth.score,
       fraud_risk_level = case when truth.score < 40 then 'high'
                               when truth.score < 70 then 'medium'
                               else 'low' end,
       updated_at = NOW()
  from truth
 where bt.member_id = truth.member_id
   and (bt.behavior_score is distinct from truth.score
        or bt.fraud_risk_level is distinct from
           case when truth.score < 40 then 'high'
                when truth.score < 70 then 'medium'
                else 'low' end);

-- P.3.e  After the repair: every P.0.e column reads 0 except members_without_behavior_row, which
--        this delta leaves alone on purpose.
select (select count(*) from public.group_poll_options o
         where o.vote_count is distinct from
               (select count(*) from public.group_poll_votes v where v.option_id = o.id)) as options_stale,
       (select count(*) from public.groups g
         where g.current_members is distinct from
               (select count(*) from public.members m
                 where m.group_id = g.id and m.status <> 'suspended'))                     as groups_stale,
       (select count(*) from public.members m
         where m.total_paid is distinct from
               (select coalesce(sum(c.amount), 0) from public.contributions c
                 where c.member_id = m.id and c.status = 'paid'
                   and c.type in ('contribution','joining_fee','registration_contribution',
                                  'loan_settlement_extraction')))                          as members_total_paid_stale,
       (select count(*) from public.member_behavior_track t
         where t.behavior_score is distinct from
               (greatest(0, 100.0 - 10.0 * (select count(*) from public.contributions c
                                             where c.member_id = t.member_id
                                               and c.status = 'overdue')))::numeric(5,2))  as behavior_score_stale;

-- P.3.f  The money again, and it must still equal P.0.f and P.2.e. None of the four aggregates above
--        is a balance: groups.balance stays ledger-derived, and the only path from this delta to a
--        ledger row is the [SEC_002] guard that did not move.
select (select count(*) from public.groups)                                        as groups_total,
       (select count(*) from public.group_ledger)                                  as ledger_rows,
       (select count(*) from public.platform_ledger)                               as platform_rows,
       (select coalesce(sum(balance), 0)::numeric(14,2) from public.groups)        as group_balance_total,
       (select coalesce(sum(amount), 0)::numeric(14,2) from public.platform_ledger) as platform_revenue_total,
       (select count(*) from public.groups g where g.balance <> coalesce(
          (select sum(l.amount) from public.group_ledger l where l.group_id = g.id), 0)) as balance_drifts;

-- ──────────────────────────────────────────────────────────────────────────────
-- P.4  BEHAVIOURAL PROOF. Run supabase/maintenance/TRIGGER_AGGREGATE_PROBE.sql as one batch on the
--      tier after P.1 - it opens its own transaction and ends in ROLLBACK, so it writes nothing
--      permanently, and it manufactures all three shapes this delta fixes:
--        A a vote re-pointed through the client's own upsert key  -> both options recounted, audit
--          branch reached;
--        B a member moved to another group                       -> origin and destination both
--          recounted;
--        C a contribution re-assigned to another member          -> origin's total_paid drops back;
--        D the guard vocabulary that moved into the helper       -> vocabulary_ok=8 of=8, and both
--          dropped RPCs at 0.
--      On a fixed tier every stale flag answers 0 and the post-rollback census equals P.0.f.
--      On an unfixed tier it answers 1 for each, which is the same probe proving the defect still
--      exists - run it before P.1 as well if you want the before picture in the record.
--
-- P.4 RESULT, recorded 2026-11-07. P.0 matched the pre-delta hashes on both tiers, so P.1 ran.
--      P.2.a matched every new hash in that section, the four event sets are the widened ones,
--      the helper is anon=false / authenticated=false / service_role=true, both dropped names
--      answer 0, and vocabulary_ok=8. P.3 corrected behavior_score on 2 rows per tier and wrote
--      no other aggregate (UPDATE 0 / UPDATE 0 / members_to_repair 0). Money was unchanged from
--      P.0.f and P.2.e:
--        Local  36 groups / 3044 ledger rows / 122 platform rows / 1485750.00 / 21100.00 / drift 0
--        Cloud  19 groups / 1397 ledger rows / 122 platform rows / 702500.00 / 21100.00 / drift 0
--      After P.3 every stale column in P.3.e reads 0, including behavior_score_stale.
--      Schema digest on both tiers: 82ee267f5fe7a5ad766f1dba2c3de5e7 over
--        1037 | C=535 E=1 F=87 G=15 I=33 K=198 P=86 R=38 S=2 T=38 V=4.
--      The object count did not fall to 1036. 20261104, applied first the same day, added one
--      policy, and this delta's net function change is minus one, so the two cancel.
--      The rollback probe in maintenance/TRIGGER_AGGREGATE_PROBE.sql was not run with this apply.

-- ──────────────────────────────────────────────────────────────────────────────
-- P.5  FOLLOW-UPS THIS DELTA LEAVES OPEN.
--   * The 10-points-per-overdue heuristic now exists in two places: inside
--     fn_trigger_update_behavior_metrics (02_FUNCTIONS_AND_TRIGGERS.sql:1206) and inside P.3.d here.
--     The right fix is a recompute_member_behavior_v1(member_id) primitive next to
--     fn_sync_single_member_financials, which both the trigger and any later repair call, so the
--     rule has one home the way the member aggregates already have one. It is not done here because
--     a new function is a new anon-executable object under the 03 blanket grant, and a delta that
--     adds primitives should add one with its own REVOKE, not as a side effect of a trigger fix.
--   * members_without_behavior_row (P.0.e) is reported, not repaired. tr_member_behavior_init inserts
--     on member INSERT only, so a member row that predates that trigger has nothing for the
--     behaviour trigger to score and reads as if the member had no history. The backfill is one
--     INSERT ... SELECT ... ON CONFLICT DO NOTHING over members, and it belongs with whatever change
--     decides what the other forty-odd columns of member_behavior_track are actually written by - on
--     a tier nothing outside these two triggers writes them, and the client only ever reads them
--     (SyncRepositoryImpl.kt:441 is a select), which is worth naming in its own delta.
--   * record_disbursement_v1 still has no MFA gate. The gold-standard rule is that a disbursement of
--     R10,000 or more answers [SEC_001] at the database, and approve_and_disburse_loan_v2 /
--     complete_payout_v2 carry that check; this wrapper does not. It was not added here because
--     nothing in the app can answer a challenge on this path: the only Kotlin caller of
--     recordDisbursement is the outbox replay (OutboxHandlers.kt:68 -> GroupRepositoryImpl.kt:81 ->
--     SupabaseGroupDataSourceImpl.kt:171), and the interactive screens disburse through
--     complete_payout_v2 and approve_and_disburse_loan_v2, which already gate. A [SEC_001] raised at
--     the database would therefore strand queued money rather than protect it. The fix starts on the
--     client: either a UI path that calls this RPC with an interactive session behind it, or the
--     decision that the outbox may not replay high-value disbursements unattended.
--   * record_liquidity_v1 has no Kotlin caller at all - 02_FUNCTIONS_AND_TRIGGERS.sql and
--     03_SECURITY_AND_RLS.sql:149-160 both describe it as the harness's and an admin's inflow route,
--     and it is kept because the guarded primitive it replaces was withdrawn from `authenticated` in
--     20261029. It was single-sourced here rather than deleted because, unlike the two RPCs in P.1.e,
--     it is named in the module that grants it and its body refuses the wrong caller; but the next
--     time someone touches that section they should decide whether it needs an app path or its own
--     retirement.
--   * The generated artifacts were re-run from the masters when this refactor landed
--     (migrations/00000000000000_GROUND_ZERO.sql, CONSOLIDATED_SCHEMA_FOR_DASHBOARD.sql,
--     online/000_CONSOLIDATED_ONLINE_SYNC.sql); SEED_ALL_DATA.sql came out unchanged because the
--     seed inlines no function bodies. The parity digest header in
--     maintenance/SCHEMA_PARITY_DIGEST.sql. Re-measured on both tiers after P.1 on 2026-11-07, the
--     digest is 82ee267f5fe7a5ad766f1dba2c3de5e7 over 1037 objects with F=87 and P=86 (T stays 38:
--     four event sets change, no trigger is added or removed). The predicted drop to 1036 objects
--     counted this delta alone; 20261104's new policy offsets the net loss of one function. That
--     measured row is the EXPECTED line. Do not edit it from the master alone.

-- ──────────────────────────────────────────────────────────────────────────────
-- R.1  ROLLBACK. Run each on its own. This reverses the event sets, which is the part a tier can be
--      put back to without the old source text; the bodies go back with the master as it stood
--      before this delta, and these are the hashes that prove you restored the right text:
--        fn_sync_group_member_count         644fa3272a8c4228f897fb0af573434d
--        fn_trigger_sync_member_financials  9638bd2649f48f3efa425999b2c455ff
--        fn_trigger_update_behavior_metrics 43cf492560b8144147ad00061a9756e7
--        fn_trigger_sync_poll_vote_count    61a74696c9fb714734cf8daa6d2cbf9e
--        fn_trigger_voting_audit            0ad1a5043119c542094500ae3d45fd93
--        record_disbursement_v1             729042ffce49ecfe608e19b7621f0462
--        record_liquidity_v1                f01eff7792049d78589f87453a46e38e
--      The dropped RPCs are not restored. Both were reachable from no caller, and
--      record_platform_revenue_v1 in particular is a second, disagreeing writer of
--      platform_ledger.balance_after - putting it back is a defect, not a rollback step. If a tier
--      genuinely needs one, it needs the master history, not this delta.
--      These statements are commented out so running this file top to bottom cannot undo itself.
--      Uncomment and run them only AFTER the bodies above have been restored from the master history
--      and re-fingerprinted: the new bodies under the old event sets are neither the fixed nor the
--      old tier, they are the old bug plus a VOTE_CHANGED audit branch that never fires.

-- DROP TRIGGER IF EXISTS tr_sync_poll_vote_count ON public.group_poll_votes;
-- CREATE TRIGGER tr_sync_poll_vote_count AFTER INSERT OR DELETE ON public.group_poll_votes FOR EACH ROW EXECUTE FUNCTION public.fn_trigger_sync_poll_vote_count();
--
-- DROP TRIGGER IF EXISTS tr_vote_audit ON public.group_poll_votes;
-- CREATE TRIGGER tr_vote_audit AFTER INSERT ON public.group_poll_votes FOR EACH ROW EXECUTE FUNCTION public.fn_trigger_voting_audit();
--
-- DROP TRIGGER IF EXISTS tr_update_behavior_metrics ON public.contributions;
-- CREATE TRIGGER tr_update_behavior_metrics AFTER INSERT OR UPDATE ON public.contributions FOR EACH ROW EXECUTE FUNCTION public.fn_trigger_update_behavior_metrics();
--
-- DROP TRIGGER IF EXISTS tr_update_behavior_metrics_loans ON public.loan_repayments;
-- CREATE TRIGGER tr_update_behavior_metrics_loans AFTER INSERT OR UPDATE ON public.loan_repayments FOR EACH ROW EXECUTE FUNCTION public.fn_trigger_update_behavior_metrics();

-- The helper may stay: an unused function whose EXECUTE is withdrawn from every role but service_role
-- is not a risk, and removing it while the wrappers still reference its name would break them.
-- DROP FUNCTION IF EXISTS public.assert_ledger_wrapper_input_v1(UUID, NUMERIC, TEXT, TEXT, TEXT, TEXT);

-- select pg_notify('pgrst', 'reload schema');
