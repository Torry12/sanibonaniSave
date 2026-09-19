# Implementation Plan - Fix Admin Financial & Payout Functions

This plan addresses several functional and UI issues in the Admin Financial dashboard, specifically focusing on the Payout request flow and the "Fix Now" consistency repair mechanism.

## Proposed Changes

### Domain Layer (Business Logic)

#### [MODIFY] [RequestPayoutUseCase.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/domain/src/main/java/com/sanibonani/save/domain/usecase/RequestPayoutUseCase.kt)
- **Amount Precedence**: Change logic to prioritize `requestedAmount` if provided by the user. Currently, it overwrites manual input with calculated projections for Stokvel/Investment groups.
- **Fallback Logic**: Only invoke `CalculateStokvelPayoutsUseCase` or `CalculateInvestmentClubValuationUseCase` if `requestedAmount` is null.
- **Validation**: Improve error messaging when a blank amount is submitted for group types that do not support automated projections (e.g., Emergency Fund).

---

### App Layer (ViewModel & Orchestration)

#### [MODIFY] [AdminFinancialViewModel.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/app/src/main/java/com/sanibonani/save/viewmodel/AdminFinancialViewModel.kt)
- **Success Feedback**: Update `submitPayoutRequest` to set a `successMessage` on success. This triggers the snackbar in the UI.
- **Consolidation Sync**: Update `consolidateLedger` to call `adminContextCacheService.warmUpGroup(groupId)` on success to ensure the balance and member counts in the UI are updated immediately after the repair.

#### [MODIFY] [AdminOverviewViewModel.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/app/src/main/java/com/sanibonani/save/viewmodel/AdminOverviewViewModel.kt)
- **Consistency**: Standardize `consolidateLedger` and `executeRoscaPayout` success messages and refresh patterns to match `AdminFinancialViewModel`.

---

### UI Layer (Presentation)

#### [MODIFY] [PayoutTab.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/app/src/main/java/com/sanibonani/save/ui/screens/admin/tabs/PayoutTab.kt)
- **Dynamic Placeholder**: Update the placeholder for the amount `SanibonaniTextField` to be contextual (e.g., "Calculated automatically" for Stokvel vs. "Enter amount" for others).

## Verification Plan

### Automated Tests
- Run `RequestPayoutUseCaseTest.kt` to verify that manual amounts are not overwritten.
- Run `AdminFinancialViewModelTest.kt` to check for `successMessage` emissions.

### Manual Verification
1.  **Integrity Repair**: Trigger "FIX NOW" on the consistency warning and verify the balance refreshes and a success snackbar appears.
2.  **Manual Payout**: Submit an individual payout for a Stokvel member with a manual amount (e.g., R100). Verify it doesn't default to the year-end projection.
3.  **Automated Payout**: Submit a payout for a Stokvel member with no amount and verify it calculates the projected value.
4.  **Error Handling**: Verify that submitting a blank amount for an Emergency Fund group shows a clear validation error.
