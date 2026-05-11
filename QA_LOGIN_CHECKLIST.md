# QA Login Checklist

Reusable manual QA checklist for login, role routing, and basic authorization in SanibonaniSave.

## Scope

Covers:
- Platform Admin login
- Group Admin login
- Member login
- Role-based route guards
- Common negative cases

## Prerequisites

Run these in Supabase SQL Editor (in order):

```sql
-- Base schema and platform admin
-- supabase/01_DATABASE_SCHEMA.sql
-- supabase/03_PLATFORM_ADMIN_SETUP.sql

-- Test business data and login profiles
-- supabase/11_SEED_TEST_DATA_10_GROUPS_100_MEMBERS.sql
-- supabase/13_CREATE_TEST_LOGIN_PROFILES.sql

-- Optional sanity checks
-- supabase/12_VERIFY_TEST_DATA_10_GROUPS_100_MEMBERS.sql
-- supabase/14_VERIFY_TEST_LOGIN_PROFILES.sql
```

## Test Accounts

- Platform Admin
  - Email: `torrymsimango@gmail.com`
  - Password: `torry123M`

- Group Admin
  - Email: `test.groupadmin@example.com`
  - Password: `Test@12345`

- Member (example)
  - Email: `seed.member.01.01@example.com`
  - Password: `Test@12345`

## Checklist

### 1) Platform Admin

- [ ] Sign in as platform admin
- [ ] Confirm navigation lands on platform dashboard
- [ ] Confirm platform-wide visibility (groups/analytics/disbursements)
- [ ] Perform one admin action (for example suspend/unsuspend)
- [ ] Refresh and confirm state persisted

Expected:
- Role resolves as `platform_admin`
- No unauthorized/permission errors for platform screens

### 2) Group Admin

- [ ] Sign out and sign in as group admin
- [ ] Confirm navigation lands on group admin dashboard
- [ ] Confirm only assigned seeded group is managed
- [ ] Confirm platform admin screen is blocked/redirected
- [ ] Open member list for assigned group

Expected:
- Role resolves as `group_admin`
- Scope is limited to assigned group

### 3) Member

- [ ] Sign out and sign in as member
- [ ] Confirm navigation lands on member dashboard
- [ ] Confirm member sees own/member-scoped data only
- [ ] Confirm admin routes are blocked/redirected
- [ ] Open contributions/history to verify seeded records exist

Expected:
- Role resolves as `member`
- No admin/platform actions available

### 4) Negative Cases

- [ ] Invalid email format (for example `invalid_email`) shows validation error
- [ ] Wrong password shows user-friendly invalid credentials message
- [ ] Email normalization works (for example uppercase/leading-trailing spaces)
- [ ] Password is treated exactly as entered (not silently trimmed)

Expected:
- Errors are user-friendly
- No crashes

### 5) Route Guard Sanity

- [ ] Logged in as member, attempt admin route deep link
- [ ] Logged in as group admin, attempt platform route deep link

Expected:
- App redirects to authorized route
- No privilege escalation

## Teardown (Optional)

Run when QA is done:

```sql
-- Remove test login profiles
-- supabase/15_CLEANUP_TEST_LOGIN_PROFILES.sql
```

If you also want to reset seeded business data, re-run your chosen seed script sequence from scratch.

