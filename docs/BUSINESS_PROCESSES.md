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

1.  **Eligibility** (`ValidateLoanEligibilityUseCase`):
    - Member must be `ACTIVE` (past probation).
    - No loan in `PENDING`, `APPROVED`, `ACTIVE` or `PARTIALLY_PAID`.
    - Zero contribution shortfall and no overdue flag from `PaymentCalculator`.
    - A consistency score above 70% is **not** part of the check — nothing in the loan path reads `payment_consistency_score`.
2.  **Application**: Member requests an amount capped at 30% of their trailing-six-month contributions minus their active loan total, and by `group.loan_max_amount` when that per-loan ceiling is set, over a term of at most 3 months. `RequestLoanUseCase` runs the same eligibility check before pricing, so an over-cap or over-term request is refused at submission.
3.  **Approval**: Group Admin reviews and approves the request.
4.  **Disbursement**: Platform Admin performs the final disbursement through `approve_and_disburse_loan_v2`, which checks the loan state, asserts AAL2 above R10,000 (`[SEC_001]`), fails closed on liquidity (`[FIN_001]`), then moves `groups.balance` and appends `group_ledger` in one transaction. The RPC does not verify the caller's identity — see the open authorization gap in `supabase/SQL_DOCUMENTATION.md`.

---

## 4. Automated Loan Settlement (Forced Extraction)

To protect the group from long-term defaults, the system enforces a 3-month maximum loan term.

1.  **Term Monitoring**: `ProcessLoanForcedSettlementUseCase` sweeps every group and selects `ACTIVE` or `PARTIALLY_PAID` loans whose `startDate` is at least three whole months ago. `LoanSettlementWorker` wraps it for a daily run, enqueued by `BackgroundWorkScheduler` for a platform-admin session — the sweep reads every group through `PlatformRepository.getAllGroups`, which RLS confines to that role, so an ordinary group admin does not get it.
2.  **Forced Extraction**: the use case calls `LoanRepository.settleExpiredLoan(loanId)`, which posts the `settle_expired_loan_v2(UUID)` RPC. The RPC refuses a caller who is neither a platform admin nor that group's admin (`[SEC_002]`), a loan that is not in a settleable state (`[FIN_002]`), a term that has not expired (`[FIN_002] LOAN_TERM_NOT_EXPIRED`), and a remaining balance that is not a clean two-decimal positive number (`[FIN_003]`).
3.  **Savings Offset**: the remaining balance (`total_to_repay − total_repaid`) is written as a negative `loan_settlement_extraction` contribution, `groups.balance` is debited through `increment_group_balance` (so the ledger records it), and the repayment is booked via `record_loan_repayment_v1`. `fn_sync_single_member_financials` then recomputes `members.total_paid`, which is how the member's recorded savings absorb the extraction.
4.  **Idempotency**: if a previous attempt wrote the extraction but not the repayment — or the pair does not match on member, group and amount — the RPC raises `[SYS_002] INCOMPLETE_OR_MISMATCHED_LOAN_SETTLEMENT` instead of guessing; when both legs already match, it completes the repayment without double-posting.
5.  **Audit Trail**: a `LOAN_FORCED_SETTLEMENT` entry is written to `audit_logs`, and the member receives a `LOAN_DEFAULTED` notification with the settled amount.
6.  **Burial Society Follow-up**: for a burial society, the use case re-scores the member and returns them to `PROBATION` when the shortfall exceeds two months of contributions.

> `record_loan_settlement_extraction_v1` is retired and revoked from every role; it accepted a caller-supplied member, group and amount without validating them as one atomic settlement. `settle_expired_loan_v2` is the only entry point.

> **Background automation is scheduled by one class.** `app/.../worker/BackgroundWorkScheduler` (`suspend fun refresh()`) is called by `AuthViewModel` the moment the session hydrates to `Success`, which covers both a cold start with a stored session and a fresh sign-in. It reads `currentUserId`/`isLoggedIn` and the resolved role, then enqueues: `ProbationCompletionWorker` for any signed-in session; `AutomatedGovernanceWorker` and `LoanSettlementWorker` only for `UserRole.PLATFORM_ADMIN`; and for every group the caller administers (`GetManagedGroupsUseCase(userId, adminOnly = true)`) a `LedgerConsistencyWorker` and a `FeeEnforcementWorker`, plus `AutomatedRoscaWorker` when `group.type == ROSCA`. Every enqueue is `enqueueUniquePeriodicWork` with `ExistingPeriodicWorkPolicy.KEEP` — a refresh runs on each session and `UPDATE` would restart the period, so a daily job on an app opened daily would drift until it never fired. The four per-group workers take `WorkManager` in `schedule(...)` and build a group-scoped unique name (`"${WORK_NAME}_$groupId"`); previously `FeeEnforcementWorker` and `LedgerConsistencyWorker` enqueued under the shared `WORK_NAME`, so an admin of two groups only ever enforced and audited the last one written into that slot.
>
> **Accepted limitation**: signing out does not cancel what another identity scheduled. WorkManager cannot recover a queued job's input from `WorkInfo`, so cancelling the previous user's per-group work would need its own persisted name registry. The stale jobs cost one refused network call per group per day — the `SEC_002` guards and RLS decide entitlement, not the schedule — and the next sign-in adds its own names. The mirror case is a group created during a live session: it is picked up at the next hydration, which costs nothing, because a group with no ledger rows, no members and no fee period has nothing for the per-group workers to act on — and the platform fee is enforced server-side by `automate_monthly_billing_v1` regardless.

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
