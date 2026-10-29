# Ledger-first policy

`public.group_ledger` is the authoritative, immutable record of every balance movement. `public.groups.balance` is a cache of that ledger: `NUMERIC(12,2) NOT NULL DEFAULT 0` since `online/SYNC_DELTA_20261022.sql`.

## Where the implementation lives

- Canonical RPC source: `supabase/02_FUNCTIONS_AND_TRIGGERS.sql`.
  - `increment_group_balance` — row-locks the group, is idempotent on `transaction_id`, updates the cached balance, and inserts the ledger row in the same transaction. `p_member_id` is optional.
  - `record_contribution_v2` (and its `internal_post_contribution` helper), `record_loan_repayment_v1`, `settle_expired_loan_v2`, `pay_platform_fee_v2`, `record_platform_revenue_v2`, `approve_and_disburse_loan_v2`, `complete_payout_v2`, `process_complex_payment_v4`, `process_rosca_payout_v2/_v3`, `reconcile_bank_transaction_v1`. `settle_expired_loan_v2` replaced the retired `record_loan_settlement_extraction_v1`, which is revoked from every role.
  - Reconciliation and repair: `recompute_ledger_running_balances_v2`, `audit_and_fix_group_balance_v1`, `recalculate_all_group_financials_v3`.
- Generated copies (never hand-edit): `migrations/00000000000000_GROUND_ZERO.sql`, `CONSOLIDATED_SCHEMA_FOR_DASHBOARD.sql`, `online/000_CONSOLIDATED_ONLINE_SYNC.sql`.
- Existing tiers change only through a new dated `online/SYNC_DELTA_<date>.sql`. `20261014` through `20261028` are applied on Local (`20261027` and `20261028` are still outstanding on Cloud) and must not be rewritten. `20261029` is cut and applied on neither tier.

## Rules

1. Never `UPDATE public.groups SET balance = ...` from an application path or a script. Post through an RPC that also writes `group_ledger`.
2. Every outbound guard must fail closed: treat a missing balance as zero (`COALESCE(v_group.balance, 0)`) and `RAISE` when funds are insufficient. Validate the payload first and raise `[FIN_003]` for a non-positive amount, a third decimal place, or an unknown `status`/`type` literal — before the first write, so a retry cannot half-apply.
3. Keep `balance_after` equal to the cached balance returned by the update in the same transaction.
4. Seeds (`SEED_ALL_DATA.sql`) that insert `groups.balance` are local-development only; never seed an existing tier.
5. When adding an RPC that moves money, mirror the `increment_group_balance` pattern: lock the group, short-circuit on an already-seen `transaction_id` with a matching amount, update the cached balance `RETURNING balance`, insert the ledger row, return the new balance. Then regenerate all four artifacts and record new expected fingerprints in a fresh delta.

## Verification

- `recompute_ledger_running_balances_v2` rebuilds `balance_after` set-based and resets the cached balance to the ledger sum.
- `audit_and_fix_group_balance_v1` reports and optionally repairs a cached balance that diverges from the ledger.
- The `SYNC_DELTA_20261022` read-only block reports body hashes, `groups_null_balance`, and `balance_nullable`; all three should confirm the repair on every tier.
- `public.vw_ledger_integrity_check` (`05_PERFORMANCE_VIEWS.sql`, `SELECT` granted to `authenticated` only) compares each cached `groups.balance` with its ledger sum and labels the group `DRIFT` or `CONSISTENT`; a clean tier returns no `DRIFT` rows. Measured on Local 2026-10-28: 19 groups, all `CONSISTENT`.
- `maintenance/SCHEMA_PARITY_DIGEST.sql` gives the one-row object inventory plus digest, and `maintenance/SCHEMA_OBJECT_FINGERPRINTS.sql` lists per-function body hashes.
