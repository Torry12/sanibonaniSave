# Database Synchronization Strategy (App to Backend)

Implement a robust, multi-phase synchronization engine to ensure strict parity between the local Room SQLite cache and the remote Supabase PostgreSQL backend.

## User Review Required

> [!IMPORTANT]
> This change introduces a comprehensive background synchronization logic. Initial sync at app startup or manually triggered from Dev Settings will now fetch and persist ALL relevant data for the user's groups.

> [!WARNING]
> High-volume data (Ledger entries, Contributions) will be synced per-group. Ensure network connectivity is stable for a full sync.

## Proposed Changes

Standardize repository interfaces to include explicit `sync*` methods that can be orchestrated by a central `SyncRepository`.

---

### [Domain Layer] :domain

#### [MODIFY] [GroupRepository.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/domain/src/main/java/com/sanibonani/save/domain/repository/equity/GroupRepository.kt)
- Add `suspend fun syncAllManagedGroups(userId: String): Result<Unit>`
- Add `suspend fun syncPublicGroups(): Result<Unit>`

#### [MODIFY] [MemberRepository.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/domain/src/main/java/com/sanibonani/save/domain/repository/equity/MemberRepository.kt)
- Add `suspend fun syncMemberships(userId: String): Result<Unit>`
- Change `syncGroupMembers` return type to `Result<Unit>`.

#### [MODIFY] [ContributionRepository.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/domain/src/main/java/com/sanibonani/save/domain/repository/equity/ContributionRepository.kt)
- Add `suspend fun syncGroupContributions(groupId: String): Result<Unit>`
- Add `suspend fun syncGroupPayments(groupId: String): Result<Unit>`

#### [MODIFY] [LoanRepository.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/domain/src/main/java/com/sanibonani/save/domain/repository/credit/LoanRepository.kt)
- Rename `getGroupLoansSync` to `syncGroupLoans(groupId: String): Result<Unit>`.

#### [MODIFY] [PayoutRepository.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/domain/src/main/java/com/sanibonani/save/domain/repository/disbursement/PayoutRepository.kt)
- Add `suspend fun syncGroupPayouts(groupId: String): Result<Unit>`

#### [MODIFY] [LedgerRepository.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/domain/src/main/java/com/sanibonani/save/domain/repository/treasury/LedgerRepository.kt)
- Add `suspend fun syncGroupLedger(groupId: String): Result<Unit>`

---

### [Data Layer] :data

#### [MODIFY] [GroupRepositoryImpl.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/data/src/main/java/com/sanibonani/save/data/repository/equity/GroupRepositoryImpl.kt)
- Implement `syncAllManagedGroups` and `syncPublicGroups`.

#### [MODIFY] [MemberRepositoryImpl.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/data/src/main/java/com/sanibonani/save/data/repository/equity/MemberRepositoryImpl.kt)
- Implement `syncMemberships` and update `syncGroupMembers`.

#### [MODIFY] [ContributionRepositoryImpl.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/data/src/main/java/com/sanibonani/save/data/repository/equity/ContributionRepositoryImpl.kt)
- Implement `syncGroupContributions` and `syncGroupPayments`.

#### [MODIFY] [LoanRepositoryImpl.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/data/src/main/java/com/sanibonani/save/data/repository/credit/LoanRepositoryImpl.kt)
- Update `syncGroupLoans`.

#### [MODIFY] [PayoutRepositoryImpl.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/data/src/main/java/com/sanibonani/save/data/repository/disbursement/PayoutRepositoryImpl.kt)
- Implement `syncGroupPayouts`.

#### [MODIFY] [LedgerRepositoryImpl.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/data/src/main/java/com/sanibonani/save/data/repository/treasury/LedgerRepositoryImpl.kt)
- Implement `syncGroupLedger`.

#### [MODIFY] [SyncRepositoryImpl.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/data/src/main/java/com/sanibonani/save/data/repository/system/SyncRepositoryImpl.kt)
- Implement `syncAllData()` to orchestrate the multi-phase sync.
- Implement `syncGroupData(groupId)` for deep group refreshes.

---

### [App Layer] :app

#### [MODIFY] [SplashViewModel](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/app/src/main/java/com/sanibonani/save/MainActivity.kt)
- Ensure it triggers `syncAllData()` after connection confirmation.

## Verification Plan

### Automated Tests
- Create `SyncRepositoryImplTest` and verify that calling `syncAllData` results in a fully populated Room database matching Supabase.

### Manual Verification
1. Open the app and wait for the splash screen to complete.
2. Check if groups and member data are visible offline (using Airplane mode).
3. Trigger a manual sync from **Dev Settings > Sync All** and verify `SyncStatus` transitions (SYNCING -> SUCCESS).
