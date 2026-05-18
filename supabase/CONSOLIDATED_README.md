Consolidated SQL Apply Guide

Purpose
-------
This directory-level helper makes it easy to apply the project's schema, migrations, and seeds in a consistent, reviewed order.

Files created
------------
- `CONSOLIDATED_APPLY.sql` — master apply script which includes the project's schema, migrations and seed SQL files using psql `\i` includes.

Why this approach
-----------------
- Keeps original SQL files intact and human-readable (we do not rewrite them).
- Provides a single canonical order for applying schema + migrations + seeds for local/staging rebuilds.
- Helps CI and automation run a reproducible apply sequence.

How to run (psql)
------------------
From the repository root, run psql with a connection string environment variable.

PowerShell example (Windows):

```powershell
$env:PG_CONN = "host=localhost port=5432 dbname=postgres user=postgres password=postgres"
psql $env:PG_CONN -f supabase/CONSOLIDATED_APPLY.sql
```

Bash example (Linux/macOS):

```bash
export PG_CONN="host=localhost port=5432 dbname=postgres user=postgres password=postgres"
psql "$PG_CONN" -f supabase/CONSOLIDATED_APPLY.sql
```

How to run (supabase CLI)
-------------------------
If you prefer the Supabase CLI, you can apply files individually. The `CONSOLIDATED_APPLY.sql` uses psql includes; the Supabase CLI doesn't directly evaluate `\i` the same way, so run psql directly or expand the includes first.

Safety notes
------------
- Always backup the target DB before running this in production.
- Review each included file for risky direct `UPDATE public.groups SET balance` patterns. Prefer running the `scripts/check_sql_balance_updates.ps1` script before applying the consolidated apply.

Recommended workflow
--------------------
1. Run `.	ools
un_local_supabase.sh` (if you have a local instance), or ensure your PG connection details are set.
2. Run `.	oolsackup_db.ps1` to capture a DB snapshot.
3. Run `.	ools
un_sql_in_order.ps1` or directly call psql to run `supabase/CONSOLIDATED_APPLY.sql`.
4. Review `public.migration_balance_recompute_audit` if you run the recompute migration.

If you want, I can:
- Add a PowerShell or Bash wrapper that expands includes and runs them with error handling/logging.
- Make an npm/gradle task to run the consolidated apply automatically with environment variable checks.

