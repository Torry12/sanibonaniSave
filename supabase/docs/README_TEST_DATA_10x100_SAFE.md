# Safe Test Seed (10 Groups / 100 Members)

Minimal deterministic seed for app logic debugging when strict table constraints block broader seed packs.

## Files

- `supabase/22_SEED_TEST_DATA_10_GROUPS_100_MEMBERS_SAFE.sql`
  - Creates 10 groups and 100 members
  - Ensures every group has a primary admin who is also a member
  - Adds 1–2 extra group-admin members per group as linked auth/profile users
  - Includes `SEED100_GROUP_CREATED` audit markers
  - Idempotent for `SEED100-G*` records
- `supabase/23_VERIFY_TEST_DATA_10_GROUPS_100_MEMBERS_SAFE.sql`
  - Quick sanity checks for group/member counts, admin membership, and status distribution

## Prerequisites

1. `supabase/01_DATABASE_SCHEMA.sql`
2. `supabase/03_PLATFORM_ADMIN_SETUP.sql`

## Run in Supabase SQL Editor

1. `supabase/22_SEED_TEST_DATA_10_GROUPS_100_MEMBERS_SAFE.sql`
2. `supabase/23_VERIFY_TEST_DATA_10_GROUPS_100_MEMBERS_SAFE.sql`

## Expected Outcomes

- 10 groups where `name LIKE 'SEED100-G%'`
- 100 members total, including primary-admin and extra-admin members
- Member statuses include `active`, `probation`, and `pending_payment`
- Every group has at least one linked `group_admin` profile in addition to the primary admin

## Notes

- This safe seed still avoids platform fees/claims-heavy flows, but it does create deterministic contribution, payout, and ledger coverage for validation.
- It is suitable for UI routing, role scoping, pagination, search, and basic member/group business-flow testing.

