# Walkthrough - Database Connections and Switching

I have implemented a dynamic database connection system that allows the app to switch between **LOCAL** and **ONLINE** environments without requiring an app restart. I also fixed several bugs related to reactive data observation when switching between different savings groups.

## Key Changes

### 1. Dynamic Connection Management
- Created [SupabaseConnectionManager.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/data/src/main/java/com/sanibonani/save/data/remote/SupabaseConnectionManager.kt) in the `:data` module.
- This manager maintains the active `SupabaseClient` and `SanibonaniDatabase` (Room) instances.
- It automatically rebuilds connections when `DevSettings` change.

### 2. Environment-Specific Databases
- The app now uses separate database files:
    - `sanibonani_local.db` for the local environment.
    - `sanibonani_online.db` for the online environment.
- This ensures data isolation and prevents sync conflicts when switching back and forth.

### 3. Repository Refactoring
- All core repositories (e.g., `GroupRepository`, `MemberRepository`, `PaymentRepository`) were refactored to use `Provider<T>` for their dependencies.
- This allows repositories (which are singletons) to always fetch the latest active client or DAO from the manager.

### 4. Reactive UI and Logic
- **Realtime Sync**: [RealtimeSyncManager.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/data/src/main/java/com/sanibonani/save/data/sync/RealtimeSyncManager.kt) now observes the connection status and restarts all subscriptions when the Supabase client changes.
- **Group Switching**: Fixed an observation bug in [MemberOverviewViewModel.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/app/src/main/java/com/sanibonani/save/viewmodel/MemberOverviewViewModel.kt) where switching between groups was not triggering a data refresh.
- **UI Feedback**: Removed the "Restart Required" banner on the [LandingScreen.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/app/src/main/java/com/sanibonani/save/ui/screens/landing/LandingScreen.kt) and replaced it with a non-intrusive "Reconnecting..." indicator.

## Verification Results

### Automated Tests
- Ran `DatabaseConnectionTest` which verified that:
    - `Room` database remains injectable and readable.
    - `SupabaseClient` remains injectable and reachable.
- **Status**: ✅ PASS (2/2 tests)

### Compilation and Build
- Performed a full debug assemble build to ensure no DI or build-config issues remained after moving files between modules.
- **Status**: ✅ SUCCESS

### Manual Verification Path
1. Change database environment in **Dev Settings**.
2. App UI shows "Reconnecting..." and then refreshes.
3. Data correctly switches between local and online silos.
4. Group switching in Member Portal is now immediate and reactive.
