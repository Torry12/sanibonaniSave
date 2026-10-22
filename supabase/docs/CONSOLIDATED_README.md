# Consolidated SQL apply

The old `CONSOLIDATED_APPLY.sql` helper and its include list no longer exist. Use the current entry points:

| Goal | File |
| :--- | :--- |
| Local full rebuild + development seed | `supabase/00_REBUILD_ALL.sql` (psql `\i` includes; destructive) |
| Supabase CLI local rebuild | `supabase db reset` — applies `migrations/00000000000000_GROUND_ZERO.sql`, the idempotency migrations, and `SEED_ALL_DATA.sql` |
| One-paste schema for the Dashboard | `supabase/CONSOLIDATED_SCHEMA_FOR_DASHBOARD.sql` |
| Existing-tier change | `supabase/online/SYNC_DELTA_<date>.sql` (new dated file; never a full rebuild) |

## Safety

- Back up the target database before any rebuild.
- Never apply a full rebuild to an existing tier.
- Run `scripts/check_sql_balance_updates.ps1` to flag direct `groups.balance` writes without a `group_ledger` insert.
- Regenerate artifacts with the four `supabase/scripts/generate_*.ps1` scripts after changing a source module.

See `docs/SEED_DATA_INSTRUCTIONS.md` and `supabase/docs/DATABASE_ORGANIZATION.md` for the current workflow.
