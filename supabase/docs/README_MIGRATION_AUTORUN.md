# Local migration auto-apply

## Prerequisites

- Docker Desktop running with the Linux engine
- Supabase CLI installed
- Repository root: `SanibonaniSave_Full`

## Apply migrations

```powershell
npx supabase start
npx supabase db reset          # baseline + migrations + SEED_ALL_DATA.sql
```

`supabase/config.toml` has `[db.seed]` enabled with `sql_paths = ["./SEED_ALL_DATA.sql"]`. To skip the seed:

```powershell
npx supabase db reset --no-seed
```

## Current migration set

- `migrations/00000000000000_GROUND_ZERO.sql` — generated baseline, and the only file in this directory

`43_platform_fee_payment_idempotency.sql`, `44_loan_repayment_idempotency.sql` and
`45_increment_group_balance_idempotency.sql` live in `supabase/legacy_migrations/` now. Each one
re-creates a money-path `SECURITY DEFINER` function in its pre-hardening form, without the
`[FIN_002]` insufficient-funds guard, the `[FIN_003]` amount/type validation or the `[SEC_002]`
caller-identity check that the masters declare. Because `db reset` applies `supabase/migrations/`
after the baseline, keeping them there made every local reset undo the hardening: measured
2026-10-31, local read `FIN_003:NO SEC_002:NO` on all three functions while Cloud read `yes`, and
the schema digests differed (`13c055f18eaedb64b21dafd70827cddf` vs `a18f379d05164eb2a5adec30c06174a3`).
Their idempotency logic is already folded into `02_FUNCTIONS_AND_TRIGGERS.sql`, so re-applying them
adds nothing. Do not move them back.

## Verify

```powershell
npx supabase migration list --local
npx supabase db query --local -f supabase/maintenance/SCHEMA_PARITY_DIGEST.sql   # see the header's EXPECTED line
```

Both tiers read `8405ef938e6325bf03b6002e54f5ecca` (`1037` objects, `F=88`). The interval on
2026-10-31 when Local carried `online/SYNC_DELTA_20261101C` and Cloud still read
`a18f379d05164eb2a5adec30c06174a3` is the case to remember: identical counts with a different
digest is one replaced function body — `consolidate_group_ledger_v1` — not a missing object, and
`SCHEMA_OBJECT_FINGERPRINTS.sql` names it.

## Notes

- `db reset` is destructive to the **local** database only.
- Migration order is timestamp-based under `supabase/migrations/`.
- The full-rebuild path for local development is `supabase/00_REBUILD_ALL.sql`; the CLI path above is equivalent for schema + seed.
- Never push a full rebuild to an existing cloud tier; use a dated `online/SYNC_DELTA_<date>.sql`.
