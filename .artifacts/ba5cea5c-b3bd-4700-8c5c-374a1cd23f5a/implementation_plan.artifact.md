# Database Refactoring & Update Plan (Final Phase)

Optimize the DAO synchronization patterns and cleanup legacy backend functions to finalize the robust hydration engine.

## User Review Required

> [!IMPORTANT]
> This phase will remove legacy Supabase RPC wrappers (`record_contribution_v1`, `process_complex_payment_v3`, `recalculate_all_group_financials_v2`). Ensure no external scripts or older app versions are still targeting these endpoints.

> [!NOTE]
> `LedgerDao.syncLedger` will be refactored from a "Full Clear" to an "Orphan Pruning" pattern to align with all other DAOs.

## Proposed Changes

### [Room Database] :data

#### [MODIFY] [LedgerEntities.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/data/src/main/java/com/sanibonani/save/data/local/LedgerEntities.kt)
- Update `LedgerDao.syncLedger` to calculate orphans (existing IDs not in incoming list) and delete them surgically instead of clearing the whole table for a group.
- Add `getGroupLedgerIds(groupId)` helper for orphan calculation.

#### [MODIFY] [UserEntities.kt](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/data/src/main/java/com/sanibonani/save/data/local/UserEntities.kt)
- Add `syncProfile(userId, profile)` to `UserProfileDao` for consistency, even if it currently just performs an upsert.

#### [MODIFY] [MemberDao](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/data/src/main/java/com/sanibonani/save/data/local/MemberEntities.kt)
- Verify `syncMembers` and `syncMembershipsForUser` for any missing `@Transaction` or edge cases. (Checked: mostly solid, will add `suspend` where missing if any).

---

### [Supabase Backend] /supabase

#### [MODIFY] [02_FUNCTIONS_AND_TRIGGERS.sql](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/supabase/02_FUNCTIONS_AND_TRIGGERS.sql)
- **DELETE** `record_contribution_v1` (v2 is standard).
- **DELETE** `process_complex_payment_v3` (v4 is standard).
- **DELETE** `recalculate_all_group_financials_v2` (v3 is standard).
- **ENFORCE** `log_audit_event` calls in any missed financial RPCs (Preliminary check shows they are present, but a deep audit will be performed).

---

### [Data Layer] :data

#### [MODIFY] [Mappers](file:///C:/Users/CRISS/AndroidStudioProjects/SanibonaniSave_Full/data/src/main/java/com/sanibonani/save/data/repository/mappers/)
- Ensure `MemberMappers.kt` and `GroupMappers.kt` correctly handle the `updated_at` field if we decide to sync it, or ensure it's not shadowing the local Room `updated_at`.

## Verification Plan

### Automated Tests
- Run `:data:testDebugUnitTest` to verify DAO sync logic.
- Specifically verify `LedgerDao` orphan pruning via a new test case if necessary.

### Manual Verification
1. Perform a "Repair Data" action in the Admin Portal and verify it uses `v3` RPC.
2. Verify that deleting a membership in the database is reflected in the app after a full sync.
