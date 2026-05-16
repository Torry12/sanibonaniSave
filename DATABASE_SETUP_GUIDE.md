# SanibonaniSave — Complete Database Setup Guide

**Last Updated:** May 14, 2026  
**Database:** Supabase (PostgreSQL 15+)  
**Status:** Production-Ready

---

## 📋 Table of Contents

1. [Quick Start (Fresh Database)](#quick-start-fresh-database)
2. [Complete SQL Execution Sequence](#complete-sql-execution-sequence)
3. [Individual SQL Files Overview](#individual-sql-files-overview)
4. [Seed Data Options](#seed-data-options)
5. [Local Development Setup](#local-development-setup)
6. [Troubleshooting & Rollback](#troubleshooting--rollback)

---

## Quick Start (Fresh Database)

For a **completely fresh Supabase instance**, run these 4 files in order via the Supabase SQL Editor:

```sql
-- Step 1: Foundation schema and utilities
-- File: supabase/01_DATABASE_SCHEMA.sql

-- Step 2: Security, Row-Level Security (RLS), and policies
-- File: supabase/02_SECURITY_AND_RLS.sql

-- Step 3: Platform admin user setup
-- File: supabase/03_PLATFORM_ADMIN_SETUP.sql

-- Step 4: Application validation constraints alignment
-- File: supabase/29_ALIGN_VALIDATION_CONSTRAINTS_WITH_APP.sql
```

**Estimated Time:** 5–10 minutes  
**Result:** Empty but fully configured production-ready database

---

## Complete SQL Execution Sequence

### Phase 1: Core Infrastructure (Must-Run, In Order)

| Step | File | Purpose | Duration |
|------|------|---------|----------|
| 1 | `01_DATABASE_SCHEMA.sql` | Create all tables, constraints, indexes, and utility functions | 2 min |
| 2 | `02_SECURITY_AND_RLS.sql` | Enable RLS policies, auth triggers, encryption, audit logging | 2 min |
| 3 | `03_PLATFORM_ADMIN_SETUP.sql` | Create initial platform admin user and global config | 1 min |

**Total Phase 1:** ~5 minutes

---

### Phase 2: Alignment & Hotfixes (For Existing Environments)

| Step | File | Purpose | Conditions |
|------|------|---------|-----------|
| 4a | `29_ALIGN_VALIDATION_CONSTRAINTS_WITH_APP.sql` | Align DB constraints with app validation rules (account numbers, branch codes, etc.) | Always run on fresh installs; run once on existing DBs |
| 4b | `30_CONSOLIDATED_RLS_ALIGNMENT.sql` | Re-verify RLS policies match app role requirements | If RLS issues detected or after permission changes |
| 4c | `31_ATOMIC_BALANCE_UPDATES.sql` | Ensure balance transactions are atomic (no race conditions) | If running payment concurrency tests |
| 4d | `32_GROUP_VOTING_AND_MULTI_ADMIN.sql` | Adds group voting tables and extends `is_group_admin` for extra admin members | If enabling voting or multi-admin authorization |

**Total Phase 2:** ~2 minutes (conditional)

---

### Phase 3: Optional Migrations & Hotfixes (Only If Upgrading)

| Step | File | Purpose | When? |
|------|------|---------|-------|
| 5 | `04_MIGRATIONS_AND_UPDATES.sql` | DB schema updates and backward compatibility fixes | Upgrade from older schema versions |
| 6 | `04_PLATFORM_ADMIN_AUTH_ALIGNMENT.sql` | Sync platform admin authentication with role system | After auth system changes |
| 7 | `24_PLATFORM_ADMIN_RLS_HOTFIX.sql` | Fix RLS policies for platform admin access | If platform admin blocked from viewing data |
| 8 | `27_ADD_ROSCA_ROTATION_METHOD.sql` | Add ROSCA rotation strategy support | If ROSCA group support not present |

**When to Run:** Only if upgrading from an older version  
**Check First:** Query `INFORMATION_SCHEMA.TABLES` to see current schema version in comments

---

### Phase 4: Recommended Migrations (Supabase Local CLI)

If using **Supabase local development** with `supabase` CLI, these migration files are auto-applied in order:

```
supabase/migrations/
├── 20260514000100_initial_schema.sql
├── 20260514000200_security_and_rls.sql
├── 20260514000300_platform_admin_setup.sql
├── 20260514000400_migrations_and_updates.sql
├── 20260514000500_platform_admin_auth_alignment.sql
├── 20260514000600_platform_admin_rls_hotfix.sql
├── 20260514000700_add_rosca_rotation_method.sql
├── 20260514000800_architecture_model_schema_templates.sql
├── 20260514000900_align_validation_constraints_with_app.sql
├── 20260515000000_consolidated_rls_alignment.sql
├── 20260515000100_atomic_balance_updates.sql
└── 20260515000200_group_voting_and_multi_admin.sql
```

**Apply Locally:**
```powershell
cd "C:\Users\CRISS\AndroidStudioProjects\SanibonaniSave_Full"
supabase db reset --local --no-seed --yes
```

---

## Individual SQL Files Overview

### Core Schema Files (01–03)

#### **01_DATABASE_SCHEMA.sql** (535 lines)
**What it creates:**

- **Schemas & Extensions**
  - UUID extension (`uuid-ossp`)
  - Crypto extension (`pgcrypto`)

- **Core Tables**
  - `profiles` — Global user records synced from `auth.users`
  - `groups` — Savings group definitions (stokvel, burial society, ROSCA, etc.)
  - `members` — Group memberships
  - `contributions` — Monthly contribution records
  - `loans` — Loan requests and tracking
  - `loan_repayments` — Payment schedules and status
  - `payouts` — Disbursement requests from groups
  - `beneficiaries` — Burial society beneficiary records
  - `beneficiary_claims` — Death claim submissions
  - `notifications` — In-app & push notification queue
  - `member_documents` — Member KYC document storage
  - `audit_logs` — Complete audit trail (automatic)
  - `member_behavior_insights` — Actuarial scoring table

- **Constraints & Validations**
  - Account number: `7–11` digits (SA PASA standard)
  - Branch code: `6` digits
  - Payment due day: `1–28` (calendar-safe)
  - Email: RFC 5322 regex
  - Full name: minimum 3 characters
  - Balances: non-negative, 2 decimal places
  - Fees: non-negative

- **Utility Functions**
  - `update_updated_at_column()` — Auto-timestamp updates
  - `handle_new_user()` — Sync auth.users → profiles on signup

- **Indexes** (for query performance)
  - Composite indexes on (group_id, user_id)
  - Indexes on frequently queried fields (status, created_at)

---

#### **02_SECURITY_AND_RLS.sql** (400+ lines)
**What it enables:**

- **Row-Level Security (RLS) Policies**
  - **Members:** Can only view their own data + group-wide shared data
  - **Group Admins:** Full access to their own group + member records
  - **Platform Admins:** Read-only access to all groups and audit logs
  - **Unauthenticated:** No access (anon role denied)

- **Auth Triggers**
  - Profile auto-sync on user creation
  - FCM token updates
  - Member key generation

- **Data Encryption**
  - Sensitive fields (member IDs, phone numbers) hashed if required

- **Audit Logging**
  - All inserts/updates/deletes logged to `audit_logs`
  - Platform admin can review all changes

- **Storage Policies**
  - Document uploads restricted to member's own folder
  - Group constitution PDFs restricted to group members

---

#### **03_PLATFORM_ADMIN_SETUP.sql** (150+ lines)
**What it does:**

1. **Creates Platform Admin User**
   - Email: `torrymsimango@gmail.com` (can be customized)
   - Password: Auto-generated strong password (set manually post-deployment)
   - Role: `platform_admin` in `profiles` table

2. **Creates Global Configuration**
   - Platform registration fee: R700 (one-time)
   - Platform monthly fee: R10 per member
   - Actuarial defaults (mortality rate, safety loading, discount rate)
   - Default probation period: 3 months
   - Default payment due day: 28th of month

3. **Seeds Initial Data**
   - System-wide settings (notification preferences, fee schedules)
   - Admin role permissions

---

### Application Alignment Files (04, 24, 27, 29–31)

#### **04_PLATFORM_ADMIN_AUTH_ALIGNMENT.sql**
- Ensures platform admin user has correct role inheritance
- Syncs authentication system with app's role checks
- Run once after initial setup, then only after auth system changes

#### **24_PLATFORM_ADMIN_RLS_HOTFIX.sql**
- Fixes edge-cases where platform admin cannot view platform-level data
- Reset RLS policies for read-only platform dashboards
- **Run if:** Platform admin sees "No data" error in admin dashboard

#### **27_ADD_ROSCA_ROTATION_METHOD.sql**
- Adds ROSCA-specific group type support
- Enables rotation strategy configurations (fixed, random draw, need-based, auction)
- **Run if:** ROSCA groups not supported in current schema

#### **29_ALIGN_VALIDATION_CONSTRAINTS_WITH_APP.sql**
- Aligns database constraints with app-side input validation
- Ensures consistency between Kotlin validators and PostgreSQL constraints
- Examples:
  - Account number length: `7–13` digits (matches app)
  - Branch code: exactly `6` digits
  - Phone: South African format validation
  - Email: RFC regex
- **Always run on fresh installs**

#### **30_CONSOLIDATED_RLS_ALIGNMENT.sql**
- Comprehensive RLS policy review
- Ensures all roles (platform_admin, group_admin, member) have correct table access
- Validates permission hierarchies

#### **31_ATOMIC_BALANCE_UPDATES.sql**
- Wraps balance-update operations in transactions
- Prevents race conditions during concurrent payment processing
- **Run before:** High-concurrency payment testing

---

## Seed Data Options

After running **Phase 1 (01–03) + Phase 2 (29)**, choose a seed option:

### Option A: Empty Database (Production-like)

**No additional SQL needed.** Database is ready for live data entry.

```sql
-- Database has:
-- ✓ All tables and constraints
-- ✓ RLS policies active
-- ✓ Platform admin user created
-- ✓ No test data
```

**Use Case:** Production environment, QA staging (to add data via app UI)

---

### Option B: Minimal Fresh Start

**File:** `supabase/05_SEED_FRESH_START.sql` (83 lines)

Creates:
- 3 Stokvels
- 1 Burial Society
- 1 ROSCA group
- 1 Investment Club
- Group admin member in each group (pre-joined 120 days ago)

```sql
-- Run after 01–03:
-- File: supabase/05_SEED_FRESH_START.sql
```

**Use Case:** Development testing, demo environments

**Duration:** 30 seconds

---

### Option C: Small Stress Test

**File:** `supabase/08_SMALL_STRESS_TEST_SEED.sql` (250+ lines)

Creates:
- 5 groups with different types
- 50 members (10 per group)
- 100+ contribution records
- 10 loans
- 5 payouts

**Use Case:** Performance testing, API load testing

**Duration:** 1–2 minutes

---

### Option D: Comprehensive Test

**File:** `supabase/09_COMPREHENSIVE_TEST_SEED.sql` (500+ lines)

Creates:
- 8 groups with all types represented
- 150+ members
- Full transaction history
- Completed and pending loans
- Beneficiary claims
- Audit trail entries

**Use Case:** Integration testing, E2E app testing

**Run After:** 01–03, then 05 (fresh start)

**Duration:** 3–5 minutes

---

### Option E: Large Scale (300 Members)

**File:** `supabase/10_SCALED_TEST_SEED_300.sql` (1000+ lines)

Creates:
- 10 groups
- 300 members (30 per group)
- 1000+ contribution transactions
- 100+ loans
- Distributed across all group types

**Use Case:** Load testing, stress testing, performance profiling

```sql
-- Advanced: Only run if testing scalability
-- File: supabase/10_SCALED_TEST_SEED_300.sql
```

**Duration:** 10–15 minutes

---

### Option F: Safe 10 Groups × 100 Members

**File:** `supabase/22_SEED_TEST_DATA_10_GROUPS_100_MEMBERS_SAFE.sql` (800+ lines)

Creates:
- 10 production-like groups
- 100 realistic members
- Every group gets a primary admin who is also a member
- Each group gets 1–2 additional linked `group_admin` members
- Contribution, payout, and ledger coverage for validation
- Phone numbers and emails suitable for testing

**Verification Script:** `23_VERIFY_TEST_DATA_10_GROUPS_100_MEMBERS_SAFE.sql`

Verification checks include:
- `groups_with_primary_admin_member = 10`
- extra group-admin member coverage per group
- member/status/payout/ledger coverage for `SEED100-G%`

```sql
-- File: supabase/22_SEED_TEST_DATA_10_GROUPS_100_MEMBERS_SAFE.sql
-- Verify: supabase/23_VERIFY_TEST_DATA_10_GROUPS_100_MEMBERS_SAFE.sql
```

**Use Case:** Realistic QA testing, demo data

**Duration:** 5–10 minutes

---

### Option G: Full E2E Testing Suite

**File:** `supabase/25_SEED_FULL_APP_E2E.sql` (1200+ lines)

Creates:
- Complete scenarios for all app features
- Real-world use cases (loan approval workflows, claim processing, etc.)
- Multiple role logins (member, group admin, platform admin)
- Historical data for reporting

**Verification:** `26_VERIFY_FULL_APP_E2E.sql`

**Documentation:** `README_FULL_APP_E2E.md`

```sql
-- File: supabase/25_SEED_FULL_APP_E2E.sql
-- Verify: supabase/26_VERIFY_FULL_APP_E2E.sql
-- README: supabase/README_FULL_APP_E2E.md
```

**Use Case:** Complete app walkthrough, feature testing

**Duration:** 10–15 minutes

---

### Option H: Debug Logic Scenarios

**File:** `supabase/17_SEED_DEBUG_LOGIC_SCENARIOS.sql` (600+ lines)

Creates edge cases for debugging:
- Negative balances
- Overdue payments
- Incomplete loans
- Failed transactions
- Conflicting member states

**Verification:** `18_VERIFY_DEBUG_LOGIC_SCENARIOS.sql`  
**Cleanup:** `19_CLEANUP_DEBUG_LOGIC_SCENARIOS.sql`

**Documentation:** `README_DEBUG_LOGIC_SCENARIOS.md`

```sql
-- File: supabase/17_SEED_DEBUG_LOGIC_SCENARIOS.sql
-- Run: supabase/18_VERIFY_DEBUG_LOGIC_SCENARIOS.sql
-- Cleanup: supabase/19_CLEANUP_DEBUG_LOGIC_SCENARIOS.sql
```

**Use Case:** Bug reproduction, error handling testing

**Duration:** 5 minutes

---

## Seed Execution Flowchart

```
┌─────────────────────────────────────┐
│ Run Phase 1 (01, 02, 03, 29)        │
│ • Schema creation                   │
│ • Security & RLS                    │
│ • Platform admin setup              │
└──────────────┬──────────────────────┘
               │
        ┌──────▼──────┐
        │ Choose Seed │
        └──────┬──────┘
               │
     ┌─────────┼─────────┐
     │         │         │
     ▼         ▼         ▼
  Empty      Fresh     Scaled
  (Prod)     Start     Test
   (05)      (22)      (10)
     │         │        │
     └─────────┴────────┘
          │
          ▼
   Database Ready ✓
```

---

## Local Development Setup

### Supabase Local CLI Migration Auto-Apply

Use this sequence to automatically apply every SQL migration in `supabase/migrations/`.

1. Ensure Docker Desktop is running (Linux engine enabled).
2. Run a local reset to apply migrations in timestamp order.
3. Optionally apply a chosen seed script.

```powershell
cd "C:\Users\CRISS\AndroidStudioProjects\SanibonaniSave_Full"
supabase --version
supabase start
supabase db reset --local --no-seed --yes
```

Optional seed apply after migrations:

```powershell
cd "C:\Users\CRISS\AndroidStudioProjects\SanibonaniSave_Full"
supabase db query --local --file .\supabase\22_SEED_TEST_DATA_10_GROUPS_100_MEMBERS_SAFE.sql
```

Quick verification:

```powershell
cd "C:\Users\CRISS\AndroidStudioProjects\SanibonaniSave_Full"
supabase migration list
```

### Prerequisites

- Docker Desktop (with Linux engine)
- Supabase CLI: `https://github.com/supabase/cli`
- PowerShell or Bash terminal

### Set Up Local Instance

```powershell
# Navigate to project
cd "C:\Users\CRISS\AndroidStudioProjects\SanibonaniSave_Full"

# Start Supabase locally (Docker required)
supabase start

# This applies all migrations from supabase/migrations/ automatically
```

**Expected Output:**
```
API URL: http://localhost:54321
DB URL: postgresql://postgres:postgres@localhost:54322/postgres
Anon Key: eyJ...
Service Role Key: eyJ...
```

### Reset Local Database

```powershell
# Option 1: Reset with migrations only (no seed)
supabase db reset --local --no-seed --yes

# Option 2: Reset and apply a seed file
supabase db reset --local --yes  # Uses seed.sql if exists
```

### Apply Seed Data Manually

If `db reset` doesn't run your seed:

```powershell
# Connect to local DB and run seed
supabase db query --local --file .\supabase\22_SEED_TEST_DATA_10_GROUPS_100_MEMBERS_SAFE.sql
```

---

## Troubleshooting & Rollback

### Issue 1: "Foreign Key Violation" on 02_SECURITY_AND_RLS.sql

**Cause:** 01_DATABASE_SCHEMA.sql did not complete successfully

**Fix:**
```sql
-- Drop schema and retry from Step 1
DROP SCHEMA public CASCADE;
-- Re-run 01_DATABASE_SCHEMA.sql
```

---

### Issue 2: Platform Admin User Already Exists

**Cause:** 03_PLATFORM_ADMIN_SETUP.sql was run twice

**Fix:**
```sql
-- Check existing admin
SELECT id, email FROM auth.users WHERE email = 'torrymsimango@gmail.com';

-- If it exists, skip 03_PLATFORM_ADMIN_SETUP.sql on next run
-- Or update the password:
UPDATE auth.users 
SET encrypted_password = crypt('newpassword', gen_salt('bf'))
WHERE email = 'torrymsimango@gmail.com';
```

---

### Issue 3: RLS Policy Blocks Admin Access

**Cause:** 02_SECURITY_AND_RLS.sql policies are too restrictive

**Fix:** Run `24_PLATFORM_ADMIN_RLS_HOTFIX.sql` or `30_CONSOLIDATED_RLS_ALIGNMENT.sql`

```sql
-- File: supabase/24_PLATFORM_ADMIN_RLS_HOTFIX.sql
```

---

### Issue 4: "Invalid account_number format"

**Cause:** Constraint from 01_DATABASE_SCHEMA.sql or 29_ALIGN_VALIDATION_CONSTRAINTS_WITH_APP.sql rejected input

**Example:**
```sql
-- Account numbers must be 7–13 digits
INSERT INTO groups (account_number) VALUES ('123'); -- ❌ Too short
INSERT INTO groups (account_number) VALUES ('1234567'); -- ✓ Valid
INSERT INTO groups (account_number) VALUES ('1234567890123'); -- ✓ Valid (13 digits)
INSERT INTO groups (account_number) VALUES ('12345678901234'); -- ❌ Too long (14 digits)
```

---

### Quick Rollback to Empty Schema

```sql
-- Full reset to empty schema
DROP SCHEMA public CASCADE;
CREATE SCHEMA public;

-- Re-run 01_DATABASE_SCHEMA.sql
```

---

## Recommended Sequence for Production

### Fresh Supabase Project

1. **Phase 1 Core (5 min)**
   ```
   01 → 02 → 03 → 29
   ```

2. **Optional: Load Test Data (5 min)**
   ```
   22_SEED_TEST_DATA_10_GROUPS_100_MEMBERS_SAFE.sql
   ```

3. **Go Live** — Database is production-ready ✓

### Existing Project Upgrade

1. **Check current schema version**
   ```sql
   SELECT obj_description(oid, 'pg_class') FROM pg_class WHERE relname = 'groups' LIMIT 1;
   ```

2. **Run only missing files from Phase 1 & 2**
   - Skip files already applied
   - Run 29–31 for alignment

3. **Backup before running**
   ```sql
   -- Supabase → Project Settings → Backups
   ```

---

## SQL Reference Quick Links

| Purpose | File |
|---------|------|
| Fresh Install (Empty DB) | `01, 02, 03, 29` |
| Add Minimal Seed Data | `05_SEED_FRESH_START.sql` |
| 10 Groups × 100 Members (safe) | `22_SEED_TEST_DATA_10_GROUPS_100_MEMBERS_SAFE.sql` |
| Full E2E Test Suite | `25_SEED_FULL_APP_E2E.sql` |
| Enable Voting + Multi-Admin | `32_GROUP_VOTING_AND_MULTI_ADMIN.sql` |
| Troubleshoot Admin Access | `24_PLATFORM_ADMIN_RLS_HOTFIX.sql` |
| Cleanup Test Data | `19, 21, 15_CLEANUP*.sql` |

---

## Key Schema Constraints

| Entity | Constraint | Details |
|--------|-----------|---------|
| Account Number | `7–13` digits | SA PASA standard, regex: `^[0-9]{7,13}$` |
| Branch Code | Exactly `6` digits | Regex: `^[0-9]{6}$` |
| Payment Due Day | `1–28` | Avoids invalid dates on short months |
| Phone Number | South African format | `0[1-9][0-9]{8}` (10 digits starting 0) |
| Email | RFC 5322 | Regex pattern validated |
| Full Name | Min 3 characters | Max 100 characters |
| Group Types | 8 allowed | stokvel, burial_society, rosca, investment_club, emergency_fund, community_savings, tontine, other |
| Member Status | 3 states | probation, active, suspended |
| Fee Status | 5 states | paid, due, warning, suspended, pending_activation |

---

## Environment-Specific Notes

### Development (Local Supabase)
- Use migrations auto-apply with `supabase start`
- Run seed via `supabase db query --local --file <seed.sql>`
- Reset often: `supabase db reset --local --yes`

### Staging (Staging Supabase)
- Apply migrations manually in order
- Use Option F (22) or G (25) seed data
- Enable audit logging for testing
- Verify admin access before handing off

### Production (Production Supabase)
- Apply migrations during scheduled maintenance window
- Backup before each migration
- Use seed data **only once** at go-live
- Run verification queries after each step
- Monitor audit logs for anomalies

---

## Support & Further Reading

- **Migration Docs:** `supabase/migrations/README.md`
- **Full App E2E:** `supabase/README_FULL_APP_E2E.md`
- **Test Data 10×100:** `supabase/README_TEST_DATA_10x100_SAFE.md`
- **Debug Scenarios:** `supabase/README_DEBUG_LOGIC_SCENARIOS.md`

---

**Database Schema Version:** 4.0 (May 14, 2026)  
**App Minimum Version:** 1.0.0  
**Compatible OS:** Android 8.0+ (API 26+)

