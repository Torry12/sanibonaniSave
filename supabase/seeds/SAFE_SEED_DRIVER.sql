-- SAFE_SEED_DRIVER.sql
-- Canonical driver: applies only the canonical safe seed. Legacy/test seeds are deprecated.
-- For advanced or stress scenarios, see E2E_SEED.sql or legacy seeds (deprecated).
\echo '\n-- Applying canonical safe seed (SAFE_SEED.sql)'
\i 'supabase/seeds/SAFE_SEED.sql'
\echo '\n-- Safe seed applied.'
-- Legacy/test seeds below are deprecated and not included by default:
-- \i 'supabase/22_SEED_TEST_DATA_10_GROUPS_100_MEMBERS_SAFE.sql'
-- \i 'supabase/11_SEED_TEST_DATA_10_GROUPS_100_MEMBERS.sql'
-- \i 'supabase/13_CREATE_TEST_LOGIN_PROFILES.sql'
-- \i 'supabase/16_PLATFORM_MEMBER_BEHAVIOR_INSIGHTS.sql'
