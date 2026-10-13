# Implementation Plan - Navigation Standardization and Memory Leak Mitigation

This plan addresses memory leak risks identified by LeakCanary and standardizes the navigation architecture to follow Jetpack Compose best practices.

## User Review Required

> [!IMPORTANT]
> The "Leaks" notification confirms an active memory leak. The most significant risk is in `LeafletGroupsMap.kt` due to `Handler` usage. Navigation improvements focus on robustness and decoupling.

- **Technical Trade-off:** Replacing `Handler.postDelayed` with `kotlinx.coroutines.delay` ensures that background retry logic is automatically cancelled when the Composable is disposed.
- **Navigation Update:** Refactoring navigation to use standard route constants and state hoisting.

## Proposed Changes

### UI Components

#### [LeafletGroupsMap.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/app/src/main/java/com/sanibonani/save/ui/components/maps/LeafletGroupsMap.kt)

- Replace `Handler.postDelayed` with a Coroutine-based retry loop inside `LaunchedEffect`.
- This prevents the `WebView` from being leaked if the user navigates away during the retry attempts.

### Navigation Standardizations

#### [NavGraph.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/app/src/main/java/com/sanibonani/save/ui/navigation/NavGraph.kt)

- **Centralized Redirection:** Extract complex redirection logic into an `AuthRedirectHandler` class to reduce boilerplate in the `SanibonaniNavGraph` Composable.
- **Robust Argument Parsing:** Use `navArgument` defaults and non-nullability more strictly to prevent crashes in sub-screens.
- **State Hoisting:** Ensure screens receive lambdas for navigation rather than the `NavController` (already partially followed, but will audit all screens).

### Administrative Module

#### [AdminViewModel.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/app/src/main/java/com/sanibonani/save/viewmodel/AdminViewModel.kt)

- Hardened `onCleared` logic (defensive Measure).

### Data Access Audit

#### [data_access_audit.artifact.md](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/.artifacts/20260604-120521-a31b513f-36e0-4663-bc0d-be5f196e19d3/data_access_audit.artifact.md)

- Comprehensive review of ViewModel lifecycles, Repository sync patterns, and Cache Services.
- Verified that `observationVersion` and `isActive` patterns are correctly implemented to prevent stale data emissions and race conditions.

## Verification Plan

### Automated Tests
- `gradlew.bat test --tests *MultiGroupTest`
- `gradlew.bat test --tests *NavGraph*`

### Manual Verification
- Verify "Browse Groups" map markers still load correctly.
- Repeatedly navigate between Login -> Dashboard -> Landing to ensure no backstack growth or leaks.
- Verify in Logcat that no "Dumping heap" messages are triggered.
