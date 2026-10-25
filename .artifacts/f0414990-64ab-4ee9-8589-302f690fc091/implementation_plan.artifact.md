# Implementation Plan - Project Hardening and Import Cleanup

This plan aims to finalize the modularization of the SanibonaniSave project by fixing stale imports, hardening the base infrastructure, and ensuring all modules are correctly synchronized.

## User Review Required

> [!IMPORTANT]
> - **Global Import Sweep**: I will update imports across the project to match the new 6-pillar structure (admin, auth, financial, group, member, system).
> - **Financial Precision**: I will continue replacing `.toDouble()` with `.amount` where appropriate in data layers, as per the previous hardening goal.
> - **Build Verification**: I will run build tasks to ensure no regressions were introduced during the refactor.

## Proposed Changes

### [Component] Infrastructure (app:common)

#### [MODIFY] [UIEvent.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/app/src/main/java/com/sanibonani/save/features/common/presentation/UIEvent.kt)
- Convert `UIEvent` to a `sealed class` for exhaustive handling. (Partially done)

#### [MODIFY] [BaseViewModel.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/app/src/main/java/com/sanibonani/save/features/common/presentation/BaseViewModel.kt)
- Clean up imports and ensure consistent usage of `UIEvent`.

### [Component] Data & Domain Sync

#### [MODIFY] [SanibonaniDatabase.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/data/src/main/java/com/sanibonani/save/data/local/SanibonaniDatabase.kt)
- Verify DAO imports and ensure it is fully aligned with the feature-based structure.

#### [MODIFY] [RepoModule.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/app/src/main/java/com/sanibonani/save/di/RepoModule.kt)
- Clean up bindings and remove redundant fully qualified names.

### [Component] Global Refactor

#### [MODIFY] Multiple Files
- Update repository and UseCase imports across `:app`, `:data`, and `:domain`.

## Verification Plan

### Automated Tests
- Run `./gradlew :domain:test` and `./gradlew :data:compileDebugKotlin`.
- Verify `:app:assembleDebug` succeeds.

### Manual Verification
- Check the app's logcat for any DI-related errors during startup.
