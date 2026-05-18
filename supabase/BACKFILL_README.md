Backfill & Recompute: group balances

This document explains the included one-off migrations and the SQL-check script.

Files added:
- `supabase/migrations/20260518000100_backfill_group_ledger_from_payments.sql`
  - Attempts to insert missing `group_ledger` rows derived from common source tables (`contributions`, `payments`, `disbursements`, `transactions`) if those tables exist.
  - Uses `transaction_id` where available and avoids inserting duplicate ledger entries.
  - Treats `disbursements` as negative amounts.

- `supabase/migrations/20260518000200_recompute_group_balances_from_ledger.sql`
  - Aggregates `group_ledger` to compute the canonical balance per group and updates `public.groups.balance` accordingly.
  - Creates/uses `public.migration_balance_recompute_audit` to record old/new balances for traceability.
  - Performs per-group updates (many small updates) to reduce single-statement transaction pressure.

- `scripts/check_sql_balance_updates.ps1`
  - A PowerShell heuristic script that scans `.sql` files for `UPDATE ... groups SET ... balance` patterns and flags files that don't appear to also insert into `group_ledger`.
  - Intended to be used in developer workflows and can be run in CI (`-CI` flag) to fail on findings.

Recommended process (safe approach):
1) Create a DB backup/snapshot.
2) Run the checker locally to find any direct balance updates in SQL files:

```powershell
# From project root
.\scripts\check_sql_balance_updates.ps1
# or to fail CI when risky matches are found
.\scripts\check_sql_balance_updates.ps1 -CI
```

3) Run the backfill migration in a staging environment first (or run with a read-only run to preview counts):

Using `psql` against your Postgres instance or use Supabase CLI (local):

```powershell
# Example - adjust connection params
psql "postgresql://postgres:password@localhost:54322/postgres" -f supabase/migrations/20260518000100_backfill_group_ledger_from_payments.sql
```

Supabase local CLI note: if you use `supabase db diff` / `supabase db push` pipelines, place this file in your migrations folder and allow the CLI to apply it according to your workflow.

4) Inspect `public.group_ledger` and validate counts and samples.

5) Run the recompute migration to set groups balances from ledger:

```powershell
psql "postgresql://postgres:password@localhost:54322/postgres" -f supabase/migrations/20260518000200_recompute_group_balances_from_ledger.sql
```

6) Inspect `public.migration_balance_recompute_audit` for differences, investigate unexpected diffs, and roll back if necessary (audit table contains old_balance values).

7) Once validated, add the `scripts/check_sql_balance_updates.ps1` check to CI (GitHub Actions step or a pre-commit hook) so future PRs are flagged if new SQL files directly change group balances without adding ledger entries.

Notes & caveats:
- The backfill script uses heuristics for common table names and expected column names (group_id, amount, transaction_id, created_at). If your schema differs, the script will skip those tables and print a notice.
- Always test in a non-production environment first. Keep backups.
- The recompute migration updates groups balances to exactly match the ledger aggregates. If your existing `groups.balance` already reflected other off-ledger adjustments, those will be replaced.
- If you want to preserve off-ledger adjustments, you should first inspect and import those adjustments into `group_ledger` (with a sensible transaction_id and description) before running the recompute.

If you'd like, I can:
- wire the script into a GitHub Actions job that fails PRs when risky matches are found, or
- adapt the backfill script to your exact source table/column names if you point me to them, or
- run the scripts against a local Supabase instance (if you want me to attempt a run here) — tell me which option you prefer.

