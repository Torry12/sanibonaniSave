# Plan: Code Cleanup and Financial Precision Hardening

This plan focuses on removing redundant code (legacy repository methods), cleaning up unused dependencies, and ensuring consistent high-precision financial data handling across the system.

## Proposed Changes

### 1. Repository & Data Layer

#### [MODIFY] [GroupRepository.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/domain/src/main/java/com/sanibonani/save/domain/repository/group/GroupRepository.kt)
- Remove `activateGroup` from the interface.

#### [MODIFY] [GroupRepositoryImpl.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/data/src/main/java/com/sanibonani/save/data/repository/group/GroupRepositoryImpl.kt)
- Remove `activateGroup` implementation.

#### [MODIFY] [MemberRepositoryImpl.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/data/src/main/java/com/sanibonani/save/data/repository/member/MemberRepositoryImpl.kt)
- Replace `.toDouble()` with `.amount` (BigDecimal) in all RPC parameters to ensure financial precision.

#### [MODIFY] [LoanRepositoryImpl.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/data/src/main/java/com/sanibonani/save/data/repository/financial/LoanRepositoryImpl.kt)
- Replace `.toDouble()` with `.amount` in RPC parameters.

#### [MODIFY] [PayoutRepositoryImpl.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/data/src/main/java/com/sanibonani/save/data/repository/financial/PayoutRepositoryImpl.kt)
- Replace `.toDouble()` with `.amount` in RPC parameters.

### 2. Use Case Layer

#### [MODIFY] [ProcessContributionUseCase.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/domain/src/main/java/com/sanibonani/save/domain/usecase/financial/ProcessContributionUseCase.kt)
- Replace `.toDouble()` with `.amount` in `allocations` JSON builder.

#### [MODIFY] [ProcessDisbursementUseCase.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/domain/src/main/java/com/sanibonani/save/domain/usecase/financial/ProcessDisbursementUseCase.kt)
- Remove unused `notificationRepository` injection. Side effects are now event-driven.

#### [MODIFY] [ProcessBurialClaimUseCase.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/domain/src/main/java/com/sanibonani/save/domain/usecase/member/ProcessBurialClaimUseCase.kt)
- Remove unused `notificationRepository` injection.

### 3. ViewModel & Application Layer

#### [MODIFY] [GroupRegistrationViewModel.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/app/src/main/java/com/sanibonani/save/features/group/presentation/GroupRegistrationViewModel.kt)
- Inject `ActivateGroupUseCase`.
- Replace `groupRepo.activateGroup` with `activateGroupUseCase`.

#### [MODIFY] [LiveSimulationRunner.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/app/src/androidTest/java/com/sanibonani/save/LiveSimulationRunner.kt)
- Inject `ActivateGroupUseCase`.
- Replace `groupRepo.activateGroup` with `activateGroupUseCase`.

### 4. Utility Layer

#### [MODIFY] [PaymentCalculator.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/domain/src/main/java/com/sanibonani/save/domain/utils/PaymentCalculator.kt)
- Remove unused `member` parameter from internal helper methods where applicable.

## Verification Plan

### Automated Tests
- Run unit tests for event handlers and Use Cases to ensure no regressions.
- Verify that `ActivateGroupUseCase` is correctly called from all sites.
- Check for precision issues in financial RPCs via logs.

### Manual Verification
- Perform a group registration and activation flow in the app.
- Perform a payment and verify the allocations are correctly ledgered in the backend.
