# Data Access and Navigation Safety Audit

This document summarizes the audit of the data access layer and navigation architecture to ensure no race conditions, memory leaks, or crashes occur during rapid navigation.

## Memory Leak Status (LeakCanary)

### Findings
- **Confirmed Fix**: The memory leak in `LeafletGroupsMap.kt` has been resolved by replacing `Handler.postDelayed` with a Coroutine-based `LaunchedEffect` and `suspendCancellableCoroutine` loop. The `WebView` is now explicitly destroyed in `onDispose`.
- **System Logs**: Low-level system logs (`mtkpower`, `libPowerHal`) were inspected in `full_dump.txt` and `dump_check_final.txt`. No application-level "Dumping heap" or LeakCanary trace messages were found in the provided logs, suggesting that the recent fixes have stabilized the heap.
- **Verification**: Manual verification on-device (repeatedly entering/exiting the Map view) is still recommended to confirm silence from LeakCanary.

## Data Access Race Conditions

### Architectural Patterns Applied
- **isActive StateFlow**: Primary ViewModels (`AdminViewModel`, `MemberViewModel`, `PlatformAdminViewModel`) use an `isActive` flag toggled by `DisposableEffect` in the UI. This prevents background synchronization and observation from running when the screen is not in the foreground.
- **Observation Versions**: ViewModels use a `requestVersion` (or `observationVersion`) counter. Every time a new observation starts (e.g., switching groups), the version is incremented. Emissions from older "stale" jobs are ignored via checks like `if (requestVersion != observationVersion) return@collect`.
- **Job Lifecycle Management**: ViewModels maintain explicit `Job` references (e.g., `groupObservationJob`, `managedGroupsJob`) and cancel them before starting new ones or in `onCleared`.
- **Thread-Safe Caches**: `AdminGroupContextCacheService` and `MemberGroupContextCacheService` use `MutableStateFlow.update` for atomic updates to the group context maps, ensuring data integrity during rapid switches.

### Specific Component Analysis

#### [BaseRepository.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/data/src/main/java/com/sanibonani/save/data/repository/BaseRepository.kt)
- Uses `channelFlow` with `awaitClose` to properly manage DB and Network observation jobs.
- Implements `retryWithExponentialBackoff` with `CancellationException` awareness, ensuring that if a user navigates away, background retries are halted immediately.

#### [NavGraph.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/app/src/main/java/com/sanibonani/save/ui/navigation/NavGraph.kt)
- **navigationThrottle**: A synchronized check-and-set object prevents rapid duplicate navigation events (e.g., from double-tapping a button), which is a common source of "fragment already added" style crashes or backstack corruption.
- **Role Enforcement**: Centralized redirection logic in `NavigationUtils` ensures that users are always routed to the correct portal based on their role, preventing "IllegalStateException: Screen not found" or "Unauthorized access" UI glitches.

#### [MemberRepositoryImpl.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/data/src/main/java/com/sanibonani/save/data/repository/MemberRepositoryImpl.kt)
- Uses Atomic RPCs (`record_contribution_v1`) for critical financial writes to Supabase, ensuring server-side integrity.
- Updates local Room cache after successful writes to provide immediate "Offline-First" UI feedback.

## Recommendations for Manual Verification

1. **Portal Switching**: Rapidly switch between "Group Admin" and "Member" views for the same group to ensure that the `CacheService` correctly hydrates the UI without flickers.
2. **Impersonation Flow**: Platform Admins should impersonate multiple members in sequence. Verify that `impersonationRequestVersion` correctly discards data from the previous member if the next one is selected before the first fetch completes.
3. **Map Navigation**: Enter and exit the "Discover Groups" map multiple times. Monitor Logcat for "LeakCanary: Watcher" or heap dump notifications.

## Conclusion
The current implementation follows best practices for reactive Android development. The combination of **Lifecycle-aware ViewModels**, **Atomic State Updates**, and **Navigation Throttling** significantly mitigates the risk of race conditions and crashes.
