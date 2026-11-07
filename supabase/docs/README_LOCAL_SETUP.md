# Local Supabase setup

The repository is configured for Supabase local development through Docker.

## Files

- `supabase/config.toml` — project configuration. `[db.seed]` is enabled and `sql_paths` points at `./SEED_ALL_DATA.sql`.
- `supabase/migrations/00000000000000_GROUND_ZERO.sql` — generated baseline, and the only file `db reset` applies.
- `supabase/legacy_migrations/43_*.sql`, `44_*.sql`, `45_*.sql` — superseded idempotency migrations, kept for history and deliberately outside `supabase/migrations/`. Re-applying them after the baseline strips the `[FIN_002]`, `[FIN_003]` and `[SEC_002]` guards from three money-path functions; see `README_MIGRATION_AUTORUN.md`.
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

## Platform admin credential

`04_PLATFORM_ADMIN_SETUP.sql` reads the owner's password from
`current_setting('sanibonani.platform_admin_password')` (the seed's fixture admin from its own
`sanibonani.seed_platform_admin_password` — see below) because the value that used to be committed in
those files is published on GitHub. So after any reset or rebuild the admin row exists — profile role,
`auth.identities` row and `app_metadata.role` all set — but `auth.users.encrypted_password` is NULL and
the account cannot sign in. It is a `NOTICE`, not an error, so the reset still reports success.

`scripts/run-supabase-local-reset.ps1` supplies it from `local.properties` (`PLATFORM_ADMIN_PASSWORD`,
the same key `BuildConfig.PLATFORM_ADMIN_PASSWORD` reads) and then proves the stored hash authenticates
that value from a separate session. To do it by hand on any tier:

```sql
BEGIN;
SET LOCAL sanibonani.platform_admin_password = '<the password only, never committed>';
-- then the contents of supabase/04_PLATFORM_ADMIN_SETUP.sql
COMMIT;
```

Verify without trusting the applying session: `SELECT extensions.crypt('<password>', encrypted_password) = encrypted_password FROM auth.users WHERE lower(email) = 'torrymsimango@gmail.com';`. A check inside the applying transaction is circular — it compares the same string it just hashed.

### Seeded platform_admin fixture

The seed also creates `platform.admin@master.example.com` — the account that owns every `LIGHT-*` and
`SEED-*` group — with `role='platform_admin'`. It reads its own setting,
`sanibonani.seed_platform_admin_password`, so it does not share the personas' published `Pass123!`; unset
means the fixture is created with a NULL `encrypted_password` (an existing row keeps its password, because
the seed only ever creates). `run-supabase-local-reset.ps1` supplies it from `SEED_PLATFORM_ADMIN_PASSWORD`
in `local.properties`, prefixes it as a session-level `SET` (the seed file opens its own `BEGIN`/`COMMIT`, so
`SET LOCAL` would be cleared before the `DO` blocks run), and verifies the stored hash from a second session.
Sign in as this fixture only to exercise group-owner flows; member personas still use `Pass123!`.

To set either credential by hand on any tier, substitute the matching setting name:

```sql
BEGIN;
SET LOCAL sanibonani.seed_platform_admin_password = '<the password only, never committed>';
-- then the contents of supabase/11_SEED_TEST_DATA_10_GROUPS_100_MEMBERS.sql (or an UPDATE of that one row)
COMMIT;
```

## Notes

- Never run `db reset`, `00_REBUILD_ALL.sql`, or the online full rebuild against an existing cloud tier. Cloud changes go through `supabase/online/SYNC_DELTA_<date>.sql`.
- If the CLI reports `dockerDesktopLinuxEngine` is missing, restart Docker Desktop and wait for the engine.
- After changing any seed module, regenerate `SEED_ALL_DATA.sql` with `supabase/scripts/generate_integrated_seed.ps1`.
