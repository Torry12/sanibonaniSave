# Debug Logic Scenario Seed

Focused deterministic seed data for debugging SanibonaniSave business rules and programmatic flow logic.

## Files

- `supabase/17_SEED_DEBUG_LOGIC_SCENARIOS.sql`
  - Creates 4 scenario groups (`DBG-*`) with deterministic edge cases
  - Includes members, contributions, payments, loans, payouts, burial claims, ledger entries, and audit markers
  - Idempotent for debug data (`DBG-*` only)
- `supabase/18_VERIFY_DEBUG_LOGIC_SCENARIOS.sql`
  - Verification queries by scenario, status, and risk profile
- `supabase/19_CLEANUP_DEBUG_LOGIC_SCENARIOS.sql`
  - Removes `DBG-*` groups and `DBG_%` debug markers only
- `supabase/20_RUN_DEBUG_LOGIC_SEED_AND_VERIFY.sql`
  - One-command orchestrator for `16` + `17` + `18` (psql include mode)
- `supabase/21_RUN_DEBUG_LOGIC_CLEANUP.sql`
  - One-command orchestrator for `19` (psql include mode)
- `supabase/16_PLATFORM_MEMBER_BEHAVIOR_INSIGHTS.sql` (recommended)
  - Enables server-backed member risk insight view used by platform admin portal

## Scenarios Included

1. `DBG-G01 Healthy Flow`
   - Mostly clean contributions and manageable disbursement workload
2. `DBG-G02 Loan Stress`
   - Mixed loan statuses (`pending`, `approved`, `active`, `overdue`, `rejected`)
3. `DBG-G03 Burial Escalation`
   - Escalated + approved + rejected burial payout claims
4. `DBG-G04 Suspension Edge`
   - Suspended fee posture, lower balance, pending/high-risk loan signals

## Prerequisites

1. `supabase/01_DATABASE_SCHEMA.sql`
2. `supabase/03_PLATFORM_ADMIN_SETUP.sql`
3. `supabase/04_MIGRATIONS_AND_UPDATES.sql`
4. (Recommended) `supabase/16_PLATFORM_MEMBER_BEHAVIOR_INSIGHTS.sql`

## Run Order (Supabase SQL Editor)

1. Run `supabase/17_SEED_DEBUG_LOGIC_SCENARIOS.sql`
2. Run `supabase/18_VERIFY_DEBUG_LOGIC_SCENARIOS.sql`

## One-Command Mode (psql / shell)

Use these if you prefer a single command flow:

- `supabase/20_RUN_DEBUG_LOGIC_SEED_AND_VERIFY.sql`
- `supabase/21_RUN_DEBUG_LOGIC_CLEANUP.sql`

Example:

```powershell
psql "$env:DATABASE_URL" -f "supabase/20_RUN_DEBUG_LOGIC_SEED_AND_VERIFY.sql"
psql "$env:DATABASE_URL" -f "supabase/21_RUN_DEBUG_LOGIC_CLEANUP.sql"
```

## Expected Debug Footprint

- 4 `DBG-*` groups
- 24 debug members
- Deterministic loan/payout/claim statuses for reproducible logic tests
- `DBG_*` audit log markers for quick traceability

## Teardown / Cleanup

Run when you are done with debugging:

- `supabase/19_CLEANUP_DEBUG_LOGIC_SCENARIOS.sql`

## Notes

- This script does **not** delete non-debug production/test records.
- Re-running is safe and only refreshes debug-tagged records.
- Use alongside platform admin portal filters and loan risk insights for triage debugging.

