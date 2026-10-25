# SanibonaniSave Refined Loan Rules & Processes

This document outlines the strict financial and operational rules for the SanibonaniSave loan system, upgraded for professional-grade risk management and cooperative sustainability.

## 1. Eligibility Requirements
- **Status**: Only members with `ACTIVE` status may apply for a loan.
- **Tenure**: A member must have an established membership history within the specific group to be eligible.
- **Good Standing**: Members must have no current outstanding contribution shortfalls or existing active loans.

## 2. Loan Constraints
- **Maximum Amount**: A loan may not exceed **30% of the total value of contributions** made by the member over the preceding **6 months**.
- **Maximum Term**: Loans can only be paid over a period of **3 months or less**.

## 3. Automatic Settlement (Forced Extraction)
- **3-Month Trigger**: If any balance remains on a loan after the 3-month term, the system will automatically extract the remaining value from the member's **share of contributions**.
- **Financial Reconciliation**:
    - Deduct balance from `member.total_paid`.
    - Update `loan.status` to `COMPLETED`.
    - Create a `LedgerEntry` recording the internal settlement.
    - Recalculate any new outstanding contribution amounts for the member if the deduction creates a deficit.

## 4. Group-Type Specific Penalties & Logic

### Burial Societies
- **Probation Reset**: If a member's outstanding amounts (including contribution shortfalls created by loan settlement) exceed **2 months of contributions** and this state persists for **more than 30 days**:
    - The member is placed back on a **new probation period**.
    - The length of this new probation is equal to the group's **original probation period** (e.g., 3 months).
    - During this time, burial benefits are suspended as per group rules.

### ROSCA (Rotating Savings)
- **Payout Offset**: When it is a member's turn to receive the rotation pot:
    - The system checks for any active loan balance.
    - The loan balance is **subtracted first** to settle the debt.
    - Only the remainder of the pot is disbursed to the member.

## 5. Architectural Implementation
- **Validation**: Enforced in `ValidateLoanEligibilityUseCase`.
- **Scheduled Extraction**: Handled by a periodic background worker (`LoanSettlementWorker`).
- **Rotation Logic**: Integrated into `CalculateRoscaRotationUseCase`.
- **Audit Trail**: Every extraction or penalty application must generate an `AuditLog` entry.
