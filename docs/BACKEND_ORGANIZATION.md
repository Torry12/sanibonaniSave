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

For an existing database, use the dated additive deltas in `online/` — `SYNC_DELTA_20261014`, `_20261021`, `_20261022`, `_20261026`, `_20261026B`, `_20261027`, `_20261028` and `_20261029`. All seven through `20261028` are applied on the Local tier; **Cloud still owes `20261027` and `20261028`**, which are pasted into the Dashboard SQL editor by hand, and `20261029` has been cut but applied on neither tier. Do not run either full rebuild against an existing tier, and never rewrite or re-run an applied delta. Each delta carries a read-only pre-flight and a fingerprint gate: compare the reported hashes with the ones recorded in the file, and stop for review if any body differs. Back up the database and inspect the SQL before applying it.

Two read-only probes live in `supabase/maintenance/`: `SCHEMA_PARITY_DIGEST.sql` returns one row of per-kind object counts plus a digest hash (its header records the expected baseline — bump it when the masters move), and `SCHEMA_OBJECT_FINGERPRINTS.sql` lists the exact function bodies that differ. After a function body changes on a live tier, reload PostgREST with `SELECT pg_notify('pgrst','reload schema');`. Never probe a `SECURITY DEFINER` RPC as `anon` on the Local stack — it crashes Local Postgres; check privileges with `has_function_privilege` instead.

## Consistency rules

- Keep PostgreSQL structures aligned with the Room entities and migrations in `:data`.
- Keep ledger transaction identifiers as `TEXT` where external payment references may be non-UUID values.
- Preserve cent-accurate numeric types for financial amounts.
- Treat `public.groups.balance` as a `NUMERIC(12,2) NOT NULL DEFAULT 0` cache of `group_ledger`; spend guards raise on a missing or insufficient balance instead of passing.
- Maintain server-side invariants through idempotent RPCs and triggers.
- Keep RLS policies and storage policies synchronized with the schema and application access patterns.
