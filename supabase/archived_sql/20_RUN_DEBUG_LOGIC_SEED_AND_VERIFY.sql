-- -----------------------------------------------------------------------------
-- SanibonaniSave - DEBUG LOGIC ORCHESTRATOR (Seed + Verify)
-- Runs debug-risk view, debug seed, then verification in one command.
--
-- NOTE:
-- - This script is intended for psql/supabase-cli shell execution because it uses
--   psql include directives (\i).
-- - Supabase SQL Editor does not execute \i directives.
-- -----------------------------------------------------------------------------

-- psql meta-commands removed for Dashboard compatibility.
-- \set ON_ERROR_STOP on

-- \echo 'Applying platform member behavior insights view...'
-- \i supabase/16_PLATFORM_MEMBER_BEHAVIOR_INSIGHTS.sql

-- \echo 'Seeding debug logic scenarios...'
-- \i supabase/17_SEED_DEBUG_LOGIC_SCENARIOS.sql

-- \echo 'Running verification queries...'
-- \i supabase/18_VERIFY_DEBUG_LOGIC_SCENARIOS.sql

-- \echo 'Debug seed + verification completed.'

