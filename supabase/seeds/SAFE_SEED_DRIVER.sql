-- SAFE_SEED_DRIVER.sql
-- Combines canonical safe seed scripts into a single include for safer application.
\echo '\n-- Applying safe seed group (includes vetted safe seed scripts)'
\i 'supabase/22_SEED_TEST_DATA_10_GROUPS_100_MEMBERS_SAFE.sql'
\i 'supabase/11_SEED_TEST_DATA_10_GROUPS_100_MEMBERS.sql'
\i 'supabase/13_CREATE_TEST_LOGIN_PROFILES.sql'
\i 'supabase/16_PLATFORM_MEMBER_BEHAVIOR_INSIGHTS.sql'
\echo '\n-- Safe seeds applied.'

