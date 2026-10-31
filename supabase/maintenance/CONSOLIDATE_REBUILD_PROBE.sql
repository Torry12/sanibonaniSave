-- Consolidate-rebuild probe - ROLLBACK-ONLY. Proves consolidate_group_ledger_v1 books what
-- internal_post_contribution books, on exactly the four contribution shapes the pre-20261101C body
-- got wrong, without leaving any of them behind.
--
-- This is the behavioural half of SYNC_DELTA_20261101C: P.3's replay only proves the function is
-- idempotent on a tier whose ledger is already complete, so on Local and Cloud it returns all zeros
-- and says nothing about the fix. This probe manufactures the missing shapes instead.
--
--   status = 'partial'      R500 -> group_ledger,   category 'contribution'            (old body skipped it)
--   type IS NULL            R100 -> group_ledger,   category 'contribution'            (old body raised a
--                                                                                        NOT NULL violation on
--                                                                                        group_ledger.category and
--                                                                                        aborted the rebuild)
--   'registration_...'      R200 -> platform_ledger, category 'registration_contribution' (old body put it in the group)
--   'member_fee'             R50 -> platform_ledger, category 'platform_fee'           (old body booked it nowhere)
--
-- It writes inside one transaction and ends with ROLLBACK, so a run changes nothing: the last
-- statement re-measures the tier after the rollback and must show the numbers it showed before.
-- Run it as one statement batch (psql -f, or the Dashboard editor) - never statement by statement,
-- and never edit the ROLLBACK into a COMMIT. The seeded group name in PROBE_GROUP below must exist
-- on the tier; if it does not, every SELECT here returns zero rows and the probe proves nothing.
--
-- Expected on a fixed tier (Local measured 2026-10-31):
--   probe group        |LIGHT-Emergency Fund|balance_before=22000.00
--   run1               |"entries_reconstructed": 2, "platform_entries": 2, "final_balance": 22600.00,
--                      | "ledger_rows": 43          <- the R500 + R100 are the only group money
--   group rows         |contribution 100.00 + contribution 500.00, created_at = each paid_at
--   platform rows      |platform_fee 50.00 + registration_contribution 200.00
--   balance/drift      |balance_after=22600.00, drift_groups=0       <- the recompute ran, and the
--                      |                                             cached column still equals SUM
--   run2               |every count 0                                 <- idempotent
--   platform census    |sum = max(balance_after), out_of_order = 0    <- the running total stayed
--                      |                                             monotonic in created_at order
--   post-rollback      |the tier exactly as P.0 of the delta measured it
--
-- On a tier still running the old body, run1 raises
--   null value in column "category" of relation "group_ledger" violates not-null constraint
-- from the typeless row alone; delete that VALUES line to see the other three defects report
-- themselves as money in the wrong ledger.
-- ─────────────────────────────────────────────────────────────────────────────

BEGIN;

CREATE TEMP TABLE probe_grp ON COMMIT DROP AS
    SELECT g.id AS group_id, g.name AS group_name, g.balance AS balance_before,
           (SELECT m.id FROM public.members m WHERE m.group_id = g.id ORDER BY m.id LIMIT 1) AS member_id
      FROM public.groups g
     WHERE g.name = 'LIGHT-Emergency Fund';

SELECT 'probe group|' || group_name || '|member=' || member_id || '|balance_before=' || balance_before AS line
  FROM probe_grp;

INSERT INTO public.contributions (member_id, group_id, amount, type, due_date, paid_at, status, transaction_id)
    SELECT p.member_id, p.group_id, v.amount, v.type, DATE '2026-09-01', v.paid_at, v.status, v.tx
      FROM probe_grp p, (VALUES
            (500.00::numeric, 'contribution'::text,              timestamptz '2026-09-01 08:00:00+00', 'partial', 'PROBE-PARTIAL'),
            (100.00::numeric, NULL::text,                        timestamptz '2026-09-02 08:00:00+00', 'paid',    'PROBE-TYPELESS'),
            (200.00::numeric, 'registration_contribution'::text, timestamptz '2026-09-03 08:00:00+00', 'paid',    'PROBE-REG'),
            ( 50.00::numeric, 'member_fee'::text,                timestamptz '2026-09-04 08:00:00+00', 'paid',    'PROBE-FEE')
        ) AS v(amount, type, paid_at, status, tx);

-- The rebuild, and what it reported.
SELECT 'run1|' || public.consolidate_group_ledger_v1(group_id)::text AS line FROM probe_grp;

-- Where each of the four landed. Keyed by the contribution id, because that is the transaction_id
-- the reconstruction writes - the seed's own contributions.transaction_id is provenance, not a key.
SELECT 'GROUP   |' || l.category || '|amount=' || l.amount || '|created=' || l.created_at
       || '|desc=' || l.description || '|src=' || c.transaction_id AS line
  FROM public.group_ledger l JOIN public.contributions c ON c.id::text = l.transaction_id
 WHERE c.transaction_id LIKE 'PROBE-%'
 ORDER BY 1;

SELECT 'PLATFORM|' || pl.category || '|amount=' || pl.amount || '|balance_after=' || pl.balance_after
       || '|created=' || pl.created_at || '|src=' || c.transaction_id AS line
  FROM public.platform_ledger pl JOIN public.contributions c ON c.id::text = pl.transaction_id
 WHERE c.transaction_id LIKE 'PROBE-%'
 ORDER BY 1;

-- The cached column moved with the ledger, and no group anywhere is left disagreeing with its own.
SELECT 'balance_after=' || g.balance || '|expected 22600.00|drift_groups=' ||
       (SELECT count(*) FROM public.groups x
         WHERE x.balance <> COALESCE((SELECT sum(l.amount) FROM public.group_ledger l WHERE l.group_id = x.id), 0)) AS line
  FROM public.groups g JOIN probe_grp p ON p.group_id = g.id;

-- A platform category must never sit in a group ledger, after the rebuild or ever.
SELECT 'platform_types_in_group_ledger=' || count(*) AS line
  FROM public.group_ledger
 WHERE category IN ('registration_contribution', 'member_fee', 'member_fee_ledger');

-- Idempotency: the same call again must add nothing.
SELECT 'run2|' || public.consolidate_group_ledger_v1(group_id)::text AS line FROM probe_grp;

-- The platform running total is re-derived table-wide in created_at order, so the two backdated
-- rows above must not leave any row whose balance_after is lower than an earlier row's.
SELECT 'platform|rows=' || count(*) || '|sum=' || sum(amount)::numeric(14,2)
       || '|last_running=' || max(balance_after)::numeric(14,2)
       || '|out_of_order=' || count(*) FILTER (
              WHERE balance_after < (SELECT max(x.balance_after) FROM public.platform_ledger x
                                      WHERE x.created_at < pl.created_at)) AS line
  FROM public.platform_ledger pl;

ROLLBACK;

-- Post-rollback census: must equal the tier's P.0 numbers, unchanged.
SELECT 'post-rollback|ledger_rows=' || (SELECT count(*) FROM public.group_ledger)
       || '|platform_rows=' || (SELECT count(*) FROM public.platform_ledger)
       || '|group_total=' || (SELECT coalesce(sum(balance), 0)::numeric(14,2) FROM public.groups)
       || '|platform_total=' || (SELECT coalesce(sum(amount), 0)::numeric(14,2) FROM public.platform_ledger)
       || '|probe_rows_left=' || (SELECT count(*) FROM public.contributions WHERE transaction_id LIKE 'PROBE-%') AS line;
