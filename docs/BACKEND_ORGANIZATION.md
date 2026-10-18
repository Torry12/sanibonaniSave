# Backend organization

This guide describes the Supabase SQL sources and the generated deployment artifacts used by the project.

## SQL sources

The active sources live directly under `supabase/`:

1. `01_DATABASE_SCHEMA.sql` defines tables, extensions, constraints, and indexes.
2. `02_FUNCTIONS_AND_TRIGGERS.sql` defines database functions and triggers.
3. `18_DEPLOY_PROCESS_COMPLEX_PAYMENT_V4.sql` defines the complex payment RPC.
4. `03_SECURITY_AND_RLS.sql` defines row-level security and grants.
5. `04_PLATFORM_ADMIN_SETUP.sql` provisions platform administration.
6. `05_PERFORMANCE_VIEWS.sql` defines reporting views.
7. `30_STORAGE_RLS.sql` provisions storage buckets and policies.
8. `online/01_ONLINE_SPECIFIC_SETUP.sql` contains production-only overrides.

`00_REBUILD_ALL.sql` and `online/00_REBUILD_ONLINE.sql` show the execution order. Both start with `07_RESET_DATABASE.sql`, which is destructive. The local rebuild also runs development seeds; the online rebuild does not.

## Generated artifacts

Edit the source modules, then regenerate their copies:

- `migrations/00000000000000_GROUND_ZERO.sql` is the consolidated baseline migration.
- `CONSOLIDATED_SCHEMA_FOR_DASHBOARD.sql` is the one-paste dashboard schema, including online overrides.
- `online/000_CONSOLIDATED_ONLINE_SYNC.sql` is the one-paste online full-rebuild script. It has a guard against accidental data deletion.
- `SEED_ALL_DATA.sql` is the integrated local development seed.

From the repository root, regenerate in this order:

```powershell
.\supabase\scripts\generate_ground_zero.ps1
.\supabase\scripts\generate_consolidated_schema.ps1
.\supabase\scripts\generate_online_sync.ps1
.\supabase\scripts\generate_integrated_seed.ps1
```

For an existing online database, use a reviewed additive migration such as `online/SYNC_DELTA_20261014.sql`; do not run either full rebuild against an existing tier. Its section G.0 is a manual RPC fingerprint gate: inspect that report first, skip G.1 when all fingerprints match, and stop for review if any function body differs. Back up the database and inspect the SQL before applying it.

## Consistency rules

- Keep PostgreSQL structures aligned with the Room entities and migrations in `:data`.
- Keep ledger transaction identifiers as `TEXT` where external payment references may be non-UUID values.
- Preserve cent-accurate numeric types for financial amounts.
- Maintain server-side invariants through idempotent RPCs and triggers.
- Keep RLS policies and storage policies synchronized with the schema and application access patterns.
