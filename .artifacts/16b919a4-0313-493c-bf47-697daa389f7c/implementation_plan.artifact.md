# Fix Unit Test Failures

The goal is to resolve the unit test failures across `:app`, `:data`, `:domain`, and `:simulation` modules.

## Proposed Changes

### [Domain Model]

#### [MODIFY] [Money.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/domain/src/main/java/com/sanibonani/save/domain/model/Money.kt)
- Update `Money.ZERO` to have scale 2 to ensure consistent equality with other `Money` instances.

### [UI/ViewModel]

#### [MODIFY] [BehaviorTrackingViewModel.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/app/src/main/java/com/sanibonani/save/features/admin/presentation/BehaviorTrackingViewModel.kt)
- Change `first()` to `firstOrNull()` when observing fraud events to prevent `NoSuchElementException` on empty flows.

### [Data/Networking]

#### [MODIFY] [libs.versions.toml](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/gradle/libs.versions.toml)
- Verify and potentially adjust `ktor` and `okhttp` versions if they are causing `NoClassDefFoundError`. *Initial investigation suggests Ktor 3.5.0 might be incorrect; will try 3.0.0.*

### [Simulation/Database]

#### [MODIFY] [LiveSimulationRunner.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/simulation/src/main/java/com/sanibonani/save/simulation/LiveSimulationRunner.kt) (and related)
- Fix the `IllegalStateException` in JVM tests by providing a custom `SessionManager` for Supabase Auth that doesn't depend on Android context.
- Investigate foreign key violation in simulation.

## Verification Plan

### Automated Tests
- Run `:app:testDebugUnitTest`
- Run `:data:testDebugUnitTest`
- Run `:domain:testDebugUnitTest`
- Run `:simulation:testDebugUnitTest`
