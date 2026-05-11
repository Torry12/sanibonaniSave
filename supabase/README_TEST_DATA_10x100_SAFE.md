# Safe Test Seed (10 Groups / 100 Members)

Minimal deterministic seed for app logic debugging when strict table constraints block broader seed packs.

## Files

- `supabase/22_SEED_TEST_DATA_10_GROUPS_100_MEMBERS_SAFE.sql`
  - Creates 10 groups and 100 members only
  - Includes `SEED100_GROUP_CREATED` audit markers
  - Idempotent for `SEED100-G*` records
- `supabase/23_VERIFY_TEST_DATA_10_GROUPS_100_MEMBERS_SAFE.sql`
  - Quick sanity checks for group/member counts and status distribution

## Prerequisites

1. `supabase/01_DATABASE_SCHEMA.sql`
2. `supabase/03_PLATFORM_ADMIN_SETUP.sql`

## Run in Supabase SQL Editor

1. `supabase/22_SEED_TEST_DATA_10_GROUPS_100_MEMBERS_SAFE.sql`
2. `supabase/23_VERIFY_TEST_DATA_10_GROUPS_100_MEMBERS_SAFE.sql`

## Expected Outcomes

- 10 groups where `name LIKE 'SEED100-G%'`
- 100 members where `email LIKE 'seed100.member.%@example.com'`
- Member statuses include `active`, `probation`, and `pending_payment`

## Notes

- This safe seed intentionally avoids payouts/platform fees/claims to reduce environment-specific constraint failures.
- It is suitable for UI routing, role scoping, pagination, search, and basic member/group business-flow testing.

