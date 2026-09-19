# Refactor Repositories for OOP Design Principles

This plan refactors the core repository layer to adhere to **Single Responsibility Principle (SRP)** and **Interface Segregation Principle (ISP)**. The current `GroupRepository` and `MemberRepository` have grown too large, handling CRUD, financial logic, and document management in single classes.

## Proposed Changes

### Domain Layer (Interfaces)

Split the large interfaces into specialized ones.

#### [NEW] [MemberContributionRepository](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/domain/src/main/java/com/sanibonani/save/domain/repository/MemberContributionRepository.kt)
- `recordContribution`
- `getMemberContributions`
- `getGroupContributions`

#### [NEW] [GroupFinancialRepository](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/domain/src/main/java/com/sanibonani/save/domain/repository/GroupFinancialRepository.kt)
- `incrementGroupBalance`
- `recordDisbursement`
- `payPlatformFee`
- `getVerifiedBalance`
- `repairGroupData`

#### [NEW] [GroupDocumentRepository](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/domain/src/main/java/com/sanibonani/save/domain/repository/GroupDocumentRepository.kt)
- `uploadConstitution`

#### [MODIFY] [MemberRepository](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/domain/src/main/java/com/sanibonani/save/domain/repository/MemberRepository.kt)
- Remove contribution and document upload methods (moved to `MemberContributionRepository` and `MemberDocumentRepository`).

#### [MODIFY] [GroupRepository](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/domain/src/main/java/com/sanibonani/save/domain/repository/GroupRepository.kt)
- Remove financial and document methods.

---

### Data Layer (Implementations)

Create specialized implementations and refactor the existing ones.

#### [NEW] [MemberContributionRepositoryImpl](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/data/src/main/java/com/sanibonani/save/data/repository/MemberContributionRepositoryImpl.kt)
- Implement `MemberContributionRepository` using logic from `MemberRepositoryImpl`.

#### [NEW] [GroupFinancialRepositoryImpl](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/data/src/main/java/com/sanibonani/save/data/repository/GroupFinancialRepositoryImpl.kt)
- Implement `GroupFinancialRepository` using logic from `GroupRepositoryImpl`.

#### [NEW] [GroupDocumentRepositoryImpl](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/data/src/main/java/com/sanibonani/save/data/repository/GroupDocumentRepositoryImpl.kt)
- Implement `GroupDocumentRepository` using logic from `GroupRepositoryImpl`.

#### [MODIFY] [MemberRepositoryImpl](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/data/src/main/java/com/sanibonani/save/data/repository/MemberRepositoryImpl.kt)
- Clean up moved methods.

#### [MODIFY] [GroupRepositoryImpl](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/data/src/main/java/com/sanibonani/save/data/repository/GroupRepositoryImpl.kt)
- Clean up moved methods.

---

### Application Layer (DI & ViewModels)

#### [MODIFY] [RepoModule](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/app/src/main/java/com/sanibonani/save/di/RepoModule.kt)
- Add bindings for the new repositories.

#### [MODIFY] UseCases & ViewModels
- Update constructors to inject the new specialized repositories where needed.
- Affected files include `CreateGroupUseCase`, `RegisterMemberUseCase`, `MemberProfileViewModel`, `PaymentViewModel`, etc.

## Verification Plan

### Automated Tests
- Run unit tests: `./gradlew :domain:test :data:test`
- Run instrumentation tests: `./gradlew :app:connectedDebugAndroidTest`

### Manual Verification
- Verify group creation flow (including constitution upload).
- Verify member registration and payment flows.
- Verify financial ledger updates.
