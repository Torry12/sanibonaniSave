# Test login profiles

The former `13_CREATE_TEST_LOGIN_PROFILES.sql` / `14_…VERIFY…` / `15_…CLEANUP…` scripts are no longer part of this repository.

Login personas now come from the master seed (`MASTER_SEED_DATA.sql`, bundled into `SEED_ALL_DATA.sql`). The authoritative list of accounts, roles, and group assignments is **`QA_LOGIN_CREDENTIALS.md`** at the repository root.

## Local setup

```powershell
npx supabase db reset      # applies SEED_ALL_DATA.sql, including all personas
```

## Rules

- Personas are local-development fixtures; never seed them into the cloud tier.
- The shared QA password is intentional for test personas; do not "harden" it in seed data.
- When adding or renaming a persona, update `MASTER_SEED_DATA.sql` and `QA_LOGIN_CREDENTIALS.md` together, then regenerate `SEED_ALL_DATA.sql`.
