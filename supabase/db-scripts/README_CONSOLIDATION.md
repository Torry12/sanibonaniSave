sCONSOLIDATION & CLEANUP - README

Purpose
-------
These scripts assist maintainers to consolidate dispersed SQL artifacts into a
single canonical consolidated SQL and safely archive redundant legacy files.

Files added
-----------
- `07_consolidate_and_cleanup.sql`  - execution plan and list of files suggested for archive
- `08_generate_inlined_consolidated.ps1` - PowerShell helper to create an inlined SQL file and optionally archive listed redundant files
- `06_rls_debug_fix_safe.sql` - safe diagnostics for RLS (created earlier)

Recommended workflow
--------------------
1) Ensure you have a full backup of your development/staging DB.
2) Run the consolidated schema & RLS application on a fresh development DB using the order in `07_consolidate_and_cleanup.sql`.
   Example (PowerShell):

```powershell
$env:PGPASSWORD = 'your-password'
psql -h your-db-host -p 5432 -U your-db-user -d your-db-name -f "C:\Users\CRISS\AndroidStudioProjects\SanibonaniSave_Full\supabase\CONSOLIDATED_SCHEMA_FOR_DASHBOARD.sql"
psql -h your-db-host -p 5432 -U your-db-user -d your-db-name -f "C:\Users\CRISS\AndroidStudioProjects\SanibonaniSave_Full\supabase\02_SECURITY_AND_RLS.sql"
psql -h your-db-host -p 5432 -U your-db-user -d your-db-name -f "C:\Users\CRISS\AndroidStudioProjects\SanibonaniSave_Full\supabase\39_ENGINEERING_PERFORMANCE_OPTIMIZATIONS.sql"
psql -h your-db-host -p 5432 -U your-db-user -d your-db-name -f "C:\Users\CRISS\AndroidStudioProjects\SanibonaniSave_Full\supabase\CONSOLIDATED_FOR_DASHBOARD_SCHEMA_PLUS_SAFE_SEED.sql"
```

3) Run `06_rls_debug_fix_safe.sql` diagnostics and inspect output. If RLS is blocking data,
   use the debug policy toggles in that file (ENABLED ONLY IN DEV) to confirm visibility.

4) After thorough verification, you may archive redundant scripts using the
   `08_generate_inlined_consolidated.ps1 -Archive true` option. This moves files
   into `supabase/archived_sql/` (git will then show them as removed; commit that change
   only after verifying you have backups).

Safety notes
------------
- Never archive/delete SQL used by production without team agreement and backups.
- The PowerShell archiving step is destructive (moves files). Review the list in
  `07_consolidate_and_cleanup.sql` before running with `-Archive true`.

If you want, I can:
- Run the safe diagnostics against a development DB if you provide credentials,
- Generate the inlined consolidated SQL for you now (no archive), or
- Archive the redundant files locally (I will only do this if you confirm).


