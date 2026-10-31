# Balance backfill, recompute & audit

`public.groups.balance` is a cache of `public.group_ledger`. Since `online/SYNC_DELTA_20261022.sql` it is `NUMERIC(12,2) NOT NULL DEFAULT 0`, so a "missing" balance can no longer exist — only a divergent one.

This document covers how to detect and repair divergence. It does **not** describe a runnable one-off migration: the earlier `20260518…_backfill_group_ledger_from_payments.sql` / `…_recompute_group_balances_from_ledger.sql` files are not part of this repository. Use the in-database RPCs below instead.

---

## 1. Diagnose (read-only)

```sql
-- Groups whose cache differs from the ledger
SELECT g.id, g.name, g.balance,
       COALESCE(SUM(l.amount), 0) AS ledger_sum,
       g.balance - COALESCE(SUM(l.amount), 0) AS delta
FROM public.groups g
LEFT JOIN public.group_ledger l ON l.group_id = g.id
GROUP BY g.id, g.name, g.balance
HAVING g.balance <> COALESCE(SUM(l.amount), 0)
ORDER BY ABS(g.balance - COALESCE(SUM(l.amount), 0)) DESC;

-- Ledger running-balance drift (balance_after vs cumulative sum)
SELECT id, group_id, amount, balance_after,
       SUM(amount) OVER (PARTITION BY group_id ORDER BY created_at, id) AS running
FROM public.group_ledger
ORDER BY group_id, created_at, id;
```

`public.audit_and_fix_group_balance_v1(p_group_id, false)` performs the same per-group audit and reports without writing.

---

## 2. Repair (writes — operator runs)

| RPC | Effect |
| :--- | :--- |
| `public.audit_and_fix_group_balance_v1(p_group_id, true)` | Recomputes the cached balance from `group_ledger` for one group and repairs it. |
| `public.recompute_ledger_running_balances_v2(p_group_id)` | Set-based rebuild of `group_ledger.balance_after`; also resets the cached `groups.balance` to the ledger sum. |
| `public.recalculate_all_group_financials_v3(p_group_id)` | Recomputes derived financial totals for a group. |
| `public.perform_group_reconciliation_v1(p_group_id)` / `public.consolidate_group_ledger_v1(p_group_id)` | Reconciliation report and ledger consolidation. The consolidation replays the five source tables — `paid` **and `partial`** contributions — sends the three platform contribution types to `platform_ledger` instead of the group, and finishes by recomputing every `balance_after` and `groups.balance`, so it moves the cached column the way the caveat below describes. |
| `public.create_ledger_checkpoint_v1(p_group_id)` | Snapshot checkpoint before a repair. |

Recommended order: take a backup → run the read-only audit → checkpoint → repair one group → re-run the audit → repeat.

> **Caveat**: a recompute makes the cache exactly match the ledger. Any off-ledger adjustment that was previously reflected only in `groups.balance` is lost. Import those adjustments into `group_ledger` (with a real `transaction_id` and description) before recomputing.

---

## 3. NULL-balance repair (already applied)

`SYNC_DELTA_20261022.sql` section L.1 did the one-time repair:

1. `UPDATE public.groups g SET balance = COALESCE((SELECT SUM(amount) FROM public.group_ledger …), 0) WHERE balance IS NULL`
2. `ALTER TABLE public.groups ALTER COLUMN balance SET NOT NULL`
3. Replaced both spend guards (`approve_and_disburse_loan_v2`, `complete_payout_v2`) with `COALESCE(v_group.balance, 0)` checks that raise on insufficiency.

Do not re-run an applied delta. Verify instead with the delta's read-only L.4 block (body hashes, `groups_null_balance = 0`, `balance_nullable = NO`).

---

## 4. Static guard for future SQL

`scripts/check_sql_balance_updates.ps1` flags files that contain an `UPDATE … groups … SET … balance` without any `group_ledger` insert.

```powershell
# report findings
.\scripts\check_sql_balance_updates.ps1

# fail CI when risky matches exist
.\scripts\check_sql_balance_updates.ps1 -CI
```

The check is a conservative heuristic: it surfaces candidates for manual review, it does not prove a write is unsafe. New balance-changing logic belongs in an RPC, followed by artifact regeneration and a dated delta.
