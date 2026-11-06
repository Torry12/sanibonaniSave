-- Aggregate-sync trigger probe - ROLLBACK-ONLY. Manufactures the three writes the aggregate-sync
-- triggers in 02_FUNCTIONS_AND_TRIGGERS.sql do not answer, and asserts the refusal vocabulary of
-- the two ledger wrappers survived moving their guards into a helper.
--
-- The defect is always the same shape: a trigger is attached to an event set narrower than the
-- writes the app actually performs, or it derives the aggregate from only the NEW side of the row.
--
--   A. vote re-point     VotingRepositoryImpl.castVote UPSERTs group_poll_votes on conflict
--                        (poll_id, member_id), so changing an option is an UPDATE - and
--                        tr_sync_poll_vote_count is AFTER INSERT OR DELETE. Neither option moves.
--   B. member reparent   tr_sync_group_member_count is AFTER INSERT OR UPDATE OR DELETE, but reads
--                        COALESCE(NEW.group_id, OLD.group_id), so only the destination group is
--                        recounted; the origin keeps a member it no longer has.
--   C. contribution
--      re-assign         tr_sync_member_financials_contrib reads COALESCE(NEW.member_id,
--                        OLD.member_id) - the origin member keeps a total_paid for money that is
--                        now counted against somebody else.
--
-- fn_trigger_sync_loan_repayment_total already gets this right, with an explicit IS DISTINCT FROM
-- pass for the old key; these three are being normalised to that pattern, not to a new one.
--
-- It writes inside one transaction and ends with ROLLBACK, so a run changes nothing. Run it as one
-- statement batch (psql -f, or the Dashboard editor) - never statement by statement, and never edit
-- the ROLLBACK into a COMMIT. It needs at least two seeded groups on the tier and takes the two
-- lowest-named LIGHT-/SEED- ones; with fewer than two it reports NO FIXTURE and proves nothing.
--
-- Expected on an UNFIXED tier:      A stale=1 (twice), B stale=1, C stale=1, vocabulary_ok=0 of=8
--                                    and helper_exists=0.
-- Expected on a FIXED tier:         every stale flag 0, vocabulary_ok=8 of=8, helper_exists=1,
--                                    dead_revenue_v1=0 and dead_settlement_v1=0.
-- ─────────────────────────────────────────────────────────────────────────────

BEGIN;

CREATE TEMP TABLE probe_grp ON COMMIT DROP AS
    SELECT id AS group_id, name AS group_name,
           ROW_NUMBER() OVER (ORDER BY name) AS rn
      FROM public.groups
     WHERE name LIKE 'LIGHT-%' OR name LIKE 'SEED-%';

CREATE TEMP TABLE fixture ON COMMIT DROP AS
SELECT g1.group_id, g2.group_id AS other_group_id,
       (SELECT m.id FROM public.members m WHERE m.group_id = g1.group_id ORDER BY m.id LIMIT 1) AS member_id
  FROM probe_grp g1 JOIN probe_grp g2 ON g2.rn = 2
 WHERE g1.rn = 1;

SELECT 'fixture|groups=' || count(*) || '|' ||
       CASE WHEN count(*) < 2 OR max(member_id) IS NULL THEN 'NO FIXTURE' ELSE 'ok' END AS line
  FROM fixture;

-- ── A. a re-pointed vote ───────────────────────────────────────────────────────────────────────
CREATE TEMP TABLE probe_poll ON COMMIT DROP AS
WITH p AS (INSERT INTO public.group_polls (group_id, created_by_member_id, title, status, allow_multiple_choice)
                SELECT f.group_id, f.member_id, 'PROBE-Rotate', 'open', false FROM fixture f
                RETURNING id),
     o AS (INSERT INTO public.group_poll_options (poll_id, label, position)
                SELECT p.id, 'PROBE-A', 1 FROM p
                UNION ALL
                SELECT p.id, 'PROBE-B', 2 FROM p
                RETURNING poll_id, label, id)
SELECT p.id AS poll_id,
       (SELECT id FROM o WHERE label = 'PROBE-A') AS opt_a,
       (SELECT id FROM o WHERE label = 'PROBE-B') AS opt_b,
       f.member_id, f.group_id
  FROM p, f;

-- First vote: an INSERT, which the current trigger does answer.
INSERT INTO public.group_poll_votes (poll_id, option_id, member_id, group_id)
     SELECT poll_id, opt_a, member_id, group_id FROM probe_poll;

-- The same member, the same (poll_id, member_id) key, a different option: an UPDATE, which it does not.
UPDATE public.group_poll_votes v SET option_id = pp.opt_b, updated_at = NOW()
  FROM probe_poll pp WHERE v.poll_id = pp.poll_id AND v.member_id = pp.member_id;

SELECT 'A|opt_a_count=' || o.vote_count || '|expect 0|stale=' || (o.vote_count <> 0)::int
  FROM public.group_poll_options o JOIN probe_poll pp ON pp.opt_a = o.id;

SELECT 'A|opt_b_count=' || o.vote_count || '|expect 1|stale=' || (o.vote_count <> 1)::int
  FROM public.group_poll_options o JOIN probe_poll pp ON pp.opt_b = o.id;

-- What a derived recount says the truth is, independent of the trigger.
SELECT 'A|true_votes_on_a=' || count(*) || '|expect 0' AS line
  FROM public.group_poll_votes v JOIN probe_poll pp ON pp.opt_a = v.option_id;

SELECT 'A|true_votes_on_b=' || count(*) || '|expect 1' AS line
  FROM public.group_poll_votes v JOIN probe_poll pp ON pp.opt_b = v.option_id;

-- ── B. a member moved to another group ────────────────────────────────────────────────────────
SELECT 'B|before|origin=' || g.current_members || '|other=' || o.current_members
  FROM public.groups g, public.groups o, fixture f
 WHERE g.id = f.group_id AND o.id = f.other_group_id;

INSERT INTO public.members (group_id, full_name, status, member_key)
     SELECT f.group_id, 'PROBE-Reparented', 'active', 'PROBE-REPARENT' FROM fixture f;

UPDATE public.members m SET group_id = f.other_group_id
  FROM fixture f WHERE m.member_key = 'PROBE-REPARENT';

-- The origin group lost the row, so its count must be back where it started.
SELECT 'B|origin_stale=' || (g.current_members <> sub.n)::int
           || '|reported=' || g.current_members || '|truth=' || sub.n
  FROM public.groups g, fixture f
 CROSS JOIN LATERAL (SELECT count(*) AS n FROM public.members m WHERE m.group_id = g.id AND m.status <> 'suspended') sub
 WHERE g.id = f.group_id;

SELECT 'B|other_stale=' || (g.current_members <> sub.n)::int
           || '|reported=' || g.current_members || '|truth=' || sub.n
  FROM public.groups g, fixture f
 CROSS JOIN LATERAL (SELECT count(*) AS n FROM public.members m WHERE m.group_id = g.id AND m.status <> 'suspended') sub
 WHERE g.id = f.other_group_id;

-- ── C. a contribution re-assigned to another member ───────────────────────────────────────────
CREATE TEMP TABLE probe_pair ON COMMIT DROP AS
    SELECT f.member_id AS from_id,
           (SELECT m.id FROM public.members m
             WHERE m.group_id = f.group_id AND m.id <> f.member_id ORDER BY m.id LIMIT 1) AS to_id
      FROM fixture f;

INSERT INTO public.contributions (member_id, group_id, amount, type, due_date, paid_at, status, transaction_id)
     SELECT from_id, (SELECT group_id FROM fixture), 250.00, 'contribution',
            DATE '2026-09-01', NOW(), 'paid', 'PROBE-REASSIGN'
       FROM probe_pair WHERE to_id IS NOT NULL;

UPDATE public.contributions c SET member_id = pp.to_id
  FROM probe_pair pp WHERE c.transaction_id = 'PROBE-REASSIGN';

-- The origin member must be back to whatever they were owed before the probe's R250 - which the
-- ledger, not the cached column, decides.
SELECT 'C|from_total_paid=' || m.total_paid || '|from_ledger='
       || COALESCE((SELECT sum(c.amount) FROM public.contributions c WHERE c.member_id = pp.from_id
                      AND c.status = 'paid'
                      AND c.type IN ('contribution','joining_fee','registration_contribution','loan_settlement_extraction')), 0)
       || '|stale=' || (m.total_paid <> COALESCE((SELECT sum(c.amount) FROM public.contributions c WHERE c.member_id = pp.from_id
                      AND c.status = 'paid'
                      AND c.type IN ('contribution','joining_fee','registration_contribution','loan_settlement_extraction')), 0))::int
  FROM probe_pair pp JOIN public.members m ON m.id = pp.from_id
 WHERE pp.to_id IS NOT NULL;

-- ── D. the moved guards still refuse everything they used to ──────────────────────────────────
-- Structural, not behavioural: both wrappers run their [SEC_002] check before anything else, so a
-- maintenance session with no JWT cannot reach the amount guard to be refused. What is provable
-- here is that the vocabulary did not shrink when the shared guards moved to
-- assert_ledger_wrapper_input_v1 - each half of each refusal is still present, in the function that
-- now owns it.
WITH need(fn, token) AS (VALUES
    ('assert_ledger_wrapper_input_v1', '[SEC_002] %'),
    ('assert_ledger_wrapper_input_v1', 'INVALID_AMOUNT: % must be positive and have at most two decimal places'),
    ('assert_ledger_wrapper_input_v1', 'INVALID_DESCRIPTION: A ledger description is required'),
    ('assert_ledger_wrapper_input_v1', 'INVALID_CATEGORY: A ledger category is required'),
    ('record_disbursement_v1',         '''DISBURSEMENT_ACCESS_DENIED'''),
    ('record_disbursement_v1',         '''Disbursement'''),
    ('record_liquidity_v1',            '''LIQUIDITY_ACCESS_DENIED'''),
    ('record_liquidity_v1',            '''Liquidity''')
)
SELECT 'D|helper_exists=' || (SELECT count(*) FROM pg_proc WHERE proname = 'assert_ledger_wrapper_input_v1')
       || '|vocabulary_ok=' || count(*) FILTER (
              EXISTS (SELECT 1 FROM pg_proc x
                       WHERE x.proname = need.fn AND position(need.token IN x.prosrc) > 0))
       || '|of=' || count(*)
       AS line
  FROM need;

-- Both wrappers must still be declared, and the two superseded RPCs must be gone.
SELECT 'D|primitives|disburse=' || count(*) FILTER (WHERE proname = 'record_disbursement_v1')
       || '|liquidity=' || count(*) FILTER (WHERE proname = 'record_liquidity_v1')
       || '|dead_revenue_v1=' || count(*) FILTER (WHERE proname = 'record_platform_revenue_v1')
       || '|dead_settlement_v1=' || count(*) FILTER (WHERE proname = 'record_loan_settlement_extraction_v1')
       AS line
  FROM pg_proc WHERE proname IN ('record_disbursement_v1','record_liquidity_v1',
                                 'record_platform_revenue_v1','record_loan_settlement_extraction_v1');

ROLLBACK;

-- Post-rollback census: must equal the tier's P.0 numbers, unchanged.
SELECT 'post-rollback|groups=' || (SELECT count(*) FROM public.groups)
       || '|members=' || (SELECT count(*) FROM public.members)
       || '|contributions=' || (SELECT count(*) FROM public.contributions)
       || '|polls=' || (SELECT count(*) FROM public.group_polls)
       || '|votes=' || (SELECT count(*) FROM public.group_poll_votes)
       || '|probe_rows_left=' || (SELECT count(*) FROM public.group_polls WHERE title = 'PROBE-Rotate') AS line;
