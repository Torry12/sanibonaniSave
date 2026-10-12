# Full App E2E Seed

Comprehensive seed pack to test nearly every major feature path in the app using realistic, mixed-status data.

## Files

- `supabase/25_SEED_FULL_APP_E2E.sql`
  - Seeds all 8 group types (`burial_society`, `stokvel`, `rosca`, `investment_club`, `emergency_fund`, `community_savings`, `tontine`, `other`)
  - Seeds members with mixed lifecycle states (`active`, `probation`, `pending_payment`, `suspended`)
  - Seeds contributions, payments, loans, loan repayments, payouts, platform fees, notifications, audit logs, actuarial metrics, and ledger rows
  - Seeds burial claims/beneficiaries for burial-society workflows
  - Handles environment drift for payout/platform-fee status constraints
  - Idempotent for `E2E-SEED-%` scope
- `supabase/26_VERIFY_FULL_APP_E2E.sql`
  - Quick verification queries for counts, status distribution coverage, and latest validation-rule alignment checks
- `supabase/29_ALIGN_VALIDATION_CONSTRAINTS_WITH_APP.sql`
  - Aligns existing database constraints with latest app/domain business rules
  - Tightens `payment_due_day` to `1..28`
  - Enforces SA banking formats: account `7..11` digits, branch code `6` digits

## Prerequisites

1. `supabase/01_DATABASE_SCHEMA.sql`
2. `supabase/03_PLATFORM_ADMIN_SETUP.sql`

## Run Order

1. `supabase/25_SEED_FULL_APP_E2E.sql`
2. `supabase/29_ALIGN_VALIDATION_CONSTRAINTS_WITH_APP.sql` (recommended for existing environments)
3. `supabase/26_VERIFY_FULL_APP_E2E.sql`

## Expected Coverage

- Group type logic and specialized business-insight routing
- Member onboarding, document review, and profile statuses
- Contribution/payment pipelines (paid/due/overdue/partial, pending/failed/completed)
- Loan lifecycle states (`pending`, `approved`, `partially_paid`, `overdue`, `completed`, `rejected`, `cancelled`)
- Payout/disbursement views with mixed statuses (constraint-aware)
- Burial claim lifecycle (including escalated and paid claims)
- Platform fee states and platform ledger rendering
- Audit logs, notifications, and maintenance-tab datasets

## Notes

- This pack uses one known platform admin (`torrymsimango@gmail.com`) as owner/reviewer.
- If your environment does not include statuses like `group_approved` or `overdue`, the seed auto-falls back to allowed values.
- The seed intentionally uses deterministic prefixes (`E2E-SEED-` and `E2E_SEED_`) so cleanup is safe and scoped.
- The latest verification script includes "expect zero rows" checks for payment due-day and banking field formats.

