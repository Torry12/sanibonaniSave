# Groups & Members Loading Fix - Testing Guide

**Status:** Implementation Complete - Awaiting Build Verification  
**Date:** April 16, 2026  
**Project:** SanibonaniSave

---

## 🎯 What Was Fixed

The app was not loading new groups or members in real-time. Users had to manually refresh to see newly created groups or members.

### Root Issues (3 causes):
1. **Cache Sync Race Condition** - Members were deleted then re-inserted, causing data loss
2. **No Real-time Group Observation** - Admin groups were fetched once, not continuously
3. **Improper Flow-Based Updates** - Missing reactive observation patterns

---

## 📋 Files Changed

### Core Data Layer
| File | Changes | Impact |
|------|---------|--------|
| `data/repository/BaseRepository.kt` | Enhanced logging for sync operations | Better debugging |
| `data/local/SanibonaniDatabase.kt` | Fixed `syncMembers()` logic, added `observeGroupsByAdmin()` DAO | Prevents data loss during sync |

### Domain/Repository Layer
| File | Changes | Impact |
|------|---------|--------|
| `domain/repository/GroupRepository.kt` | Added `observeGroupsByAdmin()` interface | Enables reactive group queries |
| `data/repository/GroupRepositoryImpl.kt` | Implemented `observeGroupsByAdmin()` with offline-first pattern | Real-time group updates |

### Use Cases
| File | Changes | Impact |
|------|---------|--------|
| `domain/usecase/GetManagedGroupsUseCase.kt` | Added `observeManagedGroups()` flow-based method | Reactive group observation |

### ViewModels
| File | Changes | Impact |
|------|---------|--------|
| `app/viewmodel/AdminViewModel.kt` | Changed from one-time fetch to continuous observation | Groups load automatically |
| `app/viewmodel/MemberViewModel.kt` | Minor formatting (already using proper observation) | No functional change |

---

## 🧪 Testing Procedures

### Test 1: New Group Creation (Admin)
**Objective:** Verify newly created groups appear immediately in the admin dashboard

**Steps:**
1. Launch app as **admin** (`torryymsimango@gmail.com` / `torry123M`)
2. Navigate to **Admin Dashboard**
3. Note the current number of managed groups
4. Open a **new browser tab** or **second device**
5. Create a new group (or use Create Group API)
6. Return to the app **without refreshing/reloading**
7. ✅ **Expected:** New group appears in the list within 2-3 seconds

**Verification Logs:**
- Look for: `"Starting network sync..."` in Logcat
- Then: `"Network fetch completed, syncing X items to cache"`
- Finally: `"Cache sync completed"`

---

### Test 2: New Member Registration (Member)
**Objective:** Verify newly registered members appear in member list immediately

**Steps:**
1. Login as **member** (existing user)
2. Open a group dashboard
3. Note current member count
4. Open a **new browser tab** or **second device**
5. Register a new member in that group
6. Return to the app dashboard **without refreshing**
7. ✅ **Expected:** New member appears in members list within 2-3 seconds

---

### Test 3: Multi-Group Switching (Admin)
**Objective:** Verify switching between groups loads correct data

**Steps:**
1. Login as admin with **2+ managed groups**
2. Select Group A
3. Note members in Group A
4. Switch to Group B
5. ✅ **Expected:** Group B members load (NOT Group A's members)
6. Switch back to Group A
7. ✅ **Expected:** Group A members load correctly again

**What to Watch:**
- State should reset when switching groups (no cross-contamination)
- All fields should clear before loading new group data

---

### Test 4: Offline Scenario
**Objective:** Verify offline-first functionality still works

**Steps:**
1. Open app and load a group (ensure it's cached)
2. Toggle airplane mode ON
3. Data should still display (from cache)
4. Toggle airplane mode OFF
5. ✅ **Expected:** Data syncs after ~2 seconds
6. Check for new items that may have been added while offline

---

### Test 5: Cache Invalidation (No Duplicates)
**Objective:** Verify members don't duplicate after sync

**Steps:**
1. Open a group with 10 members
2. Run background sync (or wait 30 seconds)
3. ✅ **Expected:** Still 10 members (not 20)
4. Check database: `SELECT COUNT(*) FROM members WHERE group_id = 'xyz'`
5. ✅ **Expected:** Count matches UI

**In Supabase SQL Editor:**
```sql
-- Check for duplicates
SELECT member_id, COUNT(*) as cnt 
FROM members 
WHERE group_id = 'GROUP_ID' 
GROUP BY member_id 
HAVING COUNT(*) > 1;
-- Expected: Empty result (no duplicates)
```

---

### Test 6: Error Handling
**Objective:** Verify graceful error handling when sync fails

**Steps:**
1. Turn off network
2. Wait 5+ seconds
3. Turn network back on
4. ✅ **Expected:** Data syncs without crashing
5. Check for error message (should be user-friendly)

---

### Test 7: Performance with Large Datasets
**Objective:** Verify app handles many groups/members

**Prerequisites:**
- Admin with 20+ groups
- Groups with 100+ members each

**Steps:**
1. Load dashboard
2. Measure load time (should be <3 seconds)
3. Switch between groups
4. ✅ **Expected:** Smooth transitions, no UI freezing
5. Check memory usage

**Logcat for Performance:**
```
grep "Network fetch completed" logcat
# Should show sync time
```

---

## 📊 Success Criteria

| Test | Pass Criteria | Status |
|------|---------------|--------|
| New Group Appears | <3 second delay | ⏳ |
| New Member Appears | <3 second delay | ⏳ |
| Group Switch | Correct data loads | ⏳ |
| Offline Mode | Works without data | ⏳ |
| No Duplicates | Count matches UI | ⏳ |
| Error Handling | Graceful fallback | ⏳ |
| Performance | <3 sec load time | ⏳ |

---

## 🔍 Debugging Guide

### If New Groups DON'T Appear:

**Check 1:** Verify groups are being fetched
```kotlin
// In Logcat, search for:
"Starting network sync..."
"Network fetch completed"
```
- If these logs don't appear → Network fetch didn't trigger
- Check network connectivity

**Check 2:** Verify cache is updating
```sql
-- In Supabase SQL Editor
SELECT COUNT(*) FROM groups WHERE admin_user_id = 'USER_ID';
```
- Compare with UI count
- If DB has more → Cache sync failed

**Check 3:** Verify DAO is emitting
```kotlin
// Add to AdminViewModel
AppLogger.d("AdminVM", "Groups updated: ${state.value.managedGroups.size}")
```

---

### If Duplicates Appear:

**Check 1:** Verify syncMembers logic
```kotlin
// This SHOULD happen:
// 1. Get existing members: [A, B, C]
// 2. Get incoming: [B, C, D]  
// 3. Delete only A (not in incoming)
// 4. Insert B, C, D

// NOT this (old logic):
// 1. Delete ALL members
// 2. Insert new ones
```

**Check 2:** Query database
```sql
SELECT member_id, COUNT(*) 
FROM members 
GROUP BY member_id 
HAVING COUNT(*) > 1;
```
- Should be empty

---

### If Sync Takes Too Long:

**Check 1:** Network latency
```kotlin
// Monitor retry delays in Logcat
"Retry attempt 1/3 after 1000ms"
```

**Check 2:** Database size
```sql
SELECT COUNT(*) FROM groups;
SELECT COUNT(*) FROM members;
SELECT COUNT(*) FROM contributions;
```
- Large tables may slow sync

---

## 🚀 Before Going Live

### Checklist:
- [ ] Build completes without errors
- [ ] All 7 tests pass
- [ ] No ANRs (Application Not Responding)
- [ ] No crashes during switching
- [ ] Logcat shows clean sync operations
- [ ] Performance acceptable
- [ ] Offline mode works
- [ ] Admin can be created in clean database

### Production Readiness:
- [ ] Code review completed
- [ ] QA signed off
- [ ] Performance benchmarks met
- [ ] Release notes prepared

---

## 📈 Metrics to Monitor

### Success Metrics:
- **Sync Time:** Should be <2 seconds for 100 members
- **Cache Hit Rate:** 90%+ items from local cache first
- **Error Rate:** <1% failed syncs
- **User Complaints:** New groups appearing = 0 complaints

### Performance Metrics:
- **Memory Usage:** Should not increase after 10 group switches
- **Database Size:** Room DB should be <50MB
- **Sync Frequency:** Network calls every 30 seconds max

---

## 📝 Test Report Template

```markdown
### Test Results - [DATE]

**Tester:** [NAME]  
**Device:** [MODEL]  
**OS Version:** [VERSION]  
**App Build:** [BUILD_NUMBER]

| Test | Result | Notes |
|------|--------|-------|
| New Group | ✅/❌ | [Observations] |
| New Member | ✅/❌ | [Observations] |
| Group Switch | ✅/❌ | [Observations] |
| Offline | ✅/❌ | [Observations] |
| Duplicates | ✅/❌ | [Observations] |
| Errors | ✅/❌ | [Observations] |
| Performance | ✅/❌ | [Observations] |

**Issues Found:**
1. [Issue Description]

**Recommendations:**
1. [Recommendation]
```

---

## 🎓 What Changed Under the Hood

### Before (❌ One-time fetch):
```
Admin opens dashboard
    ↓
getManagedGroupsUseCase(userId) called ONCE
    ↓
Fetches groups from Supabase
    ↓
State updated
    ↓
New group created somewhere else
    ↓
Admin doesn't see it (until refresh)
```

### After (✅ Continuous observation):
```
Admin opens dashboard
    ↓
observeManagedGroups(userId) starts FLOW
    ↓
Room observable emits current groups
    ↓
State updates with groups
    ↓
Network sync starts in background
    ↓
New group created somewhere else
    ↓
Supabase updated
    ↓
Network fetch picks it up
    ↓
Room database updated
    ↓
DAO observable emits new list
    ↓
State updates automatically
    ↓
UI shows new group (NO REFRESH NEEDED!)
```

---

## ✅ Next Steps

1. **Build Verification:** Wait for `gradlew build` to complete
2. **Install on Device:** Deploy APK to test device
3. **Run Tests:** Follow testing procedures above
4. **Document Results:** Use test report template
5. **Deploy:** If all tests pass, ready for production

---

**Expected Timeline:**
- Build: 5-10 minutes
- Testing: 30 minutes
- Verification: 15 minutes
- **Total:** ~1 hour to production ready

---

**Build Status:** Pending...  
Check terminal output for final results.

