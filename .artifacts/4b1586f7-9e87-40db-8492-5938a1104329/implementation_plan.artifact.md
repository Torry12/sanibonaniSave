# Implementation Plan - Offline-First Audit Logs

Update the `AuditLogRepository` and its implementation to follow the project's offline-first architecture using Room cache and the `observeAndSync` pattern.

## User Review Required

> [!IMPORTANT]
> This change introduces a dependency on the local Room database for audit logs, which ensures that admins can view logs even when offline.

> [!NOTE]
> `PlatformRepository` currently duplicates some audit log functionality. This plan focuses on `AuditLogRepository`, which is the dedicated service for this domain.

## Proposed Changes

### Domain Layer

#### [MODIFY] [AuditLogRepository.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/domain/src/main/java/com/sanibonani/save/domain/repository/AuditLogRepository.kt)
- Add `observeLogsForGroup(groupId: String): Flow<Result<List<AuditLog>>>`
- Add `observeLogsForMember(memberId: String): Flow<Result<List<AuditLog>>>`
- Add `observeAllLogs(limit: Int = 100): Flow<Result<List<AuditLog>>>`

### Data Layer

#### [MODIFY] [AuditLogRepositoryImpl.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/data/src/main/java/com/sanibonani/save/data/repository/AuditLogRepositoryImpl.kt)
- Extend `BaseRepository`.
- Implement `observeAndSync` for the new `Flow` methods.
- Update `log*` methods to perform immediate local cache updates (upsert) in addition to Supabase inserts.
- Add `AuditLog` ↔ `AuditLogEntity` mapping logic.

#### [MODIFY] [AuditLogEntity.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/data/src/main/java/com/sanibonani/save/data/local/AuditLogEntity.kt)
- Add extension functions `toModel()` and `toEntity()` to handle mapping, including JSON serialization for the `details` field.

### Application Layer

#### [MODIFY] [PlatformAdminViewModel.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/app/src/main/java/com/sanibonani/save/viewmodel/PlatformAdminViewModel.kt)
- Consider switching from one-shot `getAuditLogs` to `observeAllLogs` for a more reactive dashboard.

## Verification Plan

### Automated Tests
- Run `AuditLogRepositoryTest` (to be created or updated if exists).
- Verify mapping logic between `AuditLog` and `AuditLogEntity`.

### Manual Verification
- Open Platform Admin portal.
- Verify audit logs load correctly.
- Perform an action (e.g., approve a loan) and verify the log appears immediately (local update) and eventually syncs to Supabase.
- Test offline behavior by disabling network and viewing previously loaded logs.
