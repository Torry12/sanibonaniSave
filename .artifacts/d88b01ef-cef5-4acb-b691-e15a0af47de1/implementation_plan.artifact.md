# Implementation Plan - Resolve Error Duplication and Discrepancy Reason

This plan addresses the duplicated error messages on the Member Dashboard and fixes the underlying mathematical discrepancy in the "Total Paid" verification logic.

## Problem Description
1.  **Message Duplication**: Identical "Data Integrity" warnings are displayed twice. This is caused by redundant logic in both the `DataVerificationService` and the `MemberOverviewViewModel` being rendered independently in the UI.
2.  **False Discrepancy**: The verification logic currently sums **all** transactions, including Late Fees and Member Fees. However, the system's canonical `totalPaid` (Investment Principal) field intentionally excludes fees to ensure accurate equity valuations. A R50 discrepancy usually indicates a paid Late Fee that the verification logic incorrectly included in its sum.

## Proposed Changes

### 1. Data Layer (Verification Logic)
Harden the verification service to align with the platform's "Equity Logic".

#### [MODIFY] [DataVerificationServiceImpl.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/data/src/main/java/com/sanibonani/save/data/service/DataVerificationServiceImpl.kt)
- Update `verifyMemberData` to only sum contributions of type `contribution`, `joining_fee`, and `registration_contribution` when validating against `member.totalPaid`.

### 2. ViewModel Layer (Redundancy Removal)
Eliminate duplicate state tracking to fix the UI duplication.

#### [MODIFY] [MemberOverviewViewModel.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/app/src/main/java/com/sanibonani/save/viewmodel/MemberOverviewViewModel.kt)
- Remove the manual `integrityWarning` calculation in `startLiveObservation`.
- Remove the `dataIntegrityMessage` field from the UI state.
- Rely exclusively on the `VerificationResult` provided by the `verificationService`.

### 3. UI Layer (Consolidation)
Cleanup the dashboard to only show the consolidated verification results.

#### [MODIFY] [MemberDashboardScreen.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/app/src/main/java/com/sanibonani/save/ui/screens/member/MemberDashboardScreen.kt)
- Remove the call to `DataIntegrityAlert(it)` (which was rendering the old `dataIntegrityMessage`).
- Keep `VerificationSummary(overviewState.verificationResult)`, which handles all data integrity issues in a unified banner.

## Verification Plan

### Manual Verification
1.  **Impersonation View**: Navigate to a member who has paid a Late Fee (e.g., Bongani Khumalo in the screenshot).
2.  **Discrepancy Check**: Verify that the "Total Paid" discrepancy warning no longer appears if the only difference is fee-related.
3.  **Duplication Check**: Ensure only one unified warning banner appears if a genuine discrepancy exists (e.g., after manually editing a database value).
4.  **Audit Tab**: Verify that the "Transactions" and "Audit" tabs still correctly show the full list of payments including fees.
