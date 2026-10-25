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

- `migrations/00000000000000_GROUND_ZERO.sql` — generated baseline
- `migrations/43_platform_fee_payment_idempotency.sql`
- `migrations/44_loan_repayment_idempotency.sql`
- `migrations/45_increment_group_balance_idempotency.sql`

## Verify

```powershell
npx supabase migration list --local
```

## Notes

- `db reset` is destructive to the **local** database only.
- Migration order is timestamp-based under `supabase/migrations/`.
- The full-rebuild path for local development is `supabase/00_REBUILD_ALL.sql`; the CLI path above is equivalent for schema + seed.
- Never push a full rebuild to an existing cloud tier; use a dated `online/SYNC_DELTA_<date>.sql`.
