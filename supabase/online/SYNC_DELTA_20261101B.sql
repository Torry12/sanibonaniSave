-- SYNC_DELTA_20261101B.sql
-- DATA REPAIR, no schema and no function change. Rebuilds public.group_ledger from the source
-- tables for every group, then re-derives groups.balance from it. This SUPERSEDES
-- SYNC_DELTA_20261101: that delta could only support a group that already carried a cached
-- balance, so the 36 Cloud groups whose seed wrote contributions directly and never reached a
-- ledger row stayed R0.00 on screen while holding R18,950 - R113,100 of paid principal.
--
-- Measured on Cloud 2026-10-31 (probe: supabase/maintenance/BALANCE_VS_LEDGER_PROBE.sql):
--   49 groups, 7 ledger rows - all of them the 'opening_balance' rows 20261101 had backfilled -
--   4,553 contributions of which 4,400 paid, 42 groups with NO ledger row, 36 of those with paid
--   contributions, and balance_drifts = 0. The column never disagreed with its own empty ledger;
--   the money simply had no ledger. 0 source rows were unjoinable to members, so the rebuild
--   skips nothing.
--
-- Applied on Cloud 2026-10-31 through the Dashboard-equivalent Management API route, after a
-- restore dump (data 3.25 MB: 49 groups, 4,553 contributions, the 7 pre-existing ledger rows).
-- NOT applied on Local; P.4 states what it would do there and why that is still open.
--
-- Re-runnable, unlike 20261101: P.1.b uses ON CONFLICT (group_id, transaction_id) DO NOTHING on
-- every block, P.1.a and P.1.c are deletes over predicates that only ever match rows this delta
-- writes, and P.1.d recomputes from whatever the ledger holds.

-- ──────────────────────────────────────────────────────────────────────────────
-- P.0  PRE-FLIGHT - READ ONLY. Run each on its own, on the tier you are about to write.

-- P.0.a  The ledger must carry no user trigger: a trigger here could credit groups.balance as a
--        side effect of P.1.b and double the number this delta is trying to derive. Expect 0 rows.
select c.relname, t.tgname, pg_get_triggerdef(t.oid) as definition
from pg_trigger t
join pg_class c on c.oid = t.tgrelid
join pg_namespace n on n.oid = c.relnamespace
where n.nspname = 'public'
  and c.relname = 'group_ledger'
  and not t.tgisinternal;

-- P.0.b  Permission and provenance. Both functions are revoked from PUBLIC/anon/authenticated and
--        granted to service_role; a tier where this reads false cannot run P.1 without the
--        service role. Expect Cloud: postgres / true / true.
select current_user as role,
       has_function_privilege(current_user, 'public.consolidate_group_ledger_v1(uuid)', 'EXECUTE') as can_consolidate,
       has_function_privilege(current_user, 'public.recompute_ledger_running_balances_v2(uuid)', 'EXECUTE') as can_recompute;

-- P.0.c  What the rebuild will produce, before producing it - the same five blocks
--        consolidate_group_ledger_v1 replays, aggregated. cached_total is what the screens show
--        today; predicted_total is what they will show after P.1.d. Expect Cloud: cached_total
--        385500.00 (seven groups) against predicted_total 1677300.00 across all 49.
with src as (
  select g.id, g.balance as cached,
         coalesce((select sum(c.amount) from public.contributions c
                    where c.group_id = g.id and c.status = 'paid'
                      and c.type <> 'registration_contribution'), 0) as inflow_contrib,
         coalesce((select sum(r.amount) from public.loan_repayments r
                    where r.group_id = g.id), 0)                     as inflow_repay,
         coalesce((select sum(-p.amount) from public.payouts p
                    where p.group_id = g.id and p.status = 'completed'), 0)   as outflow_payout,
         coalesce((select sum(-l.amount) from public.loans l
                    where l.group_id = g.id
                      and l.status not in ('pending','rejected','cancelled')), 0) as outflow_loan,
         coalesce((select sum(-b.claim_amount) from public.beneficiary_payout_claims b
                    where b.group_id = g.id and b.status = 'paid'), 0)        as outflow_claim
  from public.groups g)
select count(*) as groups,
       sum(cached)::numeric(14,2)            as cached_total,
       sum(inflow_contrib + inflow_repay + outflow_payout + outflow_loan + outflow_claim)::numeric(14,2) as predicted_total,
       count(*) filter (where inflow_contrib + inflow_repay + outflow_payout + outflow_loan + outflow_claim < 0) as groups_predicted_negative
from src;

-- P.0.d  The synthetic rows P.1.a removes. These are 20261101's backfill, recognisable by the
--        'opening_balance_' transaction_id prefix. They MUST go before the rebuild: leaving them
--        in place adds the seeded capital on top of the rows that describe the same capital, which
--        would double every supported group. Expect Cloud before the run: 7 rows, R385,500.00.
select count(*) as rows_to_remove, coalesce(sum(amount), 0)::numeric(14,2) as money_to_remove
from public.group_ledger
where category = 'opening_balance'
  and transaction_id like 'opening\_balance\_%';

-- ──────────────────────────────────────────────────────────────────────────────
-- P.1  WRITE. Four statements, in this order - one tier-state change per statement, so a failure
--      leaves a readable half-state rather than an interleaved one.

-- P.1.a  Withdraw 20261101's backfill (predicate as P.0.d).
DELETE FROM public.group_ledger
WHERE category = 'opening_balance'
  AND transaction_id LIKE 'opening\_balance\_%'
RETURNING group_id, amount, transaction_id;

-- P.1.b  Rebuild from source, one group at a time. balance_after is written as 0 here on purpose;
--        P.1.d replaces every value with the running prefix sum.
SELECT g.name, public.consolidate_group_ledger_v1(g.id) ->> 'entries_reconstructed' AS reported
FROM public.groups g
ORDER BY g.name;

-- P.1.c  Un-book what the reconstruction mis-files. consolidate_group_ledger_v1 replays a
--        contribution's raw type as the ledger category, but internal_post_contribution (02_FUNCTIONS
--        _AND_TRIGGERS.sql:238) excludes 'registration_contribution' from the group ledger and sends
--        it to platform_ledger instead, so replaying it inflates group money by the platform's
--        registration fee. Predicate is narrow: only rows whose transaction_id is a
--        registration_contribution id, which only P.1.b can have produced.
DELETE FROM public.group_ledger l
WHERE l.category = 'registration_contribution'
  AND l.transaction_id IN (SELECT c.id::text FROM public.contributions c WHERE c.type = 'registration_contribution')
RETURNING l.group_id, l.amount, l.transaction_id;

-- P.1.d  Re-derive balance_after and groups.balance from the ledger, per group. This is the step
--        that actually moves groups.balance; running it alone on a tier that has not been rebuilt
--        would only re-affirm whatever the sparse ledger says.
SELECT count(*) AS groups_recomputed
FROM public.groups g
WHERE public.recompute_ledger_running_balances_v2(g.id) IS NOT NULL;

-- ──────────────────────────────────────────────────────────────────────────────
-- P.2  VERIFY - read-only.

-- P.2.a  Expect Cloud after the run: 49 / 4266 / 0 / 0 / 7 / 1677300.00.
select (select count(*) from public.groups) as groups_total,
       (select count(*) from public.group_ledger) as ledger_rows,
       (select count(*) from public.groups g where g.balance <>
          coalesce((select sum(l.amount) from public.group_ledger l where l.group_id = g.id), 0)) as balance_drifts,
       (select count(*) from public.groups g where not exists
          (select 1 from public.group_ledger l where l.group_id = g.id)) as groups_without_ledger,
       (select count(*) from public.groups where balance < 0) as negative_balances,
       (select sum(balance)::numeric(14,2) from public.groups) as platform_total;

-- P.2.b  The shipped view must agree with the hand check: expect DRIFT 0.
select count(*) as drift_groups from public.vw_ledger_integrity_check where status = 'DRIFT';

-- P.2.c  Category census. Expect Cloud: contribution 3900 / R2,157,000, joining_fee 240 / R36,000,
--        loan_repayment 13 / R20,300, loan_disbursement 12 / -R33,000, claim 17 / -R134,000,
--        payout 84 / -R369,000, and NO registration_contribution or opening_balance rows.
select category, count(*) as rows, sum(amount)::numeric(14,2) as money
from public.group_ledger group by category order by 3 desc;

-- ──────────────────────────────────────────────────────────────────────────────
-- P.3  RESULT ON CLOUD, measured after the run.
--   4,266 ledger rows over 49 groups, balance_drifts 0, groups_without_ledger 0,
--   vw_ledger_integrity_check DRIFT 0, platform total R1,677,300.00 (was R385,500.00 on seven
--   groups only). The two groups read off the device: Sim-ROSCA-4478 R30,500.00 over 119 rows and
--   Sim-Investment-7252 R108,100.00 over 116 rows, both R0.00 before.
--   Seven groups now carry a NEGATIVE cached balance: six SIM-FLOWS-* at -R26,200.00 and
--   LIGHT-Burial Society at -R1,050.00. That is not a reconstruction error - their seed wrote
--   payouts, loans and claims with no matching paid contributions, so the honest ledger is in the
--   red. Zeroing the column would only hide it; the fix belongs in the seed.

-- P.4  LOCAL: NOT RUN, measured 2026-10-31.
--   Local reads 19 groups / 1,391 ledger rows / balance_drifts 0 / opening_balance rows 0, so P.1.a
--   removes nothing there. Its registration fees are already on the correct side: all 122
--   registration contributions are in platform_ledger and none in group_ledger, which is exactly
--   what internal_post_contribution intends and what P.1.c restores on Cloud.
--   Local's own gap is narrower: 6 disbursed loans worth R17,000 have no ledger row (LIGHT-Emergency
--   Fund 2 x R3,500, and one each in SEED-Crisis Response Fund, SEED-Friends of Soweto,
--   SEED-Ubuntu Hybrid Co-op, SEED-Unity Savings Stokvel). Running P.1 on Local would add those 6
--   negative rows and move those five groups down by R17,000 in total. Left open, pending a fresh
--   Local dump: the 20261030 pre-rebuild dump predates the rebuild and is not a restore point for
--   the current Local state.

-- P.5  CLOUD: STILL MISSING AFTER THIS DELTA, and out of its scope.
--   Those same 122 registration contributions (R21,100) are booked in NEITHER Cloud ledger - not
--   group_ledger (P.1.c removes them by design) and not platform_ledger (measured: 0 rows joined on
--   transaction_id). The seed wrote the contribution rows directly, so the platform never received
--   the revenue record the live path would have made through record_platform_revenue_v2. Booking it
--   is a separate write against platform_ledger.

-- P.6  DEFECTS THIS DELTA EXPOSES BUT DOES NOT FIX.
--   * consolidate_group_ledger_v1 block 1 uses c.type as the ledger category, so it re-files
--     registration_contribution into the group ledger on every future rebuild. P.1.c is the
--     workaround; the durable fix is the same CASE the live path uses, which is a function-body
--     change and needs its own delta plus a pg_notify('pgrst','reload schema').
--   * The same function has no platform_ledger side at all, so a rebuilt group ledger silently
--     drops R21,100 of Cloud registration revenue that the live path would have booked.
--   * entries_reconstructed counts rows created in the last five minutes, while the rebuild stamps
--     historical paid_at values, so the function always reports 0 for seeded data. P.1.b's reported
--     column is therefore meaningless; read P.2.a's ledger_rows instead.
