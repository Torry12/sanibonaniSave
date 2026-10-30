-- SYNC_DELTA_20261101.sql
-- DATA REPAIR, no schema and no function change. Backfills one opening-balance ledger row for
-- every group that carries a cached balance but has no ledger rows at all, so that
-- groups.balance stops being an unsupported number and the ledger sum agrees with it.
--
-- Measured on Cloud 2026-10-30: 49 groups, 0 ledger rows in total, and 7 groups with a positive
-- cached balance totalling R385,500.00 - all of them DRIFT in public.vw_ledger_integrity_check
-- with ledger_sum = 0. The other 42 are CONSISTENT vacuously (0 = 0). Local has no such group
-- (19 groups, 1387 ledger rows, 19/19 CONSISTENT), so P.1 writes nothing there; that is the
-- correct outcome, not a failure.
--
-- Why a delta and not an UPDATE in the Dashboard: the repair has to be reviewable, idempotent
-- and re-measurable. The predicate is a query over live rows, the write returns what it wrote,
-- and a second run inserts nothing.
--
-- What this does NOT do: it does not touch groups.balance, it does not write a contribution row
-- (so totalContributions and the investment-club unit price balance.ratio(totalContributions) are
-- untouched), and it does not touch any function body, so no PostgREST schema reload is needed.
--
-- Hand-maintained. The deltas up to 20261031B are cut history - never re-run one that has already
-- run and never edit one to describe a later change.

-- ──────────────────────────────────────────────────────────────────────────────
-- P.0  PRE-FLIGHT - READ ONLY.  Run this first, on its own.

-- P.0.a  Exactly what P.1 would write. Expected on Cloud: the seven rows below, all with
--        ledger_rows = 0. Expected on Local: no rows. Stop and diff the tier if anything else
--        appears - the predicate is "no ledger rows at all AND a non-zero cached balance", so a
--        new row here is a group whose balance has no history for a reason worth reading.
select g.id,
       g.name,
       g.type,
       g.balance,
       g.created_at::date as created,
       (select count(*) from public.group_ledger l where l.group_id = g.id) as ledger_rows
from public.groups g
where not exists (select 1 from public.group_ledger l where l.group_id = g.id)
  and g.balance <> 0
order by g.balance desc, g.name;
-- expect Cloud: 7 rows - SEED-Blue Chip Investors 109000.00, SEED-Crisis Response Fund 54500.00,
--         SEED-Zion Funeral Society / SEED-Neighborhood Savings / SEED-Morning Star ROSCA /
--         SEED-Eternal Peace Burial 54000.00 each, SIM-FLOWS-466557 6000.00.
-- expect Local: 0 rows.

-- P.0.b  The totals P.2 must reproduce unchanged. groups = all groups, cached_total = the sum of
--        every cached balance. P.1 adds ledger rows only, so cached_total must be identical in
--        P.2 - a different number means something other than this delta moved a balance.
select (select count(*) from public.groups) as groups,
       (select coalesce(sum(balance), 0) from public.groups) as cached_total,
       (select count(*) from public.group_ledger) as ledger_rows,
       (select count(*) from public.vw_ledger_integrity_check where status = 'DRIFT') as drift_groups,
       (select coalesce(sum(discrepancy), 0) from public.vw_ledger_integrity_check) as total_discrepancy;
-- expect Cloud: 49 / 385500.00 / 0 / 7 / -385500.00 (the discrepancy sum is ledger_sum minus
--         table_balance, so it is negative while the cached balances are the larger side).
-- expect Local: 19 / 757500.00 / 1387 / 0 / 0.00.

-- P.0.c  Why the insert cannot move a balance: group_ledger must carry no user trigger, because
--        a trigger on this table could otherwise credit groups.balance as a side effect and the
--        backfill would double the very number it is trying to support. Expect 0 rows.
select c.relname, t.tgname, pg_get_triggerdef(t.oid) as definition
from pg_trigger t
join pg_class c on c.oid = t.tgrelid
join pg_namespace n on n.oid = c.relnamespace
where n.nspname = 'public'
  and c.relname = 'group_ledger'
  and not t.tgisinternal;
-- expect: 0 rows. A row here means the write below must be re-designed before it is run.

-- ──────────────────────────────────────────────────────────────────────────────
-- P.1  WRITE. One INSERT ... SELECT, guarded by the same predicate as P.0.a, so a re-run inserts
--      nothing. transaction_id is deterministic ('opening_balance_<group_id>'), which both makes
--      the row recognisable and collides with the existing UNIQUE (group_id, transaction_id) on a
--      second attempt. balance_after equals the amount because this is the group's only row, i.e.
--      it is also the running balance. created_at is the group's own creation date: the capital
--      was present from inception, and the row should not claim to have arrived later than the
--      group did.

INSERT INTO public.group_ledger
    (group_id, member_id, transaction_id, amount, balance_after, description, category, created_at)
SELECT g.id,
       NULL,
       'opening_balance_' || g.id::text,
       g.balance,
       g.balance,
       'Opening balance: seeded capital carried on the group with no prior ledger rows (backfilled by SYNC_DELTA_20261101)',
       'opening_balance',
       g.created_at
FROM public.groups g
WHERE NOT EXISTS (SELECT 1 FROM public.group_ledger l WHERE l.group_id = g.id)
  AND g.balance <> 0
RETURNING group_id, amount, balance_after, category, transaction_id;
-- expect Cloud: 7 rows returned, amount = balance_after on each, category opening_balance.
-- expect Local: no rows returned.

-- ──────────────────────────────────────────────────────────────────────────────
-- P.2  VERIFY - read-only. Run after P.1.

-- P.2.a  The same totals as P.0.b. ledger_rows may only have grown by the number of rows P.1
--        returned; cached_total must be byte-identical; drift_groups and total_discrepancy must
--        both be 0.
select (select count(*) from public.groups) as groups,
       (select coalesce(sum(balance), 0) from public.groups) as cached_total,
       (select count(*) from public.group_ledger) as ledger_rows,
       (select count(*) from public.vw_ledger_integrity_check where status = 'DRIFT') as drift_groups,
       (select coalesce(sum(discrepancy), 0) from public.vw_ledger_integrity_check) as total_discrepancy;
-- expect Cloud: 49 / 385500.00 / 7 / 0 / 0.00.
-- expect Local: 19 / 757500.00 / 1387 / 0 / 0.00 (unchanged - P.1 wrote nothing).

-- P.2.b  The repaired groups, read through the shipped view.
select group_name, table_balance, ledger_sum, discrepancy, status
from public.vw_ledger_integrity_check
where group_name like 'SEED-%' or group_name like 'SIM-FLOWS-%'
order by group_name;
-- expect Cloud: the seven named in P.0.a now report ledger_sum equal to table_balance,
--         discrepancy 0 and status CONSISTENT.

-- P.2.c  What was written, with the audit trail on the row itself.
select g.name, l.amount, l.balance_after, l.category, l.description, l.created_at::date
from public.group_ledger l
join public.groups g on g.id = l.group_id
where l.category = 'opening_balance'
order by l.amount desc;
-- expect Cloud: 7 rows. expect Local: 0 rows.

-- ──────────────────────────────────────────────────────────────────────────────
-- P.3  NOTES.
--   * The row is deliberately category 'opening_balance' and not 'contribution'. A contribution
--     row also moves totalContributions, which is the unit denominator in
--     CalculateInvestmentClubValuationUseCase - the backfilled capital would stop being capital
--     and start being a unit purchase. Until now the only admin-callable way to record a
--     non-contribution inflow is record_liquidity_v1 (SYNC_DELTA_20261031); this delta writes the
--     same shape of row directly because record_liquidity_v1 credits groups.balance, which is
--     already correct here.
--   * recompute_ledger_running_balances_v2 and consolidate_group_ledger_v1 both derive the balance
--     from these rows, so the group is now self-consistent under a rebuild: one row in, one row out.
--   * A group created later with an initial balance and no ledger rows will not be touched by this
--     delta (it is cut, not a standing rule). Seed the money through record_liquidity_v1 instead,
--     which writes the ledger row and the balance in one transaction.
