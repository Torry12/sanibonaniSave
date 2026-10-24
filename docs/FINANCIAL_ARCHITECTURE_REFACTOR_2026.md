# Financial Architecture Refactor — August 2026

## Executive Summary
This document records the major architectural transition of the SanibonaniSave financial systems from a flat, high-noise structure to a **Domain-Driven Reorganization**. The refactor introduces the **"Five Pillars of Finance"**, providing clear boundaries, reducing redundancy, and hardening security for high-value operations.

---

## 🏛️ The Five Pillars of Finance

### 1. Treasury (Accounting & Parity)
**Namespace**: `com.sanibonani.save.domain.repository.treasury`
**Focus**: The authoritative group ledger, bank reconciliation, and external financial providers.
- **Key Entities**: `LedgerEntry`, `BankTransaction`, `ReconciliationRecord`.
- **Logic**: Enforces "Golden Ledger" rules where group balances are derived solely from immutable entries.

### 2. Equity (Inflows & Capital)
**Namespace**: `com.sanibonani.save.domain.repository.equity`
**Focus**: Member savings, joining fees, and platform registration.
- **Consolidation**: Merged `PaymentRepository` and `MemberContributionRepository` into the **`ContributionRepository`**.
- **UseCase**: Refactored `ProcessPaymentUseCase` into **`ProcessContributionUseCase`**, unifying gateway logic with equity posting.

### 3. Credit (Lending Lifecycle)
**Namespace**: `com.sanibonani.save.domain.repository.credit`
**Focus**: The full lifecycle of personal loans, interest calculations, and term enforcement.
- **Hardening**: Standardized 3-month term limit enforcement and forced settlement logic.

### 4. Disbursement (Outflows & Security)
**Namespace**: `com.sanibonani.save.domain.repository.disbursement`
**Focus**: Secure capital exits via payouts or insurance claims.
- **Unified Workflow**: Introduced **`ProcessDisbursementUseCase`** to handle both standard payouts and burial claims.
- **Security**: Mandatory **MFA (AAL2)** enforcement for all disbursements ≥ R10,000.00.

### 5. Dynamics (Financial Engines)
**Namespace**: `com.sanibonani.save.domain.repository.dynamics`
**Focus**: Specialized logic for different group types and actuarial health.
- **Engines**: Stokvel projections, ROSCA rotations, and Investment Club NAV calculations.
- **Analytics**: Group Health Score and behavior tracking algorithms.

---

## 🔄 Mapping of Key Changes

### Repositories
| Old Root Repository | New Domain Repository | Notes |
|---------------------|-----------------------|-------|
| `LedgerRepository` | `treasury.LedgerRepository` | Hardened platform ledger flag. |
| `PaymentRepository` | `equity.ContributionRepository` | Merged into Contribution. |
| `MemberContributionRepository` | `equity.ContributionRepository` | Unified model. |
| `LoanRepository` | `credit.LoanRepository` | Moved to Credit. |
| `PayoutRepository` | `disbursement.PayoutRepository` | Moved to Disbursement. |
| `ActuarialRepository` | `dynamics.ActuarialRepository` | Moved to Dynamics. |

### Use Cases
| Old Use Case | New Use Case | Pillar |
|--------------|--------------|--------|
| `ProcessPaymentUseCase` | `ProcessContributionUseCase` | Equity |
| `ProcessPayoutUseCase` | `ProcessDisbursementUseCase` | Disbursement |
| `ActivateGroupUseCase` | `ActivateGroupUseCase` | Treasury |
| `ValidateLoanEligibility`| `ValidateLoanEligibility` | Credit |

---

## 🛠️ Implementation Details

### Dependency Injection
The **`RepoModule.kt`** has been updated to reflect the hierarchical structure. All bindings now use domain-specific sub-packages.
> [!CAUTION]
> Avoid adding financial repositories back to the root `repository/` package. Always choose the appropriate Pillar.

### ViewModel Alignment
ViewModels (e.g., `AdminFinancialViewModel`, `PaymentViewModel`) have been relocated to match their respective domain pillars in the `:app` module, ensuring that UI logic remains coupled to business domains rather than flat lists.

---

## ✅ Verification & Hardening
- **Logical Parity**: 100% logic parity maintained with v13.2 production rules.
- **Clean Imports**: Zero "ghost" imports or redundant root files remain.
- **Security**: AAL2 MFA checks are now centralized in the Disbursement pillar.

**Documented by AI Agent**
*August 8, 2026*
