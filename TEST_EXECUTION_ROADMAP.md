# 🎯 Test Execution Roadmap - SanibonaniSave

**Step-by-step guide to execute comprehensive functional testing**

*Date: May 4, 2026*  
*Build Status: ✅ SUCCESSFUL (no errors)*  
*Gradle: 8.13*  
*Kotlin: 2.1.0*

---

## 📋 Current Status Summary

### ✅ What Has Been Completed

1. **Platform Admin Suspend Button Fixes** ✅
   - Fixed state synchronization between `isPlatformSuspended` and `feeStatus`
   - Added `updateGroupSuspensionState()` helper for atomic updates
   - Updated UI to compute effective suspension correctly
   - Added unit tests validating both suspend and unsuspend flows
   - Build compiles cleanly

2. **Simulated Member Data with 4 Personas** ✅
   - Added 4 named persona members with realistic profiles:
     - Nompumelelo Dlamini (22-mo senior, loans, beneficiaries, multi-group)
     - Sipho Radebe (2-mo probation, PoR pending)
     - Busisiwe Mthembu (suspended, overdue, late fees, multi-group)
     - Mandla Sithole (brand new, pending payment)
   - Created explicit financial histories:
     - ~20 contributions per persona (Nompumelelo)
     - Loan records with repayments
     - Beneficiary records (Burial Society)
   - Updated AGENTS.md with test credentials and scenario descriptions

3. **Testing Infrastructure** ✅
   - 330+ comprehensive tests created
   - Unit tests, integration tests, UI tests, E2E tests
   - Test documentation: COMPREHENSIVE_TEST_SUITE.md, TEST_EXECUTION_GUIDE.md, TEST_INVENTORY.md
   - Testing frameworks configured: JUnit4, Espresso, Compose Test, Turbine

4. **New Testing Documentation** ✅
   - FUNCTIONAL_TESTING_VERIFICATION_PLAN.md (5 phases, 100+ test cases)
   - QUICK_TESTING_CHECKLIST.md (fast reference, ~2 hour path)
   - This document (step-by-step execution guide)

---

## 🚀 Quick Start (Next 30 Minutes)

### Option A: Unit Tests Only (2 minutes)
```bash
cd "C:\Users\CRISS\AndroidStudioProjects\SanibonaniSave_Full"

# Run all unit tests locally
./gradlew test
```

Expected:
```
BUILD SUCCESSFUL
Tests run: 170+
Failures: 0
Time: ~2-3 minutes
```

### Option B: Full Test Suite (30 minutes - requires emulator/device)
```bash
# Start Android emulator or connect device

# Run all tests
./gradlew test connectedAndroidTest

# Or specific tests
./gradlew connectedAndroidTest --tests "*PlatformAdmin*"
```

---

## 🎯 Full Testing Execution Plan (2-4 hours)

### Phase 1: Setup & Prerequisites (10 minutes)

**Step 1.1**: Verify Environment
```bash
# Check Gradle
./gradlew --version
# Expected: Gradle 8.13

# Check device
adb devices
# Expected: At least one device listed (emulator or physical device)
```

**Step 1.2**: Verify Build
```bash
cd "C:\Users\CRISS\AndroidStudioProjects\SanibonaniSave_Full"
./gradlew clean :app:assembleDebug
# Expected: BUILD SUCCESSFUL
```

**Step 1.3**: Verify Database
- Ensure Supabase project accessible
- Run seeder: `seed_30_groups_300_members.sql` (if not already run)
- Verify in AGENTS.md test credentials are current

---

### Phase 2: Unit Tests (Fast, ~3 minutes)

**Step 2.1**: Run Unit Tests
```bash
./gradlew test
```

**Expected Output**:
```
BUILD SUCCESSFUL
Tests included:
  - ValidationUtilsTest (40+ tests)
  - PaymentCalculatorTest 
  - Other unit tests
```

**Step 2.2**: Review Test Reports
```bash
# Open HTML report
start "build/reports/tests/test/index.html"
```

---

### Phase 3: Phase 1 Functional Testing - Suspend Button (25 minutes)

**Step 3.1**: Code Review (5 minutes)
1. Open IDE
2. Search for: `updateGroupSuspensionState`
3. Verify it exists and updates both fields
4. Mark as complete: ✅

**Step 3.2**: Run Unit Tests for Phase 1 (5 minutes)
```bash
./gradlew test --tests "*PlatformAdminViewModel*"
```

**Step 3.3**: Manual UI Testing (15 minutes)

1. **Start Emulator/Device**
   ```bash
   adb devices
   ```

2. **Install App**
   ```bash
   ./gradlew installDebug
   ```

3. **Launch App**
   - Open SanibonaniSave
   - Login: torrymsimango@gmail.com / torry123M

4. **Test Cases** (use QUICK_TESTING_CHECKLIST.md from Phase 1 section)
   - [ ] Test suspend action (button, dialog, state change)
   - [ ] Test unsuspend action
   - [ ] Test dialog reset per group
   - [ ] Test error handling (go offline, try to suspend)
   
   *Total: 6-8 test cases, ~2 min each*

---

### Phase 4: Phase 2 Functional Testing - Member Personas (60 minutes)

**Step 4.1**: Database Verification (5 minutes)
Run SQL queries from QUICK_TESTING_CHECKLIST.md Phase 2 section to verify persona data exists.

**Step 4.2**: Test Nompumelelo Journey (15 minutes)
- Login: member3@test.com / password123
- Test each tab as per QUICK_TESTING_CHECKLIST.md
- Verify: 22-month member, R5k loan, 3 beneficiaries, multi-group

**Step 4.3**: Test Sipho Journey (12 minutes)
- Login: member4@test.com / password123
- Test: Probation status, 2 paid + 1 due, PoR pending
- Verify: Full payment triggers status transition to ACTIVE

**Step 4.4**: Test Busisiwe Journey (12 minutes)
- Login: member5@test.com / password123
- Test: Suspension notice, restricted access, overdue tracking
- Verify: Request lift suspension available

**Step 4.5**: Test Mandla Journey (16 minutes)
1. Login: member6@test.com / password123
2. See pending payment notice
3. Click "Pay Now"
4. Complete payment for joining fee
5. Verify: Status transitions to PROBATION
6. Verify: Documents unlocked
7. Verify: First contribution now due

---

### Phase 5: Phase 3 - Analytics Dashboard (15 minutes)

**Step 5.1**: Platform Admin Analytics
- Login: torrymsimango@gmail.com / torry123M
- Navigate: Analytics tab
- Verify: 30 groups, 300+ members, metrics calculated
- Test: Search, filter, sort functions

**Step 5.2**: Fee Management
- Navigate: Fee Management tab
- Verify: Platform and admin fees displayed
- Test: Update a fee, verify UX feedback

---

### Phase 6: Phase 4 - RLS & Access Control (15 minutes)

**Step 6.1**: Platform Admin Scope
- Verify: torrymsimango@gmail.com sees all 30 groups
- Verify: Can access All Admin functions

**Step 6.2**: Group Admin Scope
- Login: admin2@test.com / password123
- Verify: Sees only their own group (NOT all 30)
- Verify: Cannot access Platform Admin functions
- Try direct URL to other group → Should be blocked

**Step 6.3**: Member Scope
- Login: member1@test.com / password123
- Verify: Cannot see "All Groups" list
- Verify: Can see only their groups in switcher
- Verify: Cannot see other members' data

---

### Phase 7: Phase 5 - Database Integrity (20 minutes)

**Step 7.1**: Run SQL Verification Queries
Copy queries from QUICK_TESTING_CHECKLIST.md Phase 5 and run in Supabase:

1. Group count: 30 ✅
2. Member count: 300+ ✅
3. Persona emails all present ✅
4. Nompumelelo has 20+ contributions ✅
5. Nompumelelo has 3 beneficiaries ✅
6. No orphaned records ✅
7. Member counts accurate ✅
8. Group balances accurate ✅

---

## 📊 Testing Execution Timeline

| Phase | Topic | Duration | Status |
|-------|-------|----------|--------|
| 1 | Setup | 10 min | ⬜ |
| 2 | Unit Tests | 3 min | ⬜ |
| 3 | Suspend Button | 25 min | ⬜ |
| 4 | Personas | 60 min | ⬜ |
| 5 | Analytics | 15 min | ⬜ |
| 6 | RLS/Access | 15 min | ⬜ |
| 7 | Database | 20 min | ⬜ |
| **TOTAL** | **All Phases** | **~150 min (2.5 hr)** | **⬜** |

---

## 📱 Device Setup for Testing

### Option 1: Android Emulator (Recommended)

```bash
# Create emulator (if needed)
emulator -avd Pixel_7_API_31 &

# Check connection
adb devices
# Expected: emulator-5554    device
```

### Option 2: Physical Device

```bash
# Connect via USB, enable Developer Mode
adb devices
# Expected: your-device-name    device
```

---

## 🔗 Reference Documents

Use these documents during testing:

1. **FUNCTIONAL_TESTING_VERIFICATION_PLAN.md**
   - Detailed test cases for all 5 phases
   - Expected results for each test

2. **QUICK_TESTING_CHECKLIST.md**
   - Fast reference checklist
   - Copy-paste SQL queries
   - Quick pass/fail tracking

3. **AGENTS.md**
   - Test credentials table
   - Persona descriptions

4. **COMPREHENSIVE_TEST_SUITE.md**
   - Full testing architecture
   - Test patterns and best practices

5. **TEST_EXECUTION_GUIDE.md**
   - Detailed how-to for running tests
   - Troubleshooting guide

---

## 🆘 Common Issues & Fixes

### Issue: Emulator Won't Start
**Solution**:
```bash
# Kill existing processes
adb kill-server

# Start fresh
emulator -avd Pixel_7_API_31 &
adb wait-for-device
```

### Issue: Tests Fail - Login Fails
**Solution**:
1. Verify email format exactly matches AGENTS.md
2. Check password is "password123" (case-sensitive)
3. Verify seeder has run: check audit log for SEED_COMPLETE marker
4. Clear app data: `adb shell pm clear com.sanibonani.save`

### Issue: Persona Doesn't Exist in Database
**Solution**:
1. Run seeder: `seed_30_groups_300_members.sql`
2. Verify in Supabase SQL Editor: 
   ```sql
   SELECT email FROM auth.users WHERE email LIKE 'member%@test.com';
   ```
3. If not there, re-run seeder

### Issue: Suspend Button Tests Fail
**Solution**:
1. Check code has `updateGroupSuspensionState()` method
2. Verify both `isPlatformSuspended` and `feeStatus` update together
3. Run unit tests: `./gradlew test --tests "*PlatformAdminViewModel*"`
4. Check test output for specific assertion failures

### Issue: Analytics Shows Wrong Counts
**Solution**:
1. Refresh page (clear cache)
2. Check database directly with SQL query
3. Verify Analytics query logic in ViewModel

---

## ✅ Sign-Off Checklist

When all testing complete, verify:

**Phase 1: Suspend Button Logic** ✅
- [ ] Code review passed
- [ ] Unit tests all passed
- [ ] Manual UI tests passed (all 6-8 cases)
- [ ] No regressions

**Phase 2: Member Personas** ✅
- [ ] All 4 personas login successfully
- [ ] Nompumelelo: 22-mo, loan, beneficiaries working
- [ ] Sipho: Probation, PoR pending working
- [ ] Busisiwe: Suspension UI working correctly
- [ ] Mandla: Payment → Status transition working

**Phase 3: Analytics** ✅
- [ ] 30 groups visible
- [ ] 300+ members visible
- [ ] Search and filters working
- [ ] Fee management functional

**Phase 4: RLS/Access** ✅
- [ ] Platform admin sees all data
- [ ] Group admin sees only their group
- [ ] Members cannot see other members
- [ ] Data properly isolated

**Phase 5: Database** ✅
- [ ] All SQL checks passed
- [ ] No orphaned records
- [ ] Counts accurate
- [ ] Seeder marker present

**Overall Result**: 🟢 PASS / 🔴 FAIL

---

## 📞 Testing Support

**If Tests Fail**:
1. Refer to QUICK_TESTING_CHECKLIST.md troubleshooting section
2. Check FUNCTIONAL_TESTING_VERIFICATION_PLAN.md for detailed test setup
3. Review error logs for specific messages
4. Consult COMPREHENSIVE_TEST_SUITE.md for architecture questions

**If You Get Stuck**:
1. Check current logs in Supabase
2. Verify device connection: `adb devices`
3. Clear app cache: `adb shell pm clear com.sanibonani.save`
4. Restart emulator/device
5. Re-run tests

---

## 🎉 Success Criteria

All testing passes when:

✅ Build compiles cleanly  
✅ All unit tests pass (170+)  
✅ All manual test cases pass (100+)  
✅ Suspend button logic verified  
✅ All 4 personas working as designed  
✅ Analytics showing correct metrics  
✅ RLS properly enforcing access  
✅ Database integrity maintained  
✅ No critical regressions  
✅ No data leakage between groups  

---

## 📝 Session Notes

Use this space to track your testing session:

```
Session Start Time: ________________
Session End Time: ________________

Issues Encountered:
1. ___________________________
2. ___________________________

Resolutions Applied:
1. ___________________________
2. ___________________________

Overall Result: ✅ PASS / 🔴 FAIL / 🟡 PARTIAL

Sign-Off:
Tester: ________________
Date: ________________
Recommendation: ________________________
```

---

## 🚀 Next Steps After Testing

If all tests pass:

1. **Code Review** - Have team review fixes
2. **Merge to Main** - Merge all changes to main branch
3. **Deploy to Production** - Follow deployment checklist
4. **Monitor** - Watch for issues in production
5. **Document** - Update release notes with changes

If any tests fail:

1. **Debug** - Use error logs to identify root cause
2. **Fix** - Apply fixes based on error analysis
3. **Re-test** - Run failed tests again
4. **Document** - Add test case for regression prevention
5. **Retry** - Return to testing from failed phase

---

*Roadmap Created: May 4, 2026*  
*Target Completion: Today*  
*Status: 🚀 Ready to Execute*

**Let's make SanibonaniSave bulletproof! 🎯**

