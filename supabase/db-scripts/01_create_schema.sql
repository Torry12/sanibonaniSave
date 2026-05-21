-- 01_create_schema.sql
-- Applies the consolidated schema to create the public schema from scratch.
-- This script uses psql meta-command \i to include the consolidated schema file that lives in the supabase folder.
-- Usage: psql -h <host> -p <port> -U <user> -d <db> -f 01_create_schema.sql
\echo "Applying consolidated schema..."
\i ../CONSOLIDATED_SCHEMA_FOR_DASHBOARD.sql
\echo "Schema application complete."

