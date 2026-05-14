# Local Supabase Setup

This repository is now initialized for Supabase local development.

## What was added
- `supabase/config.toml`
- `supabase/migrations/`
- `supabase/README_FULL_APP_E2E.md`
- `supabase/29_ALIGN_VALIDATION_CONSTRAINTS_WITH_APP.sql`
- `supabase/30_CONSOLIDATED_RLS_ALIGNMENT.sql`

## Current migration set
The local migration folder includes the project’s schema, security, admin, and alignment history:
- `20260514000100_initial_schema.sql`
- `20260514000200_security_and_rls.sql`
- `20260514000300_platform_admin_setup.sql`
- `20260514000400_migrations_and_updates.sql`
- `20260514000500_platform_admin_auth_alignment.sql`
- `20260514000600_platform_admin_rls_hotfix.sql`
- `20260514000700_add_rosca_rotation_method.sql`
- `20260514000800_architecture_model_schema_templates.sql`
- `20260514000900_align_validation_constraints_with_app.sql`
- `20260515000000_consolidated_rls_alignment.sql`

## How to apply locally
Once Docker Desktop is running and the Linux engine is available:

```powershell
cd "C:\Users\CRISS\AndroidStudioProjects\SanibonaniSave_Full"
supabase db reset --local --no-seed --yes
```

## Notes
- `db.seed.enabled` is disabled in `config.toml` so reset will not require `seed.sql`.
- If the CLI reports that `dockerDesktopLinuxEngine` is missing, restart Docker Desktop and wait until the engine is ready.
- The reset command applies the migration history to the local database.

