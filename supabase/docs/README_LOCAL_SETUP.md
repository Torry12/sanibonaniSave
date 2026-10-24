# Local Supabase setup

The repository is configured for Supabase local development through Docker.

## Files

- `supabase/config.toml` — project configuration. `[db.seed]` is enabled and `sql_paths` points at `./SEED_ALL_DATA.sql`.
- `supabase/migrations/00000000000000_GROUND_ZERO.sql` — generated baseline.
- `supabase/migrations/43_platform_fee_payment_idempotency.sql`, `44_loan_repayment_idempotency.sql`, `45_increment_group_balance_idempotency.sql` — idempotency migrations applied after the baseline.
- `supabase/SEED_ALL_DATA.sql` — generated integrated development seed (local only).
- `supabase/00_REBUILD_ALL.sql` — canonical rebuild order for the numbered source modules.

## Apply locally

```powershell
npx supabase start
npx supabase db reset          # destructive to the LOCAL database only
```

`db reset` applies the baseline, the migrations, and `SEED_ALL_DATA.sql`.

To re-run the seed without a full reset:

```powershell
npx supabase db query --local --file "supabase/SEED_ALL_DATA.sql"
```

## Notes

- Never run `db reset`, `00_REBUILD_ALL.sql`, or the online full rebuild against an existing cloud tier. Cloud changes go through `supabase/online/SYNC_DELTA_<date>.sql`.
- If the CLI reports `dockerDesktopLinuxEngine` is missing, restart Docker Desktop and wait for the engine.
- After changing any seed module, regenerate `SEED_ALL_DATA.sql` with `supabase/scripts/generate_integrated_seed.ps1`.
