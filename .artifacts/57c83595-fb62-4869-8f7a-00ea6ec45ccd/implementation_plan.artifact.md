# Implementation Plan - Fix App Crash at Launch

The app is crashing at launch due to an `IllegalStateException` thrown when `SupabaseClient` or `SanibonaniDatabase` is accessed before they are initialized. This happens because `SupabaseConnectionManager` performs its initial connection build in a background coroutine, while some startup components (like `SplashViewModel` or certain Singletons) require these dependencies immediately during Hilt injection or initialization.

## Proposed Changes

### [SupabaseConnectionManager.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/data/src/main/java/com/sanibonani/save/data/remote/SupabaseConnectionManager.kt)

- **Synchronous Initialization**: Update the `init` block to call `performRebuildSync()` instead of launching a background coroutine for the initial connection. This ensures that the `SupabaseClient` and `SanibonaniDatabase` are available as soon as Hilt provides the `SupabaseConnectionManager`.
- **Maintain Performance**: Since `createSupabaseClient` and `Room.databaseBuilder(...).build()` are lightweight configuration-only operations that do not perform immediate I/O, this change will not cause ANRs during startup.

### [BankSimulationGatewayImpl.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/data/src/main/java/com/sanibonani/save/data/remote/gateway/BankSimulationGatewayImpl.kt)

- **Inject Provider**: Change the direct `SupabaseClient` dependency to a `javax.inject.Provider<SupabaseClient>`. This ensures the gateway always uses the currently active client (respecting environment swaps between local/online) and avoids holding a stale reference.
- **Lazy Access**: Update all methods to use `supabaseProvider.get()` instead of the direct `supabase` property.

## Verification Plan

### Automated Tests
- Run `:app:assembleDebug` to ensure compilation.
- (If possible) Run a simple instrumentation test that launches the app to verify no crash occurs at startup.

### Manual Verification
- Deploy the app to a device/emulator.
- Observe the splash screen: it should transition from "Connecting..." to the login screen or dashboard without crashing.
- Verify that toggling between "Online" and "Local" in Dev Settings still works (this relies on the `Provider` change in `BankSimulationGatewayImpl`).
