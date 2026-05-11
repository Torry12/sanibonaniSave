# Test Login Profiles

This setup creates deterministic login users for role-based auth testing:

- Platform Admin
- Group Admin
- Members

## Files

- `supabase/13_CREATE_TEST_LOGIN_PROFILES.sql`
- `supabase/14_VERIFY_TEST_LOGIN_PROFILES.sql`
- `supabase/15_CLEANUP_TEST_LOGIN_PROFILES.sql`

## Prerequisites

1. `supabase/01_DATABASE_SCHEMA.sql`
2. `supabase/03_PLATFORM_ADMIN_SETUP.sql`
3. `supabase/11_SEED_TEST_DATA_10_GROUPS_100_MEMBERS.sql`

## Run Order

1. Run `supabase/13_CREATE_TEST_LOGIN_PROFILES.sql`
2. Run `supabase/14_VERIFY_TEST_LOGIN_PROFILES.sql`

## Teardown / Cleanup

When QA is done, run:

- `supabase/15_CLEANUP_TEST_LOGIN_PROFILES.sql`

This removes the test group-admin + seeded-member auth users and unlinks seeded members from `members.user_id`.

## Test Credentials

> Test-only credentials. Do not use in production.

- Platform Admin
  - Email: `torrymsimango@gmail.com`
  - Password: `torry123M`

- Group Admin
  - Email: `test.groupadmin@example.com`
  - Password: `Test@12345`

- Members (10 users from first seeded group)
  - Email pattern: `seed.member.XX.YY@example.com`
  - Password (all): `Test@12345`

## Notes

- The script links member auth users to existing seeded members (`members.user_id`) so member login flows map to real member records.
- The script assigns the first `SEED-G%` group to the test group admin for realistic admin dashboard testing.
- Re-running the script refreshes passwords/metadata and is safe for repeated QA cycles.

