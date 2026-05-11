-- -----------------------------------------------------------------------------
-- SanibonaniSave - DEBUG LOGIC ORCHESTRATOR (Cleanup)
-- Runs debug cleanup in one command.
--
-- NOTE:
-- - This script is intended for psql/supabase-cli shell execution because it uses
--   psql include directives (\i).
-- - Supabase SQL Editor does not execute \i directives.
-- -----------------------------------------------------------------------------

\set ON_ERROR_STOP on

\echo 'Cleaning up debug logic scenarios...'
\i supabase/19_CLEANUP_DEBUG_LOGIC_SCENARIOS.sql

\echo 'Debug cleanup completed.'

