# Supabase Local Migration Auto-Apply

This guide runs all SQL migrations under `supabase/migrations/` against your local Supabase stack.

## Prerequisites

- Docker Desktop running (Linux engine enabled)
- Supabase CLI installed
- Project root opened at `SanibonaniSave_Full`

## Auto-Apply Migrations (No Seed)

```powershell
cd "C:\Users\CRISS\AndroidStudioProjects\SanibonaniSave_Full"
supabase --version
supabase start
supabase db reset --local --no-seed --yes
```

## Auto-Apply + Optional Seed

```powershell
cd "C:\Users\CRISS\AndroidStudioProjects\SanibonaniSave_Full"
supabase db reset --local --no-seed --yes
supabase db query --local --file .\supabase\22_SEED_TEST_DATA_10_GROUPS_100_MEMBERS_SAFE.sql
```

## Verify Applied Migrations

```powershell
cd "C:\Users\CRISS\AndroidStudioProjects\SanibonaniSave_Full"
supabase migration list
```

## One-Command Helper Script

Use:

```powershell
cd "C:\Users\CRISS\AndroidStudioProjects\SanibonaniSave_Full"
.\scripts\run-supabase-local-reset.ps1 -NoSeed
```

With seed:

```powershell
cd "C:\Users\CRISS\AndroidStudioProjects\SanibonaniSave_Full"
.\scripts\run-supabase-local-reset.ps1 -SeedFile ".\supabase\22_SEED_TEST_DATA_10_GROUPS_100_MEMBERS_SAFE.sql"
```

## Notes

- If Docker is not running, local commands fail with pipe/engine errors.
- Migration order is timestamp-based from `supabase/migrations/`.
- Keep top-level SQL files and migration copies aligned when changing constraints.

