# Business Calculation and Flow Audit Report

This report summarizes the audit of the core business logic, financial calculations, and update flows across the SanibonaniSave platform.

## Summary of Findings

| Flow Area | Accuracy & Integrity | Status |
| :--- | :--- | :--- |
| **Financial Precision** | Consistent use of `BigDecimal` for ledger-critical math. | ✅ **EXCELLENT** |
| **Atomic Updates** | All balance/contribution writes use Supabase RPCs (server-side atomicity). | ✅ **VERIFIED** |
| **Actuarial Engine** | Multi-type logic (Burial, Stokvel, ROSCA, etc.) follows industry standards. | ✅ **VERIFIED** |
| **Member Scoring** | Weighted scoring (40/30/20/10) correctly prioritizes payment performance. | ✅ **VERIFIED** |
| **Audit Integrity** | SQL level correctly maintains `group_ledger`. Kotlin events need hardening. | ⚠️ **MINOR IMPROV** |

## Detailed Audit

### 1. Actuarial and Financial Logic (`GroupTypeActuarialEngine`)
- **Burial Society**: Logic correctly accounts for mortality rates (StatsSA proxy) and safety loading (35%). Solvency ratio calculation uses a robust NPW-based model.
- **Investment Club**: Portfolio performance tracks CAGR and Sharpe Ratio. Market premium (6.5%) and volatility haircuts are applied for forward projections.
- **ROSCA**: Implements the Besley-Coate-Loury welfare gain model. Payout rotation correctly handles 1-based cycle months and wrap-around.
- **Stokvel**: Payout projections correctly account for remaining months in the 12-month cycle.

### 2. Payment Flow (`ProcessPaymentUseCase`)
- **Fee Handling**: Correctly separates the `monthly_member_fee` (platform income) from the `contribution` (group fund).
- **Status Determination**: Member status is updated to `PAID` or `PARTIAL` based on the net contribution amount, protecting group integrity.
- **Context Awareness**: Enforces presence of full `Group` and `Member` objects during confirmation to prevent orphaned records.

### 3. Database Atomicity and Ledger
- **Server-Side Integrity**: Functions like `record_contribution_v1` perform multi-table writes (contribution record, member stats, group balance, ledger entry) within a single transaction.
- **Virtual Bank**: Simulates real-world money movement between virtual accounts for all participants.

## Identified Improvements (Hardening)

1.  **Kotlin Ledger Events**: While the database ledger is updated by RPCs, the app's internal `DomainEventDispatcher` is not notified in all cases.
    *   **Action**: Emit `LedgerEntryCreatedEvent` from `incrementGroupBalance` (GroupRepo) and `recordContribution` (MemberRepo).
2.  **Calculation Failure Visibility**: Some pure calculation functions (e.g., in `ActuarialRepositoryImpl`) use `getOrElse` with defaults.
    *   **Action**: Standardize on `Result<T>` for all financial projections to ensure UI can show "Calculation Error" instead of stale/default data.

## Conclusion
The business calculation flows are technically sound, accurate, and follow the principles of the Immutable Financial Ledger. The system is resilient to data conflicts due to the use of server-side atomic operations.
