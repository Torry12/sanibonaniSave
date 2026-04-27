# 🧪 SanibonaniSave Testing Guide

**Last Updated**: March 24, 2026  
**Status**: Ready for Testing & Deployment  
**Target**: Beta Users & QA Teams

---

## ✅ Pre-Testing Checklist

### Environment Setup
- [ ] Android Studio Panda (2024.2.1+) installed
- [ ] AGP 8.7.3 (comes with Android Studio)
- [ ] Min SDK 26 device or emulator available
- [ ] `local.properties` configured with all secrets
- [ ] `google-services.json` in `app/` folder

### Gradle Setup
```bash
# Verify gradle wrapper exists
ls -la gradlew gradlew.bat

# Verify Java 17
java -version  # Should be 17.x

# Verify Android SDK
echo %ANDROID_HOME%  # Should be set
```

### Supabase Setup
- [ ] Project created at supabase.io
- [ ] Schema uploaded: `supabase/schema.sql`
- [ ] Anon key in `local.properties` as `SUPABASE_ANON_KEY`
- [ ] Service role key in `local.properties` as `SUPABASE_SERVICE_ROLE_KEY`

### Firebase Setup
- [ ] Project created at console.firebase.google.com
- [ ] Android app added (package: `com.sanibonani.save`)
- [ ] `google-services.json` downloaded and placed in `app/`

---

## 🚀 Building the App

### Step 1: Sync Gradle
```bash
# From project root
./gradlew --version  # Verify gradle works

# Full sync
./gradlew clean
```

### Step 2: Build Debug APK
```bash
# Assemble debug build
./gradlew assembleDebug

# Output: app/build/outputs/apk/debug/app-debug.apk
```

### Step 3: Install on Device
```bash
# Install APK
./gradlew installDebug

# Or manual install
adb install app/build/outputs/apk/debug/app-debug.apk
```

---

## 🧪 Automated Testing

### Run All Tests
```bash
# Unit tests (fast, local JVM)
./gradlew test

# Expected output: ✓ All tests pass

# Instrumented tests (requires emulator/device)
./gradlew connectedAndroidTest

# Expected output: ✓ All tests pass
```

### Coverage Report
```bash
# Generate coverage report
./gradlew testDebugUnitTest --tests "*" 

# View report: app/build/reports/coverage/
```

---

## 🎯 Manual Testing Scenarios

### Authentication Flow
**Objective**: Verify sign-up, sign-in, token refresh, logout

**Test Case 1: Sign Up**
1. Launch app
2. See splash screen with connection check
3. Tap "Sign Up"
4. Enter valid email: `test.user@example.com`
5. Enter password: `TestPass123!`
6. Confirm password: `TestPass123!`
7. Tap "Create Account"
8. **Expected**: Home screen shows, user is logged in

**Test Case 2: Sign In (After Sign Out)**
1. Tap "Profile" → "Sign Out"
2. Verify sent to login screen
3. Enter email + password from Test Case 1
4. Tap "Sign In"
5. **Expected**: Home screen shows immediately (cached session)

**Test Case 3: Token Refresh**
1. Open Settings → Developer Options (Android)
2. Turn off network (simulate expired token)
3. Turn network back on
4. Perform any action (fetch groups, etc.)
5. **Expected**: Data loads without re-login (auto-refresh)

**Test Case 4: Logout**
1. From home screen, tap "Profile"
2. Scroll down, tap "Sign Out"
3. **Expected**: Logged out, sent to login screen

### Group Management
**Objective**: Verify group registration, browsing, joining

**Test Case 5: Browse Public Groups**
1. From home, tap "Browse Groups"
2. Scroll list of public groups
3. Tap on a group
4. **Expected**: Group detail screen loads with info, members, location
5. Verify tabs (About, Members, Documents, Location) all work

**Test Case 6: Search & Filter**
1. On "Browse Groups" screen, tap search
2. Enter group name fragment
3. **Expected**: List filters to matching groups
4. Try province filter
5. **Expected**: List filters by province
6. Try group type filter
7. **Expected**: List filters by type (Burial Society, Stokvel, etc.)

**Test Case 7: Register New Group**
1. From home, tap "+ Create Group"
2. **Step 1 (Identity)**:
   - Enter group name: "Test Burial Society"
   - Select type: "Burial Society"
   - Enter description: "Test group for QA"
   - Tap "Continue"
3. **Step 2 (Location)**:
   - Select province: "Gauteng"
   - Enter city: "Johannesburg"
   - Enter township: "Soweto"
   - Tap "Continue"
4. **Step 3 (Finance)**:
   - Enter joining fee: "500"
   - Enter monthly contribution: "200"
   - Enter late fee: "50"
   - Max members: "50"
   - Tap "Continue"
5. **Step 4 (Admin Account)**:
   - Enter admin email: `admin@testgroup.com`
   - Enter password: `AdminPass123!`
   - Select bank: "FNB"
   - Enter account number: "1234567890"
   - Enter branch code: "250355"
   - Tap "Create Group & Pay Admin Fee"
6. **Expected**: Group created in Supabase, admin dashboard accessible

### Member Management
**Objective**: Verify member registration, document upload, probation

**Test Case 8: Join Group**
1. From group detail, tap "Join This Group"
2. **Registration Form**:
   - Full Name: "Test Member"
   - SA ID: "0010010000100" (valid checksum)
   - Phone: "0712345678"
   - Email: "member@example.com"
   - Notification preference: "Both"
   - Tap "Continue"
3. **Document Upload**:
   - Tap "📁 Choose File" for ID document
   - Select any PDF/image from device
   - Tap "✓ Uploaded"
   - Repeat for proof of residence
   - Tap "Complete Registration"
4. **Expected**: Member created in probation status, documents pending verification

**Test Case 9: Member Status Progression**
1. As admin, navigate to group
2. See member in "Probation" status
3. Tap member row
4. See probation end date, contributed amount
5. After probation ends (or manual update in Supabase):
   - Member status changes to "Active"
   - **Expected**: Email notification sent

### Payment Flow
**Objective**: Verify YoCo integration, contribution tracking

**Test Case 10: Make Contribution Payment**
1. Join group (Test Case 8)
2. Tap "Make Payment"
3. Select payment type: "Monthly Contribution"
4. Amount auto-fills: "200"
5. Tap "Pay with YoCo"
6. YoCo modal appears
7. Use test card: `4111 1111 1111 1111` (Visa)
8. Expiry: `12/25`
9. CVC: `123`
10. Tap "Pay"
11. **Expected**: 
    - Payment shows as "Processing"
    - Webhook received (check Supabase)
    - Status updates to "Completed"
    - Member total contributions increases

**Test Case 11: Payment History**
1. After payment (Test Case 10), tap "Contribution History"
2. See payment entries:
   - Joining Fee (paid)
   - Monthly Contributions (paid)
   - Due contributions (if any)
3. **Expected**: History shows all payment types with dates

**Test Case 12: Late Payment Notification**
1. As admin, update contribution due date to past
2. Member should receive:
   - FCM push notification (if app is open)
   - WhatsApp message (if enabled)
   - Email (always sent)
3. **Expected**: Notification arrives within 1 minute

### Notifications
**Objective**: Verify FCM, WhatsApp, Email delivery

**Test Case 13: Push Notification (FCM)**
1. Member joins group (Test Case 8)
2. Admin sends message: "Welcome to the group!"
3. **Expected**: 
   - If app is open: In-app notification appears
   - If app is closed: Push notification in system tray
   - Tap notification → app opens to relevant screen

**Test Case 14: WhatsApp Integration**
1. Member enables "WhatsApp" notification preference
2. Trigger event: Payment due
3. **Expected**: WhatsApp message received within 2 minutes
4. Message includes: group name, amount due, due date

**Test Case 15: Email Integration**
1. Any notification event triggered
2. **Expected**: Email received within 5 minutes
3. Email includes: group name, event details, action link

### Offline Mode
**Objective**: Verify offline-first caching and sync

**Test Case 16: Browse Offline**
1. Load group list while online
2. Turn off network
3. Navigate away and back to group list
4. **Expected**: Cached groups still visible (but marked as stale)
5. Turn network back on
6. Refresh list
7. **Expected**: Latest data from Supabase fetched

**Test Case 17: Offline Form Submission**
1. Turn off network
2. Try to join group
3. Fill form and submit
4. **Expected**: 
   - Error message: "No network connection"
   - Form data preserved
5. Turn network back on
6. Submit again
7. **Expected**: Form processes successfully

**Test Case 18: Offline Contribution History**
1. Load member's contribution history while online
2. Turn off network
3. Navigate to contribution history
4. **Expected**: Cached history still visible
5. Turn network back on, refresh
6. **Expected**: Latest data fetched

### Admin Dashboard
**Objective**: Verify fee tracking, analytics, enforcement

**Test Case 19: Fee Status Dashboard**
1. As admin, view group dashboard
2. See fee status card showing:
   - Current status: "DUE" / "PAID" / "WARNING" / "SUSPENDED"
   - Amount due: R10 × number of members
   - Payment deadline
3. Tap "Pay Platform Fee"
4. YoCo modal appears
5. Complete payment
6. **Expected**: Status updates to "PAID", next month countdown shows

**Test Case 20: Actuarial Metrics**
1. As admin, tap "Analytics"
2. See metrics dashboard:
   - Pure Premium: calculated
   - Gross Premium: calculated
   - Reserve Adequacy: % shown
   - Solvency Margin: % shown
   - Composite Risk Score: 0-100
3. **Expected**: All metrics calculate correctly based on members, contributions

**Test Case 21: Member Document Verification**
1. As admin, view "Member Documents"
2. See all pending documents
3. Tap document thumbnail to verify
4. Select: "✓ Verified" or "✗ Rejected"
5. If verified: Member status advances from probation
6. **Expected**: Email notification sent to member

### Edge Cases
**Objective**: Verify error handling and recovery

**Test Case 22: Network Timeout**
1. Simulate slow network (Android Settings → Developer Options → Network speed simulator)
2. Try to load groups
3. **Expected**: Spinner shows for up to 10 seconds, then timeout error with retry button

**Test Case 23: Malformed Input**
1. Try to register member with invalid SA ID: "abc1234567890"
2. **Expected**: Error message: "SA ID must contain only digits"
3. Try with non-matching probation months format
4. **Expected**: Validation prevents submission

**Test Case 24: Concurrent Operations**
1. Join group while another instance joins same group
2. **Expected**: 
   - Both registrations succeed
   - No data corruption
   - Both members appear in group

**Test Case 25: Session Expiry**
1. Sign in with valid credentials
2. Manually delete JWT from EncryptedSharedPreferences (Android Studio Device File Explorer)
3. Try to load data
4. **Expected**: 
   - Error detected
   - Automatic sign-out triggered
   - Sent to login screen

---

## 📊 Performance Testing

### Load Testing
**Objective**: Verify app stability with 500+ groups

```bash
# Load test script (Supabase PostgREST)
INSERT INTO groups (name, type, province, city, township) 
SELECT 
  'Test Group ' || generate_series(1, 500),
  array['burial_society', 'stokvel', 'rosca'][floor(random()*3)+1],
  'Gauteng',
  'Johannesburg',
  'Test Township'
FROM generate_series(1, 500);
```

**Expected**:
- Group list loads in < 2 seconds
- Scrolling smooth (60 FPS)
- No memory leaks (check in Android Profiler)

### Battery Testing
**Objective**: Verify battery drain over 1 hour of usage

**Test**:
1. Open app with 100% battery
2. Use for 1 hour: browse, join group, make payments
3. Check battery percentage
4. **Expected**: < 15% battery used

### Data Usage Testing
**Objective**: Verify minimal data consumption

**Test**:
1. Enable data usage tracking in Android Settings
2. Use app for 1 hour
3. Check data consumed
4. **Expected**: < 50 MB for typical usage

---

## 🔒 Security Testing

### Test Case 26: Secrets Not Exposed
1. Decompile APK: `apktool d app-debug.apk`
2. Search for hardcoded secrets:
   - SUPABASE_URL
   - API keys
   - Firebase config
3. **Expected**: All secrets injected via BuildConfig, not hardcoded

### Test Case 27: Token Security
1. Sign in
2. Extract JWT from EncryptedSharedPreferences
3. Attempt to use expired token
4. **Expected**: App auto-refreshes token silently

### Test Case 28: RLS Policy Enforcement
1. As member, try to access another member's data via Supabase directly
2. **Expected**: RLS blocks access (401 Unauthorized)

---

## ✅ Sign-Off Checklist

### Functional Testing
- [ ] All 25 test cases pass
- [ ] No crashes or ANRs
- [ ] UI responsive (no freezing)
- [ ] Offline mode works
- [ ] Online sync works

### Performance Testing
- [ ] 500+ groups load smoothly
- [ ] Battery usage acceptable
- [ ] Data usage minimal
- [ ] No memory leaks

### Security Testing
- [ ] No hardcoded secrets
- [ ] Tokens properly stored and refreshed
- [ ] RLS policies enforced
- [ ] No sensitive data in logs

### Compatibility Testing
- [ ] Min SDK 26 (Android 8.0) works
- [ ] Max SDK 35 (Android 15) works
- [ ] Tested on phones and tablets
- [ ] Tested on 2 different devices

---

## 📝 Regression Testing (Before Each Release)

Before deploying to production, run:

```bash
# Full test suite
./gradlew test connectedAndroidTest

# Build for release
./gradlew assembleRelease

# Check for lint warnings
./gradlew lint

# Performance baseline
./gradlew benchmarkDp
```

---

## 🎓 Testing Best Practices

1. **Test on real devices** - Emulator can hide issues
2. **Test on slow networks** - Simulate 3G/LTE
3. **Test offline scenarios** - Unplug network cable
4. **Test with real data** - 500+ groups, 10k+ members
5. **Test edge cases** - Invalid input, concurrent operations
6. **Test after updates** - Always run full suite after Gradle updates

---

## 📞 Troubleshooting

### Build Fails
```bash
# Clean everything
./gradlew clean

# Rebuild with verbose output
./gradlew assembleDebug --stacktrace
```

### APK Won't Install
```bash
# Uninstall previous version
adb uninstall com.sanibonani.save

# Reinstall
./gradlew installDebug
```

### Tests Fail
```bash
# Run with verbose output
./gradlew test --stacktrace

# Run specific test
./gradlew test --tests TestClassName
```

### App Crashes
1. Check logcat: `adb logcat | grep SanibonaniSave`
2. Check Firebase Crashlytics console
3. Review stack trace in Logcat
4. Check SHORTCOMINGS.md for known issues

---

**Status**: ✅ Ready for QA Testing  
**Next Step**: Execute test plan and document results  
**Support**: Reference AGENTS.md for architecture questions

