# Walkthrough - Fixing `activateGroup` Unresolved Reference

I have fixed the build error caused by the removal of the `activateGroup` method from `GroupRepository`. This method was replaced by the `ActivateGroupUseCase`, which orchestrates group activation more comprehensively.

## Changes

### 1. Updated Integration Tests
I updated several integration tests to inject and use `ActivateGroupUseCase` instead of calling `groupRepository.activateGroup()`:
- [ComplexFinancialScenariosTest.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/app/src/androidTest/java/com/sanibonani/save/ComplexFinancialScenariosTest.kt)
- [GroupOnboardingIntegrationTest.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/app/src/androidTest/java/com/sanibonani/save/integration/GroupOnboardingIntegrationTest.kt)
- [MemberOnboardingIntegrationTest.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/app/src/androidTest/java/com/sanibonani/save/integration/MemberOnboardingIntegrationTest.kt)

### 2. Cleaned up `GroupRepositoryIntegrationTest.kt`
Removed outdated tests that were specifically targeting the now-removed `activateGroup` method in [GroupRepositoryIntegrationTest.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/app/src/androidTest/java/com/sanibonani/save/integration/GroupRepositoryIntegrationTest.kt).

### 3. Fixed Unit Tests
Updated unit tests for `GroupRegistrationViewModel` to correctly mock and verify calls to `ActivateGroupUseCase`:
- [GroupViewModelTest.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/app/src/test/java/com/sanibonani/save/GroupViewModelTest.kt)
- [RegistrationLifecycleTest.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/app/src/test/java/com/sanibonani/save/RegistrationLifecycleTest.kt)

### 4. Fixed `TestRepoModule.kt`
Fixed a compilation error in [TestRepoModule.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/app/src/androidTest/java/com/sanibonani/save/di/TestRepoModule.kt) where the mock `SupabaseRepository` was missing new MFA-related methods required by the updated interface.

## Verification Results

### Automated Tests
I successfully ran the following command to verify that the project compiles:
```powershell
./gradlew :app:compileDebugAndroidTestKotlin
```
The build finished successfully.

> [!IMPORTANT]
> All calls to `activateGroup` should now use `ActivateGroupUseCase` instead of `GroupRepository`.
