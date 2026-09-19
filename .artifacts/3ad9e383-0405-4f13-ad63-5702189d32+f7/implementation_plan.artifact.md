# Implementation Plan - Fix Admin Dashboard Loading Deadlock

I will fix the issue where the Admin Dashboard (including Support Mode) fails to load due to a deadlock in the context observation logic.

## User Review Required

> [!IMPORTANT]
> I am refactoring how the `AdminOverviewViewModel` tracks the active group. Instead of observing its own internal `uiState.group.id` (which is reset to null during loading), it will now directly observe the `selectedGroupId` from the cache service. This ensures that the data fetch is triggered immediately and doesn't get stuck in a "null" loop.

## Proposed Changes

### app module

#### [MODIFY] [AdminOverviewViewModel.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/app/src/main/java/com/sanibonani/save/viewmodel/AdminOverviewViewModel.kt)
- **Fix Observation Deadlock**: Update `observeContext()` to map from `adminContextCacheService.selectedGroupId` instead of `uiState.map { it.group?.id }`.
- **Harden State Sync**: Ensure that `isLoading` is set correctly even if the context is null initially, but transitioning to a non-null state.

#### [MODIFY] [AdminDashboardScreen.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/app/src/main/java/com/sanibonani/save/ui/screens/admin/AdminDashboardScreen.kt)
- Refine the full-screen loading condition to be less aggressive, ensuring that if a `groupId` is present but the object isn't hydrated yet, we still show the appropriate tab-level loading instead of a blocking full-screen spinner if suitable.

## Verification Plan

### Manual Verification
- **Support Mode Entry**: Navigate from the Platform Admin portal to any group. Verify the dashboard loads successfully.
- **Group Switching**: Use the internal group selector in the Admin Dashboard. Verify that switching groups works flawlessly and doesn't get stuck on "Loading dashboard...".
- **Initial Load**: Log in as a Group Admin and verify the dashboard appears immediately.
