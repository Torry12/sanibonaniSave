# Implementation Plan - Refactor, Debug, and Clean Financial Core

Refactor the financial core of SanibonaniSave to ensure RLS-compatible precision, standardize repository patterns, and harden high-value disbursement security.

## User Review Required

> [!IMPORTANT]
> - **Financial Precision**: All `Double` types in financial paths (balances, contributions, fees) will be replaced with `Money` (BigDecimal). This may affect existing test data if they rely on floating-point comparison.
> - **RPC Return Types**: Supabase RPC calls like `increment_group_balance` will now be decoded as `Money` (via String) to prevent precision loss.
> - **Navigation Parameters**: Amounts passed in Navigation will be serialized as plain strings (`BigDecimal.toPlainString()`).

## Proposed Changes

### [Component] Domain Core

#### [MODIFY] [Money.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/domain/src/main/java/com/sanibonani/save/domain/model/Money.kt)
- Add `toPlainString()` helper.
- Ensure `hashCode` and `equals` handle trailing zeros in `BigDecimal` correctly.

#### [MODIFY] [PlatformRepository.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/domain/src/main/java/com/sanibonani/save/domain/repository/treasury/PlatformRepository.kt)
- Update `updateGlobalFees` signature to use `Money` instead of `Double`.

### [Component] Data Layer (Repositories)

#### [MODIFY] [GroupFinancialRepositoryImpl.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/data/src/main/java/com/sanibonani/save/data/repository/treasury/GroupFinancialRepositoryImpl.kt)
- Fix `incrementGroupBalance` and `recordDisbursement` to decode RPC results as `Money`.
- Ensure all calls use the authoritative pattern to avoid "Cent-Drift".

#### [MODIFY] [ContributionRepositoryImpl.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/data/src/main/java/com/sanibonani/save/data/repository/equity/ContributionRepositoryImpl.kt)
- Refactor `recordContribution` and `recordPayment` to use `safeApiCall`.
- Fix `stableUuid` generation to use `amount.amount.toPlainString()`.

#### [MODIFY] [LedgerRepositoryImpl.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/data/src/main/java/com/sanibonani/save/data/repository/treasury/LedgerRepositoryImpl.kt)
- Update `logPlatformEvent` to use `toPlainString()` for the amount.
- Ensure `consolidateGroupLedger` handles complex JSON response correctly.

#### [MODIFY] [PlatformRepositoryImpl.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/data/src/main/java/com/sanibonani/save/data/repository/treasury/PlatformRepositoryImpl.kt)
- Update `updateGlobalFees` to accept `Money` and serialize correctly for Supabase.

### [Component] UI & ViewModels

#### [MODIFY] [PlatformAdminViewModel.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/app/src/main/java/com/sanibonani/save/viewmodel/dynamics/PlatformAdminViewModel.kt)
- Refactor fee input logic to avoid `Double` intermediate states.
- Update `saveGlobalFees` to pass `Money` to the repository.

#### [MODIFY] [NavGraph.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/app/src/main/java/com/sanibonani/save/ui/navigation/NavGraph.kt)
- Update `onNavigatePayment` and `onNavigateToPayment` lambda calls to pass `amount.amount.toPlainString()`.

## Verification Plan

### Automated Tests
- Run `ComplexFinancialScenariosTest` to ensure balance integrity.
- Run `PaymentAndPayoutRepositoryIntegrationTest` for contribution flow.
- Execute `BusinessProcessSimulationTest` to verify end-to-end flows.

### Manual Verification
- Deploy to emulator and test Platform Admin fee updates.
- Verify large disbursement (≥ R10,000) triggers MFA challenge in UI.
- Check Logcat for "Starting network sync" and "Cache sync completed" to verify `BaseRepository` logic.
