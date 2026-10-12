# Test Data Seed (10 Groups / 100 Members)

This seed creates a realistic QA dataset for business-logic testing in SanibonaniSave.

## Files

- `supabase/11_SEED_TEST_DATA_10_GROUPS_100_MEMBERS.sql`
  - Idempotent seed script for 10 groups and 100 members
  - Also creates linked data (contributions, payments, notifications, loans, loan repayments, platform fees, actuarial metrics, burial beneficiaries/claims, payouts, audit logs)
- `supabase/12_VERIFY_TEST_DATA_10_GROUPS_100_MEMBERS.sql`
  - Verification queries for quick sanity checks

## Prerequisites

1. Base schema applied (`supabase/01_DATABASE_SCHEMA.sql`)
2. Platform admin exists from `supabase/03_PLATFORM_ADMIN_SETUP.sql` with email:
   - `torrymsimango@gmail.com`

## Run in Supabase SQL Editor

1. Execute:
   - `supabase/11_SEED_TEST_DATA_10_GROUPS_100_MEMBERS.sql`
2. Then execute:
   - `supabase/12_VERIFY_TEST_DATA_10_GROUPS_100_MEMBERS.sql`

## Expected minimum outcomes

- `groups`: `10` (`name LIKE 'SEED-G%'`)
- `members`: `100` (`email LIKE 'seed.member.%@example.com'`)
- Linked records across contributions, payments, loans, claims, notifications, and ledgers for business-flow testing

## Notes

- The script only cleans/replaces old seed records where group name starts with `SEED-G`.
- Existing non-seed data remains untouched.
- Group ownership is assigned to the existing platform admin so admin dashboard and impersonation flows can be tested immediately.

