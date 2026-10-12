# Implementation Plan - Refactor, Streamline & Polish

I will perform a final polish pass on the application to standardize UI state observation, refine financial formatting, and harden the ViewModel lifecycle management.

## User Review Required

> [!IMPORTANT]
> I will be migrating all remaining `collectAsState()` calls in the UI layer to `collectAsStateWithLifecycle()`. This is a recommended Android best practice that prevents the app from consuming resources while the UI is in the background.

## Proposed Changes

### Core Models & Utils

#### [MODIFY] [Money.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/domain/src/main/java/com/sanibonani/save/domain/model/Money.kt)
- Refine `formatShort()` to correctly handle negative values (e.g., "-R5.0K" instead of "R-5.0K").
- Add a `formatWithSign()` helper for clear credit/debit representation in ledgers.

### app module - UI

#### [MODIFY] [MainActivity.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/app/src/main/java/com/sanibonani/save/MainActivity.kt)
- Standardize splash and sync state observation using `collectAsStateWithLifecycle`.

#### [MODIFY] [DevSettingsOverlay.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/app/src/main/java/com/sanibonani/save/ui/components/DevSettingsOverlay.kt)
- Migrate to `collectAsStateWithLifecycle`.

#### [MODIFY] [GlobalLoadingOverlay.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/app/src/main/java/com/sanibonani/save/ui/components/GlobalLoadingOverlay.kt)
- Migrate to `collectAsStateWithLifecycle`.

#### [MODIFY] [Screen Components]
- Perform a sweep of `CreatePlatformAdminScreen.kt`, `PaymentSandboxScreen.kt`, `PasswordRecoveryScreen.kt`, `GroupVotingScreen.kt`, and `KycCaptureScreen.kt` to migrate to lifecycle-aware collection.

### app module - ViewModels

#### [MODIFY] [PlatformAdminViewModel.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/app/src/main/java/com/sanibonani/save/viewmodel/PlatformAdminViewModel.kt)
- Ensure all repository listeners are correctly scoped and cleaned up.
- Standardize error handling to use `toUserMessage()` globally.

### test infrastructure

#### [MODIFY] [TestAppModule.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/app/src/androidTest/java/com/sanibonani/save/di/TestAppModule.kt)
- Replace `android.util.Log` and `println` with `AppLogger` for standardized test diagnostics.

## Verification Plan

### Automated Tests
- Run all instrumented and unit tests to ensure no regressions in state observation.
  ```powershell
  ./gradlew test
  ./gradlew connectedDebugAndroidTest
  ```

### Manual Verification
- **Backgrounding**: Open the app, go to a dashboard, then put the app in the background. Verify in the Profiler that resource consumption drops as expected due to lifecycle-aware collection.
- **Financial UI**: Check shortfalls and repayments to ensure negative currency values are formatted correctly.
- **Dev Tools**: Verify the Dev Settings overlay still functions correctly after the collection refactor.

Please approve this plan to finalize the app's quality hardening.
