db-scripts/ README

Purpose

This folder contains a small, safe sequence of scripts to recreate the SanibonaniSave database from scratch and apply seed data for development/testing.

Files and order

1) 01_create_schema.sql  - applies the consolidated schema file `../CONSOLIDATED_SCHEMA_FOR_DASHBOARD.sql` (drops/creates public schema)
2) 02_migrations.sql     - placeholder wrapper (the runner executes individual numbered migration files in the parent `supabase` folder)
3) 03_seed.sql           - applies consolidated safe seed `../CONSOLIDATED_FOR_DASHBOARD_SCHEMA_PLUS_SAFE_SEED.sql`
4) 04_run.ps1            - PowerShell runner that executes the above in order and also runs numbered migration files found in the parent `supabase` folder.
5) 05_run.sh             - POSIX shell runner (bash/sh) that supports dry-run, inlining all SQL into a single file and executing it.

Prerequisites

- psql (Postgres client) on PATH. (On Windows, install via Postgres installer or use the psql shipped with Postgres app.)
- A reachable Postgres instance (local Postgres, Supabase local, or remote DB). You need a superuser or a user permitted to DROP/CREATE schema and create tables.

Usage examples:

PowerShell (Windows):

```powershell
# one-time: set PGPASSWORD in the environment for the session
$host = 'localhost'
$port = 5432
$user = 'postgres'
$password = 'mypass'
$db = 'sanibonani'

# Run the rebuild+seed (PowerShell runner)
.\04_run.ps1 -Host $host -Port $port -User $user -Password $password -Database $db
```

POSIX (Linux / macOS / WSL / Git Bash):

```bash
# export PGPASSWORD or rely on .pgpass
export PGPASSWORD='mypass'
./05_run.sh -h localhost -p 5432 -U postgres -d sanibonani

# Create a single inlined SQL file (in supabase/db-scripts/inlined_all.sql) without executing
./05_run.sh -h localhost -p 5432 -U postgres -d sanibonani -n -i

# Create and execute the inlined SQL
./05_run.sh -h localhost -p 5432 -U postgres -d sanibonani -x
```

Notes and safety

- The consolidated schema file drops `public` schema (DROP SCHEMA IF EXISTS public CASCADE). Do NOT run this against a production database.
- This set of scripts is intended for development/testing and CI where you want a clean database.
- The runner intentionally skips files that look like seed dumps when applying migrations; the consolidated seed is applied once at the end.
- If you prefer `supabase` CLI, you can run the SQL files manually with `supabase db remote connect` or via the Supabase SQL editor.

If you want me to instead produce a single combined SQL file (fully inlined) that contains the schema + migrations + seed in one file, tell me and I will produce it.

