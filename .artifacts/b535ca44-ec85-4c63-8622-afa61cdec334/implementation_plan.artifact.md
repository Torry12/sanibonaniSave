# Fix Connection and Sync Reliability Bugs

This plan addresses several critical reliability issues in the data connection and synchronization layers, specifically focusing on Supabase client management and Realtime sync stability.

## Proposed Changes

### 1. Networking & Connection Management

#### [MODIFY] [SupabaseConnectionManager.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/data/src/main/java/com/sanibonani/save/data/remote/SupabaseConnectionManager.kt)
- Add a thread-safe lock for `rebuildConnections`.
- Ensure the previous `SupabaseClient` is properly closed before creating a new one to prevent resource leaks.
- Avoid redundant rebuilds if the `useOnline` setting hasn't changed.

#### [MODIFY] [RemoteClientProvider.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/data/src/main/java/com/sanibonani/save/data/remote/RemoteClientProvider.kt)
- Increase emulator connection timeout from 10s to 15s to handle slow local environments.
- (Optional) Explicitly configure a persistent session manager for Auth to ensure session longevity.

### 2. Realtime Synchronization

#### [MODIFY] [RealtimeSyncManager.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/data/src/main/java/com/sanibonani/save/data/sync/RealtimeSyncManager.kt)
- **Dynamic Client Awareness**: Update the constructor to take `SupabaseConnectionManager` instead of a static `SupabaseClient`.
- **Auto-Reconnect on Client Change**: Observe `connManager.currentClient` and automatically trigger `reconnect()` whenever the client instance changes.
- **Fix BatchProcessor Bug**: Update the `enqueue` logic to ensure that if items arrive while a batch is being processed, they are not ignored and will trigger a subsequent batch.
- **Subscription Resilience**: Add a retry mechanism for failed table subscriptions.

### 3. Dependency Injection Safety

#### [MODIFY] [AppModule.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/app/src/main/java/com/sanibonani/save/di/AppModule.kt)
- Update `provideSupabaseClient` and `provideDatabase` to be more resilient, potentially returning a "lazy" or "provider" wrapper if the underlying protocol hasn't finished its first initialization.

## Verification Plan

### Automated Tests
- Run unit tests for `RealtimeSyncManager` if available.
- Verify `BatchProcessor` logic with a dedicated unit test.

### Manual Verification
- **Mode Switching**: Toggle between "Local" and "Online" in DevSettings and verify that Realtime subscriptions restart and data continues to flow.
- **Timeout Test**: Simulate a slow network to local Supabase and ensure the increased timeout prevents premature failures.
- **Kill/Restart**: Verify that the authentication session persists after killing and restarting the app.
