# Refactoring, Analysis, and Fine-tuning Plan

This plan outlines the steps to improve the application's architecture, code quality, and performance by consolidating misplaced files, refactoring ViewModels, and ensuring consistent UI patterns.

## User Review Required

- **Module Boundaries**: Moving files between modules (`app`, `domain`, `data`) to strictly follow the 3-module architecture. This will involve updating many imports.
- **Consolidation**: Moving `data` and `domain` packages that are currently nested inside the `app` module or incorrectly placed in the `domain` module.

## Proposed Changes

### 1. Architectural Consolidation (File Moves)

Group files into their correct modules as per `AGENTS.md`.

#### [domain module](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/domain/src/main/java/com/sanibonani/save/domain)

- Move `app/src/main/java/com/sanibonani/save/domain/model/AgentTask.kt` -> `domain/src/main/java/com/sanibonani/save/domain/model/AgentTask.kt`
- Move `app/src/main/java/com/sanibonani/save/domain/model/AgentResult.kt` -> `domain/src/main/java/com/sanibonani/save/domain/model/AgentResult.kt`
- Move `app/src/main/java/com/sanibonani/save/domain/repository/AgentRepository.kt` -> `domain/src/main/java/com/sanibonani/save/domain/repository/AgentRepository.kt`
- Move `app/src/main/java/com/sanibonani/save/domain/event/EventHandlerInitializer.kt` -> `domain/src/main/java/com/sanibonani/save/domain/event/EventHandlerInitializer.kt`
- Move `app/src/main/java/com/sanibonani/save/domain/utils/SessionConfig.kt` -> `domain/src/main/java/com/sanibonani/save/domain/utils/SessionConfig.kt`
- Move `app/src/main/java/com/sanibonani/save/usecase/SignOutUseCase.kt` -> `domain/src/main/java/com/sanibonani/save/domain/usecase/SignOutUseCase.kt`
- **Fix domain internal structure**: Move `domain/src/main/java/com/sanibonani/save/data/utils/SafeResultExtensions.kt` -> `domain/src/main/java/com/sanibonani/save/domain/utils/SafeResultExtensions.kt`
- **Fix domain internal structure**: Move `domain/src/main/java/com/sanibonani/save/data/logging/AppLogger.kt` -> `domain/src/main/java/com/sanibonani/save/domain/logging/AppLogger.kt`

#### [data module](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/data/src/main/java/com/sanibonani/save/data)

- Move `app/src/main/java/com/sanibonani/save/data/repository/AgentRepositoryImpl.kt` -> `data/src/main/java/com/sanibonani/save/data/repository/AgentRepositoryImpl.kt`
- Move `app/src/main/java/com/sanibonani/save/data/local/Migrations.kt` -> `data/src/main/java/com/sanibonani/save/data/local/Migrations.kt`
- Move `app/src/main/java/com/sanibonani/save/data/remote/PolicyApiService.kt` -> `data/src/main/java/com/sanibonani/save/data/remote/PolicyApiService.kt`
- Move `app/src/main/java/com/sanibonani/save/data/utils/DevSettings.kt` -> `data/src/main/java/com/sanibonani/save/data/utils/DevSettings.kt`

---

### 2. ViewModel & Composable Refactoring

#### [PlatformAdminViewModel.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/app/src/main/java/com/sanibonani/save/viewmodel/PlatformAdminViewModel.kt)

- Consolidate `payBurialClaim`, `approveBurialClaim`, and `rejectBurialClaim` into a private `processBurialClaim` helper.
- Pre-compute association maps (ID to name/province) in the `PlatformAdminUiState` to avoid expensive re-computation in Composables.

#### [DisbursementsTab.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/app/src/main/java/com/sanibonani/save/ui/screens/admin/tabs/platform/DisbursementsTab.kt)

- Use pre-computed maps from `PlatformAdminUiState` instead of creating them on-the-fly.

---

### 3. Verification Plan

#### Automated Tests
- Run existing unit tests to ensure no regressions after file moves and refactoring:
  `./gradlew test`
- Run specifically `PlatformAdminViewModelTest` after refactoring:
  `./gradlew test --tests *PlatformAdminViewModelTest`

#### Manual Verification
- Deploy the app and navigate to the Platform Administrator screen.
- Verify all tabs (Analytics, Groups, Payments, Ledger, etc.) load correctly.
- Verify Payout and Loan escalation actions still work as expected.
- Check Logcat for any DI errors or missing imports.
