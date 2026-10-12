# 🏦 SanibonaniSave — SQL Database Documentation

**Version**: 7.1 (August 2026 - Financial Precision & SQL Sync)
**Platform**: Supabase (PostgreSQL) / Room (SQLite)

This document provides a comprehensive overview of the SanibonaniSave database schema, including tables, key fields, and core business logic. The local Room database mirrors this schema for offline-first capabilities.

---

## 🏗️ 1. Identity & Profiles

### `user_profiles`
Extends `auth.users` with application metadata.
- `id` (UUID): Maps to `auth.users.id`.
- `full_name` (TEXT): Member's display name.
- `email` (TEXT): Primary contact.
- `role` (TEXT): `platform_admin`, `group_admin`, or `member`.

---

## 🏛️ 2. Group Management

### `groups`
The central container for all cooperative activities.
- `id` (UUID): Unique group identifier.
- `name` (TEXT): Group name.
- `type` (TEXT): `burial_society`, `stokvel`, `rosca`, `investment_club`, `emergency_fund`, `tontine`.
- `balance` (NUMERIC): Atomic balance tracking (Precision: 2 decimals).
- `joining_fee` (NUMERIC): One-time entry fee.
- `monthly_contribution` (NUMERIC): Recurring obligation.
- `late_fee` (NUMERIC): Penalty for missed deadlines.
- `goal_amount` (NUMERIC): Target savings for the cycle.
- `rosca_rotation_method` (TEXT): `FIXED`, `RANDOM`, `VOTED`.
- `loan_interest_rate` (DOUBLE): Default interest for the group.
- `loan_max_amount` (NUMERIC): Borrowing cap.

### `group_polls`
Governance and voting for group decisions.
- `id` (UUID): Poll identifier.
- `groupId` (UUID): Parent group.
- `type` (TEXT): `rotation_order`, `policy_change`, `disbursement_approval`.
- `status` (TEXT): `DRAFT`, `OPEN`, `CLOSED`, `EXECUTED`.

---

## 👥 3. Membership & Documents

### `members`
Links users to groups with status tracking.
- `id` (UUID): Member identifier.
- `group_id` (UUID): Reference to `groups`.
- `user_id` (UUID): Reference to `user_profiles` (nullable for offline adds).
- `status` (TEXT): `ACTIVE`, `PROBATION`, `SUSPENDED`, `PENDING_PAYMENT`.
- `member_key` (TEXT): Unique short-code for offline identification.
- `total_paid` (NUMERIC): Cumulative contributions.
- `contract_status` (TEXT): Verification state of the legal contract.

### `beneficiaries`
Registered dependents for Burial Societies.
- `member_id` (UUID): Reference to `members`.
- `relationship` (TEXT): e.g., Spouse, Child, Parent.
- `is_over_65` (BOOLEAN): Affects actuarial risk and premiums.

### `member_documents`
KYC and legal compliance files.
- `member_id` (UUID): Reference to `members`.
- `label` (TEXT): e.g., "ID Document", "Proof of Residence".
- `status` (TEXT): `PENDING`, `VERIFIED`, `REJECTED`.

---

## 💰 4. Financials & Ledger

### `contributions`
Records expected and paid obligations.
- `id` (UUID): Contribution ID.
- `status` (TEXT): `PAID`, `DUE`, `OVERDUE`, `PARTIAL`.
- `type` (TEXT): `contribution`, `joining_fee`, `late_fee`, `penalty`.
- `principal_amount` (NUMERIC): The base contribution amount.
- `late_fee_amount` (NUMERIC): Accrued penalties.

### `payouts`
- `amount` (NUMERIC): Disbursement value.
- `status` (TEXT): `PENDING`, `PROCESSING`, `COMPLETED`, `FAILED`.
- `supporting_document_url` (TEXT): Link to proof of payment or claim document.
- `metadata` (JSONB): Extensible field for specific group type data.

### `group_ledger`
**The Source of Truth.** Immutable audit trail for all balance changes.
- `id` (UUID): Ledger entry ID.
- `amount` (NUMERIC): Change in balance (positive or negative).
- `balance_after` (NUMERIC): Snapshot of balance after transaction.
- `category` (TEXT): `contribution`, `loan_disbursement`, `loan_repayment`, `payout`.

### `bank_connections`
Secure linkage to external financial accounts.
- `group_id` (UUID): The group owning the connection.
- `provider_id` (TEXT): Link to `financial_providers`.
- `status` (TEXT): `active`, `expired`, `disconnected`, `pending`.
- `account_number_hidden` (TEXT): Masked account for display.
- `linked_at` (TIMESTAMPTZ): Connection establishment time.

### `settlements`
Tracks the clearing of funds from payment gateways.
- `transaction_id` (TEXT): Link to raw payment.
- `fee_deducted` (NUMERIC): Platform or gateway fee.
- `status` (TEXT): `PENDING`, `SETTLED`, `FAILED`.

---

## 💳 5. Credit & Loans

### `loans`
Lifecycle management for credit features.
- `id` (UUID): Loan identifier.
- `amount` (NUMERIC): Principal borrowed.
- `total_to_repay` (NUMERIC): Principal + calculated interest.
- `total_repaid` (NUMERIC): Amount paid back to date.
- `amortization_type` (TEXT): `flat`, `reducing_balance`, `bullet`.
- `effective_apr` (NUMERIC): Actual cost of borrowing.
- `status` (TEXT): `PENDING`, `APPROVED`, `ACTIVE`, `COMPLETED`, `DEFAULTED`.

### `loan_repayments`
Individual payments made against an active loan.
- `loan_id` (UUID): Reference to `loans`.
- `amount` (NUMERIC): Repayment value.

---

## 📊 6. Analytics & Behavior (ML-Ready)

### `member_behavior_track`
Data points for finetuning Azure AI Foundry models.
- `payment_consistency_score` (DOUBLE): Weighted score of on-time payments.
- `loan_default_count` (INT): Number of defaulted loans.
- `fraud_risk_level` (TEXT): `LOW`, `MEDIUM`, `HIGH`, `CRITICAL`.
- `behavior_score` (DOUBLE): Aggregated score for reliability (0-100).

### `fraud_detection_events`
Audit trail for suspicious activities.
- `event_type` (TEXT): `velocity_spike`, `duplicate_transaction`, `unusual_withdrawal`.
- `severity` (TEXT): `INFO`, `WARNING`, `CRITICAL`.

---

## 🔄 7. System & Sync

### `pending_operations`
Offline outbox for synchronization.
- `type` (TEXT): The RPC or operation to perform (e.g., `record_contribution`).
- `payload` (TEXT): JSON representation of the entity.
- `retry_count` (INT): Number of failed sync attempts.
- `created_at` (BIGINT): Epoch timestamp matching mobile device state.

### `audit_logs`
System-wide activity tracking.
- `actor_id` (UUID): Who performed the action.
- `action` (TEXT): e.g., `GROUP_CREATED`, `MEMBER_SUSPENDED`.

### `platform_fee_config`
Pricing tiers for different group types.
- `group_type` (TEXT): Primary key part.
- `rosca_cycle` (INTEGER): Cycle duration for ROSCA.
- `registration_fee` (NUMERIC): One-time setup fee.
- `monthly_fee` (NUMERIC): Ongoing maintenance fee.
- `shortfall_recovery_fee` (NUMERIC): Fee for processing under-contributions.

---

## ⚙️ Business Logic (Supabase RPCs)

### Atomic Balance Helpers
- **`increment_group_balance`**: Updates `groups.balance` and inserts into `group_ledger`.

### Core RPCs
- **`record_contribution_v2`**: Atomically records payment, updates balance, and logs to ledger.
- **`approve_and_disburse_loan_v1`**: Validates solvency, updates loan status, and subtracts from balance.

---

## 🔐 Security

- **Row Level Security (RLS)**: Enforced on all tables based on `group_id` and `user_id`.
- **Financial Integrity**: `Double` is forbidden for currency. All math is performed using `NUMERIC` in Postgres and `Money` (BigDecimal) in Kotlin.
