# 🏦 SanibonaniSave — SQL Database Documentation

**Version**: 1.0 (June 2026)
**Platform**: Supabase (PostgreSQL)

This document provides a comprehensive overview of the SanibonaniSave database schema, including tables, key fields, and core business logic implemented via functions and triggers.

---

## 🏗️ Core Schema (`01_DATABASE_SCHEMA.sql`)

### 1. Identity & Profiles
- **`profiles`**: Extends the `auth.users` table with application-specific metadata.
    - `id`: Maps directly to `auth.users.id`.
    - `role`: `platform_admin`, `group_admin`, or `member`.

### 2. Group Management
- **`groups`**: The central entity representing a savings group (Stokvel, Burial Society, etc.).
    - `type`: Defines the group's operational logic (e.g., `burial_society`, `rosca`).
    - `balance`: Atomic balance tracking (see atomic balance helpers).
    - `joining_fee` & `monthly_contribution`: Standard financial parameters.
    - `rosca_rotation_method`: Strategy for rotation (fixed, random, etc.).

### 3. Membership
- **`members`**: Links users to groups with status tracking.
    - `status`: `active`, `probation`, `suspended`, or `pending_payment`.
    - `joined_at` & `probation_end_at`: Lifecycle tracking.
    - `total_paid`: Running total of all contributions.

### 4. Financials
- **`contributions`**: Records expected and paid periods.
    - `status`: `paid`, `due`, `overdue`, or `partial`.
    - `type`: `contribution`, `joining_fee`, `late_fee`, etc.
- **`payments`**: Raw transaction records from payment gateways (YoCo, Stitch, etc.).
- **`group_ledger`**: Immutable audit trail for every group balance change.
- **`platform_ledger`**: Tracks revenue earned by the platform.

### 5. Credit & Loans
- **`loans`**: Loan lifecycle management.
    - `status`: `pending`, `approved`, `active`, `completed`, etc.
    - `total_to_repay`: Includes interest calculated at disbursement.
- **`loan_repayments`**: Individual payments made against an active loan.

### 6. Burial Society Specifics
- **`beneficiaries`**: Registered dependents for burial society members.
- **`beneficiary_payout_claims`**: Claims submitted upon the death of a beneficiary.

---

## ⚙️ Business Logic (`02_FUNCTIONS_AND_TRIGGERS.sql`)

### 1. Atomic Balance Helpers
To prevent race conditions and ensure data integrity, all balance updates must go through:
- **`increment_group_balance`**: Updates `groups.balance` and automatically inserts a record into `group_ledger`.

### 2. Core RPCs (Remote Procedure Calls)
These functions encapsulate multi-step business operations:
- **`record_contribution_v1`**:
    - Inserts into `contributions`.
    - Updates group balance (via `increment_group_balance`).
    - Handles platform fees if the contribution type is `member_fee`.
- **`record_loan_repayment_v1`**:
    - Inserts into `loan_repayments`.
    - Updates `loans.total_repaid`.
    - Updates group balance.
- **`approve_and_disburse_loan_v1`**:
    - Validates group balance sufficiency.
    - Updates loan status to `active`.
    - Subtracts the loan amount from the group balance.

### 3. Automatic Triggers
- **`update_updated_at_column`**: Ensures every `updated_at` field is accurate.
- **`fn_sync_group_member_count`**: Keeps `groups.current_members` in sync with the actual number of rows in `members`.

---

## 📈 Analytics & Behavior Tracking

- **`group_actuarial_metrics`**: Advanced risk and solvency scores for group health.
- **`member_behavior_track`**: Scores members (0–100) based on payment history and loan defaults.
- **`fraud_detection_events`**: Audit trail for suspicious activities (velocity spikes, duplicate transactions).

---

## 🔐 Security & RLS (`03_SECURITY_AND_RLS.sql`)

The system enforces strict **Row Level Security (RLS)**:
- **Members**: Can only read data for groups they belong to.
- **Group Admins**: Can manage members and financials within their group.
- **Platform Admins**: Full access across all groups and platform ledgers.

---

## 📝 Example SQL Comments

When writing new SQL, follow these commenting standards:

```sql
-- Use double dashes for single-line comments
/* Use block comments
   for complex logic descriptions */

-- 1. HEADER SECTIONS (All caps, numbered)
-- 2. FIELD EXPLANATIONS (Inline or above the field)
CREATE TABLE public.example (
    id UUID PRIMARY KEY, -- Unique identifier
    status TEXT DEFAULT 'pending' -- Initial state for all new records
);
```
