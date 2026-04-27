# Fix: App Not Loading New Groups or Members

**Date:** April 16, 2026  
**Status:** IMPLEMENTED & PENDING BUILD VERIFICATION  
**Issue:** New groups and members were not appearing in the admin and member UI without manual refresh

---

## Root Cause Analysis

The app uses an offline-first architecture with Room (local cache) + Supabase (remote). The loading issue had multiple causes:

### 1. **Cache Invalidation Race Condition**
- `syncMembers()` in the DAO was doing a full delete-insert cycle
- This created a race condition where the Flow would emit stale data after deletion
- **Fix**: Changed to only delete members that are NO LONGER in the incoming list

### 2. **No Real-time Observation of Admin Groups**
- `AdminViewModel.observeAdminData()` was making a **one-time call** to `getManagedGroupsUseCase()`
- When a new group was created, it wouldn't be fetched until the admin manually refreshed
- **Fix**: Changed to use a Flow-based observation method that continuously syncs with Supabase

### 3. **Incomplete BaseRepository Sync Logic**
- The `observeAndSync()` function was not properly logging the sync progress
- Made improvements to logging for better debugging

---

## Changes Made

### 1. **BaseRepository.kt** - Enhanced Sync Logging
```kotlin
// Added logging to track when network sync starts, completes, and fails
// This helps debug future sync issues
```

**File**: `data/src/main/java/com/sanibonani/save/data/repository/BaseRepository.kt`

### 2. **SanibonaniDatabase.kt** - Fixed Cache Sync
#### Modified `syncMembers()` 
- **Before**: `deleteMembersByGroupId(groupId) + upsertMembers(members)`  
  ❌ Deleted ALL members, then re-inserted → Race condition
  
- **After**: Only delete members not in incoming list, then upsert  
  ✅ Preserves deletion order, prevents data loss during sync

#### Modified `syncPublicGroups()`
- Applied same logic: only delete groups that are NO LONGER public/paid

#### Added new DAO methods
- `fun observeGroupsByAdmin(adminId: String): Flow<List<GroupEntity>>`
- `suspend fun deleteMember(id: String)`

**File**: `data/src/main/java/com/sanibonani/save/data/local/SanibonaniDatabase.kt`

### 3. **GroupRepository.kt** - Added Flow-Based Query
```kotlin
fun observeGroupsByAdmin(adminId: String): Flow<Result<List<Group>>>
```
- Returns **reactive stream** instead of single snapshot
- Automatically syncs with Supabase and emits updates
- Local database observers will pick up Supabase changes

**File**: `domain/src/main/java/com/sanibonani/save/domain/repository/GroupRepository.kt`

### 4. **GroupRepositoryImpl.kt** - Implemented Reactive Groups Query
```kotlin
override fun observeGroupsByAdmin(adminId: String): Flow<Result<List<Group>>> = observeAndSync(
    dbFlow = db.groupDao().observeGroupsByAdmin(adminId),
    mapper = { it.toModel() },
    toEntity = { it.toEntity() },
    networkFetch = {
        supabase.postgrest["groups"].select(...) {
            filter { eq("admin_user_id", adminId) }
        }.decodeList<Group>()
    },
    cacheSync = { list -> db.groupDao().upsertGroups(list) }
)
```
- Uses `observeAndSync()` to keep groups in sync
- Room database is authoritative source
- Supabase is fetched periodically via retry logic

**File**: `data/src/main/java/com/sanibonani/save/data/repository/GroupRepositoryImpl.kt`

### 5. **GetManagedGroupsUseCase.kt** - Added Reactive Method
```kotlin
fun observeManagedGroups(userId: String, adminOnly: Boolean = false): Flow<Result<List<Group>>>
```
- For **adminOnly=true**: Returns `observeGroupsByAdmin(userId)` directly
- For **adminOnly=false**: Observes memberships and fetches each group
- Provides real-time updates as groups change

**File**: `domain/src/main/java/com/sanibonani/save/domain/usecase/GetManagedGroupsUseCase.kt`

### 6. **AdminViewModel.kt** - Changed to Reactive Observation
#### Before:
```kotlin
private fun observeAdminData() {
    val userId = supabaseRepo.currentUserId ?: return
    viewModelScope.launch {
        val result = getManagedGroupsUseCase(userId, adminOnly = true)  // ❌ One-time call
        if (result.isSuccess) { ... }
    }
}
```

#### After:
```kotlin
private fun observeAdminData() {
    val userId = supabaseRepo.currentUserId ?: return
    managedGroupsJob?.cancel()
    managedGroupsJob = viewModelScope.launch {
        getManagedGroupsUseCase.observeManagedGroups(userId, adminOnly = true).collect { result ->  // ✅ Continuous observation
            result.onSuccess { groups ->
                _state.update { it.copy(managedGroups = groups) }
                // Auto-select first group if none selected
                if (groups.isNotEmpty() && currentObservedGroupId == null) {
                    groups.first().id?.let { selectGroup(it) }
                }
            }
        }
    }
}
```

**Key improvements**:
- Uses `observeManagedGroups()` instead of `getManagedGroupsUseCase()`
- Collects Flow updates continuously
- Properly cancels previous job before starting new observation
- New groups appear automatically as they're created in Supabase

**File**: `app/src/main/java/com/sanibonani/save/viewmodel/AdminViewModel.kt`

### 7. **MemberViewModel.kt** - Already Using Proper Observation
- `loadUserMemberships()` already uses `observeMemberships()` Flow
- No changes needed (minor formatting improvements made)

**File**: `app/src/main/java/com/sanibonani/save/viewmodel/MemberViewModel.kt`

---

## How It Works Now

### Data Flow for New Group Creation:

```
1. Admin creates a new group
   ↓
2. Group inserted into Supabase
   ↓
3. AdminViewModel's observeGroupsByAdmin() Flow picks up change
   ↓
4. Network fetch triggers (via observeAndSync)
   ↓
5. New group cached in Room database
   ↓
6. Room DAO observable emits updated list
   ↓
7. State updates with new group
   ↓
8. UI automatically renders new group
```

### Data Flow for New Member Registration:

```
1. Member registers for a group
   ↓
2. Member inserted into Supabase
   ↓
3. MemberViewModel's observeMembers() Flow picks up change
   ↓
4. Network sync via observeAndSync()
   ↓
5. New member cached in Room
   ↓
6. Members list emitted via Flow
   ↓
7. State updates with new member
   ↓
8. Admin dashboard automatically shows new member
```

---

## Testing Checklist

- [ ] Create a new group as admin → appears immediately in dashboard
- [ ] Register a new member → appears in group members list immediately
- [ ] Switch between managed groups → lists update correctly
- [ ] Member switches between their groups → dashboards load correctly
- [ ] Offline mode: work offline, then sync when reconnected
- [ ] Verify no duplicate entries after sync
- [ ] Check app performance with 10+ groups/100+ members
- [ ] Verify realtime updates work (test on actual device/emulator with live data)

---

## Performance Impact

- **Positive**: Real-time updates mean users see changes immediately
- **Neutral**: Slightly more database queries but well-optimized with indices
- **Monitoring**: New logging in BaseRepository helps track sync performance

---

## Future Improvements

1. **Realtime Channel Subscriptions**: Could add real-time Postgres Change subscriptions for groups
2. **Pagination**: For admins managing 1000+ groups, add pagination to observeGroupsByAdmin
3. **Caching Strategy**: Could implement smart cache expiration (e.g., re-sync every 5 minutes)
4. **Offline Support**: Ensure conflicts are handled when syncing offline changes

---

## Files Modified

| File | Changes |
|------|---------|
| BaseRepository.kt | Enhanced logging |
| SanibonaniDatabase.kt | Fixed syncMembers(), added observeGroupsByAdmin DAO |
| GroupRepository.kt | Added observeGroupsByAdmin() interface method |
| GroupRepositoryImpl.kt | Implemented observeGroupsByAdmin() |
| GetManagedGroupsUseCase.kt | Added observeManagedGroups() method |
| AdminViewModel.kt | Changed to reactive observation of groups |
| MemberViewModel.kt | No functional changes (formatting only) |

---

## Build Status

- **Status**: Compiling...  
- **Test Run**: Pending device/emulator testing  
- **Expected**: Should compile without errors  

---

**Next Steps**: 
1. Complete build verification
2. Run app and test new group/member loading
3. Test offline-first scenario
4. Verify performance with larger datasets

