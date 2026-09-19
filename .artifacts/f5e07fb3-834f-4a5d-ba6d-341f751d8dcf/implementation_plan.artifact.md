# Implementation Plan - Fix Connection Bugs

This plan addresses several connection-related issues identified in the codebase, ranging from overly aggressive timeouts to resource leaks and premature connectivity signals.

## User Review Required

> [!IMPORTANT]
> The `LandingViewModel` timeout is being increased from 12s to 30s to accommodate repository-level retries. This may make the initial loading spinner stay longer on very poor connections before showing an error.

## Proposed Changes

### 1. Network Connectivity Service hardening
Refine how internet availability is detected to avoid premature "online" signals.

#### [MODIFY] [NetworkConnectivityServiceImpl.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/app/src/main/java/com/sanibonani/save/service/NetworkConnectivityServiceImpl.kt)
- Update `onAvailable` in `NetworkCallback` to check for `NET_CAPABILITY_INTERNET` before emitting `true`.
- Ensure `currentStatus` correctly reflects the internet capability.

### 2. Supabase Connection Management
Prevent resource leaks and ensure clean transitions when switching between local and online environments.

#### [MODIFY] [SupabaseConnectionManager.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/data/src/main/java/com/sanibonani/save/data/remote/SupabaseConnectionManager.kt)
- Close the existing `SupabaseClient` in `rebuildConnections()` before creating a new one.

### 3. Repository Resilience
Improve retry logic and API call safety.

#### [MODIFY] [BaseRepository.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/data/src/main/java/com/sanibonani/save/data/repository/BaseRepository.kt)
- Refine `retryWithExponentialBackoff` to only retry on specific network-related exceptions (e.g., `IOException`, `SocketTimeoutException`, `HttpRequestTimeoutException`).
- Update `safeApiCall` to distinguish between recoverable network errors and permanent errors (like 4xx) before queuing to the offline outbox.

### 4. Landing Page Logic
Improve the initial data fetch experience.

#### [MODIFY] [LandingViewModel.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/app/src/main/java/com/sanibonani/save/features/landing/LandingViewModel.kt)
- Increase `refreshData` timeout from 12s to 30s.
- Add a check for `isSystemOnline` in `refreshData()` to return early (with a clear offline message) instead of waiting for a network timeout.
- Use `SupervisorJob` for the refresh job to prevent failures in one async block from cancelling others prematurely.

## Verification Plan

### Automated Tests
- Run `LandingViewModelTest` if it exists, or create a simple unit test for the refresh logic.
- Run `SupabaseConnectionManagerTest` to verify rebuild logic.

### Manual Verification
- Launch the app and verify the "Connection timeout" no longer appears prematurely on a working connection.
- Toggle Airplane mode and verify the `OfflineBanner` and error messages respond correctly.
- Switch between Local and Online database in Dev Settings and verify the app reconnects successfully.
