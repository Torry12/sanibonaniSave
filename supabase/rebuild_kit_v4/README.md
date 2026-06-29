# SanibonaniSave Database Rebuild Kit (v4.0)

This folder contains a verified set of SQL scripts to rebuild and maintain the SanibonaniSave database. 
The scripts are aligned with the latest app features, including performance optimizations and strict security policies.

## Execution Order
To ensure all dependencies (foreign keys, views, functions) are satisfied, run the scripts in the following numbered order in your **Supabase SQL Editor**:

1.  **`00_SCHEMA_RESET.sql`**: Drops the existing `public` schema and recreates it. (Use with caution!)
2.  **`01_TABLES_AND_INDEXES.sql`**: Creates all database tables and core performance indexes.
3.  **`02_FUNCTIONS_AND_TRIGGERS.sql`**: Adds atomic business logic (loans, contributions) and automatic timestamp/count triggers.
4.  **`03_PERFORMANCE_VIEWS.sql`**: Creates high-performance views for the Landing Screen and Dashboards.
5.  **`04_SECURITY_AND_RLS.sql`**: Enforces strict Row Level Security (RLS) and grants appropriate permissions.
6.  **`05_SEED_DATA.sql`**: Populates the database with 15 Groups and 150 Members for testing and development.

## Maintenance Notes
*   **Permissions**: If you encounter "Database permissions are not configured" in the app, re-run `04_SECURITY_AND_RLS.sql`.
*   **Stats**: If stats on the Landing Screen show 0 or fail to load, ensure `03_PERFORMANCE_VIEWS.sql` has been run.
*   **Constraints**: All strings (descriptions, bank names) have strict regex rules—avoid spaces in technical identifiers.
*   **App alignment**: Kotlin PostgREST projections live in `data/src/main/java/com/sanibonani/save/data/remote/PostgrestColumns.kt`. Keep `01_TABLES_AND_INDEXES.sql` in sync with that file.
*   **Legacy databases**: If member sync fails with `column members.<col> does not exist`, run `supabase/migrations/20260529120000_align_members_app_columns.sql` (or `supabase/41_ALIGN_MEMBERS_APP_COLUMNS.sql`).
*   **Deprecated SQL**: Do not provision new environments from `CONSOLIDATED_MINIMAL_SCHEMA_SEED_RLS.sql` or `SCHEMA_FULL_RESET.sql`—they use a slim `members` table incompatible with the app.
