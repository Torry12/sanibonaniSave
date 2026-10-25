# 🏦 SanibonaniSave — Business Process Reference

This document outlines the core business processes and financial workflows enforced by the SanibonaniSave platform. It serves as a guide for both administrative users and developers.

---

## 1. Member Onboarding & Registration

The platform supports both self-registration and administrator-led member addition.

1.  **Group Discovery**: Users browse the "Discover Groups" map to find a local group.
2.  **KYC Submission**: Users provide a valid 13-digit SA ID number and residential address (verified via Geoapify).
3.  **Joining Fee**: Registration is only finalized once the joining fee is recorded.
4.  **Probation Period**: New members enter a default 3-month probation period where they can contribute but cannot request loans.

---

## 2. Prioritized Payment Allocation (The "Waterfall" Model)

To ensure platform sustainability and group liquidity, all incoming member payments follow a strict prioritized allocation order in the `ProcessPaymentUseCase`:

| Priority | Destination | Purpose |
| :--- | :--- | :--- |
| **1. Platform Fee** | Platform Ledger | R10 per member/month to cover infrastructure costs. |
| **2. Loan Installment**| Group Ledger | Settles any active debt/interest before accumulating savings. |
| **3. Contribution** | Group Balance | The remainder is added to the member's "Total Paid" savings pool. |

**Rule**: A member cannot add to their savings if they have an outstanding, due loan installment.

---

## 3. Lending Lifecycle & Risk Management

Lending is restricted based on group type and member standing.

1.  **Eligibility**:
    - Member must be `ACTIVE` (past probation).
    - Consistency score must be > 70%.
    - No existing active loans.
2.  **Application**: Member requests an amount (capped by `group.loan_max_amount`).
3.  **Approval**: Group Admin reviews and approves the request.
4.  **Disbursement**: Platform Admin performs the final disbursement, which atomically deducts from the `group_balance` and updates the `group_ledger`.

---

## 4. Automated Loan Settlement (Forced Extraction)

To protect the group from long-term defaults, the system enforces a 3-month maximum loan term.

1.  **Term Monitoring**: A background process identifies loans exceeding 90 days.
2.  **Forced Extraction**: The system executes the `record_loan_settlement_extraction_v1` RPC.
3.  **Savings Offset**: The outstanding balance is deducted directly from the member's historical savings (`total_paid`).
4.  **Audit Trail**: A `loan_settlement_extraction` entry is recorded in the ledger to maintain transparency.

---

## 5. Group-Type Specialized Logic

The platform orchestrates unique behaviors for different cooperative models:

### 🛡️ Burial Society
- **Focus**: Funeral insurance for members and beneficiaries.
- **Rules**: Enforces waiting periods for claims (6 months for natural death).
- **Escalation**: High-value claims are escalated to the Platform Admin for verification.

### 💰 Stokvel
- **Focus**: Periodic lump-sum payouts.
- **Rules**: Calculates `total_expected` based on contribution streaks.
- **Payouts**: Requires "Group Approved" status before appearing in the Platform Admin's disbursement queue.

### 🔄 ROSCA (Rotating Savings)
- **Focus**: Interest-free lending via rotation.
- **Rules**: Assigns a `rosca_position` to members.
- **Automation**: The `ExecuteRoscaPayoutUseCase` automatically generates the "Pot" for the member assigned to the current month.

---

## 6. Financial Integrity & Immutable Ledger

**The "Golden Rule"**: No balance is ever updated without a corresponding ledger entry.

- **Atomicity**: All financial operations are performed via `SECURITY DEFINER` RPCs to ensure that the balance change and the audit log entry succeed or fail as a single unit.
- **Traceability**: Every transaction ID in the ledger can be cross-referenced with a physical receipt or bank statement reference.
- **Repair Flow**: The `repair_group_data_v1` function is available to sync the physical table balance with the sum of all ledger entries in case of data drift.
