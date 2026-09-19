# Implementation Plan - Optimize Dashboard Loading Speed

I will optimize the dashboard loading sequence by parallelizing data hydration and ensuring that cached data is displayed immediately to reduce perceived latency.

## User Review Required

> [!IMPORTANT]
> I will be refactoring the "Context Cache Services" to perform network fetches in parallel. This will significantly reduce the time it takes for a group to become "fully hydrated" and dismiss the loading spinner.

> [!TIP]
> I will also update the Platform Admin's group discovery logic to show local groups first while syncing in the background, preventing a full-screen block when the network is slow or the group list is large.

## Proposed Changes

### app module

#### [MODIFY] [AdminGroupContextCacheService.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/app/src/main/java/com/sanibonani/save/service/AdminGroupContextCacheService.kt)
- Refactor `warmGroupContext` to use `coroutineScope` and `async`.
- Fetch `members`, `notifications`, `payouts`, and `metrics` in parallel.
- Wrap network-heavy calls (like `syncGroupMembers`) in `withTimeoutOrNull(8.seconds)` to prevent indefinite hangs.

#### [MODIFY] [MemberGroupContextCacheService.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/app/src/main/java/com/sanibonani/save/service/MemberGroupContextCacheService.kt)
- Refactor `warmGroupContext` to use parallel execution for `contributions`, `beneficiaries`, `loans`, and `notifications`.
- Implement consistent timeout logic.

### domain module

#### [MODIFY] [GetManagedGroupsUseCase.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/domain/src/main/java/com/sanibonani/save/domain/usecase/GetManagedGroupsUseCase.kt)
- Update `observeManagedGroups` for `isPlatformAdmin`.
- Change the flow to emit local groups first (`fromNetwork = false`) followed by a network sync (`fromNetwork = true`).

## Verification Plan

### Automated Tests
- Run `AdminOverviewViewModelTest` to verify that `managedGroups` are populated twice (local then network).

### Manual Verification
- **Dashboard Load**: Log in and monitor the "Loading dashboard..." spinner. It should appear for a significantly shorter duration than before.
- **Offline Reliability**: Turn on Airplane mode after logging in and restart the app. The dashboard should still load using cached data.

Please approve this plan to proceed with the performance optimizations.
