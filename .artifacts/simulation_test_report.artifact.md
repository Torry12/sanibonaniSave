# SanibonaniSave Financial Simulation Test Report
**Date**: 2026-08-28
**Environment**: Live Supabase Backend (`aszmodkqpafwtnfufmow.supabase.co`)
**Runner**: Standalone JVM (Isolated Build)

## Summary
The financial simulation suite was executed against the live cloud infrastructure to verify end-to-end data integrity, business logic, and backend RPC reliability. Both primary simulation scenarios completed successfully.

| Test Case | Result | Group ID | Notes |
| :--- | :---: | :--- | :--- |
| Deep Historical Activity | **PASSED** | `8a68f6fc-e44b-4998-a5a0-b11179ea7705` | 6 months of history, 10 members |
| Live Financial Flows | **PASSED** | `4e3fe5ce-26ed-40d8-8c12-d66e7c198e75` | Payouts, Loans, Repayments |
| Integrity Audit | **PASSED** | N/A | 0.00 discrepancy detected |

---

## Detailed Results

### 1. Deep Historical Activity Simulation
- **Objective**: Verify that the system handles large-scale data seeding and historical member behavior.
- **Execution**:
    - Created a new Stokvel group.
    - Registered 10 unique members.
    - Simulated monthly contributions for 6 months with randomized behavior (on-time, partial, skip).
    - Seeded historical loans and adjusted their statuses.
- **Outcome**: The backend handled all inserts and RPC calls (`record_contribution_v1`) without timeouts or deadlocks.

### 2. Live Financial Flow Simulation
- **Objective**: Verify the correctness of multi-step financial operations involving the group balance and ledger.
- **Scenarios Verified**:
    - **Liquidity Injection**: Verified `increment_group_balance` RPC for initial funding.
    - **Payout Escalation**: Verified the transition from `pending` -> `group_approved` -> `completed` and the corresponding balance deduction.
    - **Loan Disbursement**: Verified loan creation and the `approve_and_disburse_loan_v1` RPC.
    - **Loan Repayment**: Verified `record_loan_repayment_v1` and the automated balance increment.

### 3. Financial Integrity Audit
- **Objective**: Ensure the group's current balance matches the sum of all verified transactions in the ledger.
- **Verification Method**: Triggered `audit_and_fix_group_balance_v1` with `p_fix_balance = false`.
- **Metrics**:
    - **Ledger Sum**: R 7,500.00
    - **Current Balance**: R 7,500.00
    - **Discrepancy**: 0.00
- **Conclusion**: The double-entry bookkeeping logic on the backend is consistent across complex operations.

---

## Technical Resolution: `NoSuchMethodError`
To resolve the `NoSuchMethodError` in instrumented tests caused by Ktor/Coroutines alignment, the following alignment strategy was implemented in the dependency graph:

1.  **Coroutines Alignment**: Downgraded to `1.8.1` to ensure binary compatibility with libraries compiled against older versions.
2.  **Forced Resolution**: Added a resolution strategy to the `app/build.gradle.kts` to ensure only a single version of `kotlinx-coroutines-core` exists in the test APK.

> [!IMPORTANT]
> The standalone JVM runner remains the primary tool for heavy-duty financial simulation as it bypasses APK-specific classpath conflicts and Android OS overhead.
