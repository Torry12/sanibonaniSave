# Implementation Plan - Final App Refactor & Cleanup

Complete the feature-based modularization by fixing all stale imports, consolidating logic, and hardening the project structure.

## User Review Required

> [!IMPORTANT]
> - **Global Import Sweep**: This will affect almost every file in the project. I will use specialized tools to ensure 100% accuracy.
> - **DI & Database Alignment**: I will verify that `SanibonaniDatabase` and `RepoModule` are perfectly synchronized with the new package structure to prevent runtime Hilt errors.

## Proposed Changes

### [Component] Infrastructure (Common)

#### [MODIFY] [BaseViewModel.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/app/src/main/java/com/sanibonani/save/features/common/presentation/BaseViewModel.kt)
- Ensure it properly imports `UIEvent` and other common models.

### [Component] Global Refactor (Imports)

#### [MODIFY] All Kotlin Files
- Sweep and update all repository imports based on the new 6-pillar structure: `admin`, `auth`, `financial`, `group`, `member`, `system`.
- Sweep and update all `BaseViewModel` and `UIEvent` imports.
- Sweep and update all UseCase imports.

### [Component] Data Layer (Persistence)

#### [MODIFY] [SanibonaniDatabase.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/data/src/main/java/com/sanibonani/save/data/local/SanibonaniDatabase.kt)
- Ensure all DAOs and Entities are correctly imported and registered.
- Verify version is at **75**.

#### [MODIFY] [RepoModule.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/app/src/main/java/com/sanibonani/save/di/RepoModule.kt)
- Update all bindings to match the new package paths.

### [Component] Navigation

#### [MODIFY] [NavGraph.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/app/src/main/java/com/sanibonani/save/ui/navigation/NavGraph.kt)
- Fix any remaining type inference issues with `hiltViewModel`.
- Ensure it uses the new feature-based Screen definitions.

## Verification Plan

### Automated Tests
- ✅ **:app:compileDebugKotlin**: The ultimate proof of success.
- ✅ **:data:compileDebugKotlin**: Verify persistence layer integrity.
- ✅ **:domain:compileDebugKotlin**: Verify domain model consistency.

### Manual Verification
- Launch the app and verify the Splash -> Login -> Dashboard flow.
- Verify that "Support Mode" still functions correctly in the new structure.
