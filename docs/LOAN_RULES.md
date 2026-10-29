# SanibonaniSave Refined Loan Rules & Processes

This document outlines the strict financial and operational rules for the SanibonaniSave loan system, upgraded for professional-grade risk management and cooperative sustainability.

## 1. Eligibility Requirements
Enforced in `ValidateLoanEligibilityUseCase`:
- **Status**: `member.status` must be `ACTIVE` (`ValidateLoanEligibilityUseCase.kt:34`); anything else is rejected with "Member not active."
- **Term**: a requested `termMonths > 3` is rejected outright (`:35`) — the 3-month cap is validated, not advisory.
- **Good Standing**: no loan in `PENDING`, `APPROVED`, `ACTIVE` or `PARTIALLY_PAID` (`:50`), and `PaymentCalculator` must report a zero shortfall and no overdue flag (`:75`).
- **Borrowing power**: 30% of the contributions actually paid in the trailing six months (`:55-63`).

## 2. Loan Constraints
- **Maximum Amount**: `maxBorrowingPower − currentActiveLoanTotal`, where `maxBorrowingPower` is 30% of the trailing-six-month contributions (`:63-64`). `group.loan_max_amount`, when positive, is a per-loan ceiling checked before any repository read (`:39-44`); zero/null means "not configured". Both `RequestLoanUseCase` (submission) and `ProcessLoanDecisionUseCase` (approval) run the check, so an over-cap request is refused before it is priced or queued.
- **Maximum Term**: 3 months for an eligibility-checked loan. The column `groups.loan_max_months` (default 12) is a separate group setting and is not what the eligibility check enforces.

## 3. Automatic Settlement (Forced Extraction)
- **3-Month Trigger**: `ProcessLoanForcedSettlementUseCase` selects loans in `ACTIVE` or `PARTIALLY_PAID` whose `startDate` is at least three whole months in the past, and posts each one to `settle_expired_loan_v2(UUID)`.
- **One Guarded RPC**: the database re-checks the state and accepts `active`, `partially_paid`, `overdue` or `defaulted`; any other status raises `[FIN_002] INVALID_STATUS`, and a term that has not expired raises `LOAN_TERM_NOT_EXPIRED`. Non-admin, non-group-admin callers raise `[SEC_002] LOAN_SETTLEMENT_ACCESS_DENIED`.
- **Savings Offset**: the RPC inserts a negative `contributions` row of type `loan_settlement_extraction`, which `tr_sync_member_financials_contrib` feeds into `fn_sync_single_member_financials` — that is what moves `members.total_paid`, and it is why the column is allowed to go negative.
- **Ledger**: the same transaction posts `increment_group_balance(-remaining, 'loan_settlement_extraction', …)` and then `record_loan_repayment_v1`, whose `tr_sync_loan_repayment_total` runs `fn_sync_single_loan_totals`. That helper sets `loans.status = 'completed'` once `total_repaid >= total_to_repay - 0.01`, else `partially_paid`. No caller writes `loans.status` directly.
- **Audit**: `log_audit_event('LOAN_FORCED_SETTLEMENT', 'system', …)` records the extraction.

## 4. Group-Type Specific Penalties & Logic

### Burial Societies
- **Probation Reset**: immediately after a forced settlement, `ProcessLoanForcedSettlementUseCase` re-scores the member and, when the shortfall exceeds **2 months of contributions**, sets `MemberStatus.PROBATION`. It is a status write, not a new probation period — `probation_end_at` is not extended — and there is no 30-day persistence test. If the member's contribution history cannot be read, the sanction is skipped rather than guessed.

### ROSCA (Rotating Savings)
- **Payout Offset**: when a member's turn arrives, `process_rosca_payout_v2` / `_v3` finds the member's outstanding loan, debits the group by that amount under ledger category `rosca_payout_offset`, and repays it through `record_loan_repayment_v1` before the remainder of the pot is disbursed.

## 5. Architectural Implementation
- **Validation**: `ValidateLoanEligibilityUseCase` (client) plus the `[FIN_002]`/`[FIN_003]` guards inside `settle_expired_loan_v2` (server).
- **Scheduled Extraction**: `LoanSettlementWorker` runs the use case on a daily period. `BackgroundWorkScheduler` enqueues it only when the session hydrates to a `UserRole.PLATFORM_ADMIN` account — the sweep walks `PlatformRepository.getAllGroups()`, which RLS confines to that role. There is no other caller: `LoanRepository.settleExpiredLoan(loanId)` is reached only from this use case, so for an ordinary group admin an expired loan is not force-settled on the device.
- **Rotation Logic**: `CalculateRoscaRotationUseCase` with `RoscaRotationUtils`; `groups.rosca_rotation_method` is `fixed`, `random_draw`, `need_based` or `auction`.
- **Audit Trail**: every extraction writes `audit_logs` via `log_audit_event`.
