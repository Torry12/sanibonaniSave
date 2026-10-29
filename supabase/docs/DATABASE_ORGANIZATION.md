# Supabase database organization

## Source modules

The authoritative SQL modules are in the `supabase/` directory, not in a separate `v4_canonical/` tree:

- `01_DATABASE_SCHEMA.sql` — tables, extensions, constraints, and indexes
- `02_FUNCTIONS_AND_TRIGGERS.sql` — functions and triggers
- `18_DEPLOY_PROCESS_COMPLEX_PAYMENT_V4.sql` — complex payment RPC
- `03_SECURITY_AND_RLS.sql` — row-level security and grants
- `04_PLATFORM_ADMIN_SETUP.sql` — platform administrator setup
- `05_PERFORMANCE_VIEWS.sql` — analytics and reporting views
- `30_STORAGE_RLS.sql` — storage buckets and policies
- `online/01_ONLINE_SPECIFIC_SETUP.sql` — production-only overrides

The rebuild drivers `00_REBUILD_ALL.sql` and `online/00_REBUILD_ONLINE.sql` define the execution order. Each includes `07_RESET_DATABASE.sql`; these are full rebuilds and can delete existing data. Only the local driver includes development seeds.

## Generated SQL

After changing a source module, run these scripts from the repository root:

```powershell
.\supabase\scripts\generate_ground_zero.ps1
.\supabase\scripts\generate_consolidated_schema.ps1
.\supabase\scripts\generate_online_sync.ps1
.\supabase\scripts\generate_integrated_seed.ps1
```

They update the ground-zero migration baseline, dashboard bundle, online full-rebuild bundle, and integrated local seed respectively. Do not hand-edit those generated copies.

For an existing cloud database, use the dated additive deltas in `online/` (`SYNC_DELTA_20261014`, `_20261021`, `_20261022`, `_20261026`, `_20261026B`, `_20261027`, `_20261028`, `_20261029`). All seven through `_20261028` are applied on Local; Cloud still owes `20261027` and `20261028`, and `20261029` is cut but applied on neither tier. Do not use a full-rebuild script to synchronize an existing tier, and never rewrite or re-run an applied delta. Each delta carries a read-only pre-flight and a fingerprint gate: compare the reported hashes with the ones recorded in the file, and stop for review if any body differs. Take a backup before applying database changes. `maintenance/SCHEMA_PARITY_DIGEST.sql` and `maintenance/SCHEMA_OBJECT_FINGERPRINTS.sql` are the read-only drift probes.

## Integrity requirements

- Store financial amounts in exact PostgreSQL numeric types and keep them aligned with the Kotlin `Money` model.
- `public.groups.balance` is a `NUMERIC(12,2) NOT NULL DEFAULT 0` cache of `group_ledger`; spend guards must treat a missing balance as zero and raise rather than pass.
- Use `TEXT` for external ledger transaction references that may not be UUIDs.
- Use idempotency keys and atomic RPCs for ledger-affecting writes.
- Keep member counts and derived financial totals maintained by server-side triggers.
- Keep RLS, storage policies, and RPC return shapes aligned with application models.
