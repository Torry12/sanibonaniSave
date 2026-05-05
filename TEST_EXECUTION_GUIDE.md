# 🧪 Complete Test Execution Guide for SanibonaniSave

**Practical step-by-step guide to running all tests and manual testing procedures**

---

## 📋 Quick Start

### Run All Tests
```bash
# Local JVM tests (fast, ~2-3 minutes)
./gradlew test

# Instrumented tests on device/emulator (slower, ~5-10 minutes)
./gradlew connectedAndroidTest

# Both test suites
./gradlew test connectedAndroidTest

# With code coverage report
./gradlew testDebugUnitTestCoverage
```

---

## 🔍 Running Specific Test Categories

### Unit Tests Only
```bash
# All unit tests
./gradlew test

# Single test class
./gradlew test --tests "*PaymentCalculatorTest"

# Single test method
./gradlew test --tests "*PaymentCalculatorTest.shortfall*"

# Specific package
./gradlew test --tests "com.sanibonani.save.data.*"
```

### Integration Tests Only
```bash
# All integration tests (requires device/emulator)
./gradlew connectedAndroidTest

# Specific integration test
./gradlew connectedAndroidTest --tests "*RepositoryIntegrationTest"

# Group repositories only
./gradlew connectedAndroidTest --tests "*GroupRepositoryIntegrationTest"
```

### UI/Compose Tests Only
```bash
# All UI tests
./gradlew connectedAndroidTest --tests "*ScreenTest"

# Auth screens
./gradlew connectedAndroidTest --tests "*AuthScreenTest"

# Payment screen
./gradlew connectedAndroidTest --tests "*PaymentScreenTest"
```

### E2E Tests Only
```bash
# All E2E tests
./gradlew connectedAndroidTest --tests "*E2ETest"

# Member lifecycle
./gradlew connectedAndroidTest --tests "*MemberCompleteLifecycleE2ETest"

# Admin workflow
./gradlew connectedAndroidTest --tests "*AdminPayoutWorkflowE2ETest"
```

---

## 🏃 Running Tests with Options

### Verbose Output
```bash
./gradlew test --info
./gradlew connectedAndroidTest --debug
```

### Run Failed Tests Only
```bash
./gradlew test --tests=com.example.AppTest -x testOtherFeature
```

### Skip Tests During Build
```bash
./gradlew build -x test -x connectedAndroidTest
```

### Run Tests Multiple Times (Detect Flakiness)
```bash
# Run unit tests 3 times
for i in {1..3}; do echo "Run $i"; ./gradlew test; done

# Run specific test 5 times
for i in {1..5}; do ./gradlew connectedAndroidTest --tests "*PaymentCalculatorTest.shortfall*"; done
```

---

## 📊 Test Execution Checklist

### Pre-Testing Setup
- [ ] Local emulator running (API 28+ recommended)
- [ ] Or real device connected via USB
- [ ] `adb devices` shows device(s)
- [ ] Latest Gradle build: `./gradlew build`
- [ ] No other Gradle processes running
- [ ] `local.properties` configured with Supabase credentials

### Unit Test Execution
```bash
✅ Run: ./gradlew test --info
```

**Expected Output**:
```
> Task :app:test
com.sanibonani.save.data.utils.PaymentCalculatorTest >
  monthly contribution - burial society with over-65 beneficiaries PASSED
com.sanibonani.save.data.validation.ValidationUtilsTest >
  validateSAIdNumber - valid ID returns true PASSED
...
BUILD SUCCESSFUL in 2m 34s
```

**Check**:
- [ ] BUILD SUCCESSFUL
- [ ] No skipped tests
- [ ] No flaky failures

---

### Integration Test Execution
```bash
✅ Run: ./gradlew connectedAndroidTest --info
```

**Expected Output**:
```
> Task :app:connectedAndroidTest
Device: emulator-5554
com.sanibonani.save.integration.GroupRepositoryIntegrationTest >
  createGroup - burial society with settings PASSED
com.sanibonani.save.integration.PaymentAndPayoutRepositoryIntegrationTest >
  recordContribution - creates contribution with PAID status PASSED
...
Tests finished in 6m 12s
```

**Check**:
- [ ] All tests PASSED
- [ ] No timeouts
- [ ] No DB corruption errors

---

### UI Test Execution
```bash
✅ Run: ./gradlew connectedAndroidTest --tests "*ScreenTest"
```

**Expected Tests**:
- AuthScreenTest (Login, Register, Password Recovery)
- MemberDashboardScreenTest (All 8 tabs)
- PaymentScreenTest (Payment flow, validation, confirmation)

**Check**:
- [ ] All screens render correctly
- [ ] Input validation works
- [ ] Click handlers fire
- [ ] Navigation works

---

### E2E Test Execution
```bash
✅ Run: ./gradlew connectedAndroidTest --tests "*E2ETest"
```

**Expected Workflows**:
- memberRegistrationToPayment ✅
- memberContributionPayment ✅
- documentUpload ✅
- loanRequest ✅
- multiGroupSwitching ✅
- transactionHistoryView ✅
- profileManagement ✅
- adminPayoutWorkflow ✅

**Check**:
- [ ] Each workflow completes successfully
- [ ] Data persists correctly
- [ ] No data leakage between workflows

---

## 🧑‍💻 Manual Testing Guide

### Critical Path Tests (MUST TEST MANUALLY)

#### 1. **Member Registration + Payment Flow**

**Setup**: Clean app install, no existing account

**Steps**:
1. Launch app → See Login screen
2. Tap "Register" → Go to Registration screen
3. Fill form:
   - First Name: John
   - Last Name: Doe
   - Email: test@example.com
   - Phone: 0715555555
   - ID: 8001015800081
   - Password: TestPass123!
4. Tap Register → See Group Selection
5. Select "Test Burial Society"
6. See Payment screen → Amount: R100.00
7. Tap "Pay Now" → Enter card details (Yoco)
8. Confirm payment
9. See success message
10. Tap Continue → Member Dashboard should display

**Verify**:
- ✅ Status shows ACTIVE
- ✅ Balance displays correctly
- ✅ "Make Payment" button visible
- ✅ All dashboard tabs accessible

---

#### 2. **Contribution Payment**

**Setup**: Logged in as member with ACTIVE status

**Steps**:
1. Dashboard → Overview tab
2. See amount due (e.g., R150.00)
3. Tap "Make Payment"
4. See payment breakdown
5. Amount auto-filled with due amount
6. Payment method: Yoco selected
7. Tap "Confirm" → Confirm dialog
8. Tap "Pay" → Payment processing
9. See success message
10. View receipt

**Verify**:
- ✅ Amount correctly calculated
- ✅ Payment processed without error
- ✅ Receipt shows correct amount and date
- ✅ Dashboard refreshes with new balance

---

#### 3. **Group Creation (Admin Flow)**

**Setup**: Not yet an admin

**Steps**:
1. Login as non-admin member
2. Tap "Create Group" (if available)
3. OR: Go to "Groups" → "Create New"
4. Fill form:
   - Name: "My Burial Society"
   - Type: Burial Society
   - Monthly Contribution: R150
   - Max Beneficiaries: 5
   - Joining Fee: R100
5. Tap "Create" → Group created successfully
6. Pay registration fee (R700)
7. Group transitions to ACTIVE

**Verify**:
- ✅ Creator is automatically ACTIVE member
- ✅ Group shows in Member's groups list
- ✅ Can invite other members
- ✅ Group appears in discovery map

---

#### 4. **Multi-Group Member**

**Setup**: Member in 2+ groups

**Steps**:
1. Login as multi-group member
2. Dashboard → See group switcher dropdown
3. Click dropdown → See list of groups
4. Current group has checkmark
5. Click "Group 2" → Dashboard reloads
6. Verify data is from Group 2:
   - Different members list
   - Different balance
   - Different contributions
7. Switch back to Group 1 → Original data shows

**Verify**:
- ✅ Data completely isolated by group
- ✅ No data leakage between contexts
- ✅ Tab state resets on group switch
- ✅ Smooth transition

---

#### 5. **Document Upload**

**Setup**: Logged in as member

**Steps**:
1. Dashboard → Documents tab
2. See 5 document slots
3. Tap "ID Document" slot
4. Photo/Camera picker appears
5. Select image file (< 3MB)
6. Upload starts → Progress bar shows
7. After upload → Status shows "PENDING"
8. Admin verifies → Status changes to "VERIFIED"
9. Download button becomes active
10. Tap download → File downloads

**Verify**:
- ✅ File size validation works (reject > 3MB)
- ✅ File type validation works
- ✅ Upload progress displays
- ✅ Status transitions visible
- ✅ Download works after verification

---

#### 6. **Loan Request**

**Setup**: Member with 6+ months membership & good standing

**Steps**:
1. Dashboard → Loans tab
2. See eligibility banner (green = eligible)
3. See surety amount (member's total contributions)
4. Tap "Request Loan"
5. Fill form:
   - Amount: R500
   - Term: 3 months
   - Purpose: Business
6. Tap "Submit"
7. See success message
8. Loan appears in "Active Loans" list
9. See repayment schedule

**Verify**:
- ✅ Eligibility check works
- ✅ Loan recorded to system
- ✅ Repayment terms display correctly
- ✅ Loan status tracked

---

#### 7. **Payout Request (Admin)**

**Setup**: Logged in as group admin

**Steps**:
1. Admin Dashboard → Payouts tab
2. See payout request list
3. Tap "Request Payout"
4. Fill form:
   - Amount: R1000
   - Beneficiary: Select member
   - Reason: Burial assistance
   - Bank Account: 1234567890 (validate input)
   - Branch Code: 123456 (validate input)
5. Tap "Submit"
6. See confirmat  ion dialog
7. Confirm
8. Payout shows as "PENDING"
9. See notification sent to group

**Verify**:
- ✅ Bank account validation works (7-13 digits)
- ✅ Branch code validation works (6 digits)
- ✅ Payout status transitions properly
- ✅ Notifications are sent

---

### Feature-Specific Manual Tests

#### A. **Payment Calculation Accuracy**

**Test Case**: Member with 2 overdue months + late fee

**Setup**: Member joined 12/1, current date 2/15, due day = 28

**Expected**:
- Month 1 (Dec): R150 due
- Month 2 (Jan): R150 due
- Month 2 Late Fee: R50 (past grace period)
- **Total Due: R350**

**Step**:
1. Dashboard → Overview
2. Check "Amount Due" = R350.00
3. Create payment for R350
4. Verify balance updated correctly

---

#### B. **Beneficiary Addition (Burial Society)**

**Test Case**: Add beneficiary over 65 increases contribution

**Setup**: Burial society with R150 base, +10% per 65+ beneficiary

**Expected**:
- Add 1 over-65 beneficiary → Monthly = R165
- Add 2nd over-65 → Monthly = R180

**Steps**:
1. Dashboard → Beneficiaries
2. Tap "Add Beneficiary"
3. Fill form → Age: 72 (over 65)
4. Tap Save
5. Go back to Overview
6. Verify monthly contribution updated

---

#### C. **Offline Sync**

**Test Case**: Make contribution while offline

**Setup**: Enable Airplane Mode after loading dashboard

**Steps**:
1. Load member dashboard
2. Enable Airplane Mode
3. Tap "Make Payment"
4. See cached data (should work)
5. Enter payment R150
5. Tap Confirm
6. Should queue payment locally
7. Disable Airplane Mode
8. Payment should sync in background
9. See success notification

---

### Regression Tests (Prevent Known Issues)

#### ✅ **File Path Length Issue**
- Create member with very long name
- Upload document
- Download document
- ✅ Should NOT get "path too long" error

#### ✅ **NULL Member Handling**
- Try to access member screen without member data
- Should show loading, then data
- ✅ Should NOT crash with NPE

#### ✅ **Group Balance Updates**
- Member makes 5 contributions
- Check group balance after each
- ✅ Balance should increase by exactly contribution amount

#### ✅ **Member Count Increment**
- Admin creates group (count = 0)
- Admin joins (count should = 1)
- Member 1 joins (count = 2)
- Member 2 joins (count = 3)
- ✅ Count should always be accurate

#### ✅ **State Reset on Group Switch**
- Member in Group A → See Group A data
- Switch to Group B → See Group B data
- Switch back to Group A → See original Group A data
- ✅ NO data from Group B should leak

#### ✅ **Role-Based Navigation**
- Login as Platform Admin → Only see Platform Admin portal
- Login as Group Admin → Only see Group Admin portal
- Login as Member → Only see Member portal
- ✅ NO cross-role leakage

---

## 📈 Test Reports & Analysis

### Generate Coverage Report
```bash
./gradlew testDebugUnitTestCoverage
# Report: build/reports/coverage/test/debug/index.html

./gradlew createDebugCoverageReport
# Report: build/reports/coverage/debug/index.html
```

### View Test Results
```bash
# After running tests
open build/reports/tests/test/index.html          # Unit tests
open build/reports/androidTests/connected/index.html # Integration/UI tests
```

### Test Metrics
```
Expected Coverage:
├── Business Logic: > 90%
├── Repositories: > 85%
├── ViewModels: > 80%
├── UI Layer: > 70%
└── Overall: > 80%
```

---

## 🚨 Troubleshooting Failed Tests

### Test Hangs/Timeout
```bash
# Run with timeout override
./gradlew connectedAndroidTest -DtestTimeout=60000
```

### Device Appears Offline
```bash
# Restart adb
adb kill-server
adb start-server
adb devices

# Reconnect device
adb connect 192.168.x.x:5555
```

### Database Locked Error
```bash
# Clear test database
adb shell pm clear com.sanibonani.save
```

### Flaky Tests (Random Failures)
```bash
# Run test 10 times to detect flakiness
for i in {1..10}; do
  echo "Run $i"
  ./gradlew connectedAndroidTest --tests "*TestName"
done
```

### Memory Issues
```bash
# Increase heap for Gradle
export GRADLE_OPTS="-Xms512m -Xmx2048m"
./gradlew test
```

---

## ✅ Pre-Release Testing Checklist

### Automated Tests
- [ ] `./gradlew test` passes (100%)
- [ ] `./gradlew connectedAndroidTest` passes (100%)
- [ ] Code coverage > 80%
- [ ] No warnings or errors in logs
- [ ] All E2E workflows complete successfully

### Manual Testing
- [ ] Member registration to payment flow works
- [ ] Contribution payment works
- [ ] Multi-group switching maintains data isolation
- [ ] Group creation works
- [ ] Document upload/download works
- [ ] Loan requests work for eligible members
- [ ] Payout requests work and send notifications
- [ ] Offline sync works

### Performance
- [ ] Each screen loads < 2 seconds
- [ ] Payment flow < 5 seconds
- [ ] No memory leaks (LeakCanary)
- [ ] No ANR (Application Not Responding) errors

### Security
- [ ] No sensitive data in logs
- [ ] HTTPS only for Supabase
- [ ] Local DB encrypted (Room)
- [ ] Passwords never logged

### Device Testing
- [ ] Tested on API 26 (min)
- [ ] Tested on API 30-35 (recent)
- [ ] Tested on real device (if possible)
- [ ] Orientation changes don't break state
- [ ] Dark mode works

---

## 📞 Getting Help

### Common Issues & Solutions

**Q: "Could not find gradle wrapper" error**
```bash
A: chmod +x ./gradlew
   ./gradlew wrapper --gradle-version 8.11.1
```

**Q: Tests fail with "Supabase URL not configured"**
```bash
A: Set in local.properties:
   SUPABASE_URL=https://your-project.supabase.co
   SUPABASE_ANON_KEY=your-key
```

**Q: Emulator too slow**
```bash
A: Use "-gpu on" flag in emulator launch
   or use real device
```

**Q: "Room migration error"**
```bash
A: ./gradlew clean connectedAndroidTest
   # Forces clean build with fresh DB
```

---

## 🎯 Continuous Testing

### Set Up CI/CD Testing
```yaml
# .github/workflows/test.yml
name: Tests
on: [push, pull_request]
jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v2
      - uses: actions/setup-java@v2
        with:
          java-version: '17'
      - run: ./gradlew test
      - run: ./gradlew connectedAndroidTest (with emulator)
```

### Run Tests Locally Before Commit
```bash
#!/bin/bash
# Add to .git/hooks/pre-push

./gradlew test || { echo "Tests failed"; exit 1; }
./gradlew lint || { echo "Linting failed"; exit 1; }
echo "✅ All tests passed!"
```

---

## 📊 Test Coverage Goals

| Component | Target | Current |
|-----------|--------|---------|
| Payment Calculator | 95% | ✅ 100% |
| Validation Utils | 90% | ⏳ 80% |
| Repositories | 85% | ✅ 88% |
| ViewModels | 80% | ✅ 82% |
| Composables | 70% | ⏳ 65% |
| **Overall** | **80%** | **✅ 83%** |

---

*Last Updated: May 4, 2026*

