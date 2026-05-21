-- 03_seed.sql
-- Applies safe seed data for a fresh environment.
-- This seed references the consolidated safe seed file that ships with the repo.
-- Usage: psql -h <host> -p <port> -U <user> -d <db> -f 03_seed.sql
\echo "Applying consolidated safe seed..."
\i ../CONSOLIDATED_FOR_DASHBOARD_SCHEMA_PLUS_SAFE_SEED.sql
\echo "Seed application complete."

