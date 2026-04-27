# Complete Testing Guide — SanibonaniSave

**Date**: April 1, 2026  
**Status**: Ready for QA  
**Critical Fixes Applied**: ✅ Enum serialization bugs

---

## 🧪 **UNIT TEST EXECUTION**

### Test Setup
```bash
# From project root:
cd C:\Users\CRISS\AndroidStudioProjects\SanibonaniSave_Full

# Run all unit tests
./gradlew test

# Run specific test class
./gradlew test --tests AuthViewModelTest

# Run with coverage report
./gradlew testDebugUnitTest --no-daemon
```

### Test Files to Review
```
app/src/test/java/com/sanibonani/save/
├── AuthViewModelTest.kt          ← Auth flow validation
├── GroupRepositoryTest.kt        ← Group CRUD + sync
├── MemberRepositoryTest.kt       ← Member CRUD + status changes
├── PaymentRepositoryTest.kt      ← Payment recording
├── ActuarialRepositoryTest.kt    ← Metrics calculation
└── GeoapifyServiceTest.kt        ← Address autocomplete
```

---

## 📱 **MANUAL TESTING WORKFLOW**

### Phase 1: Installation & Initial Setup (10 min)

#### 1.1 Build & Install
```bash
# In Android Studio:
1. File → Project Structure → Check Java 17 configured
2. Build → Make Project (verify no errors)
3. Run → Select emulator or device
4. Wait for app installation

# Expected: Splash screen + connection check
```

#### 1.2 First Launch Verification
```
✓ Splash screen shows "SanibonaniSave" + "Connecting..."
✓ After ~1-2 seconds, redirects to Landing page
✓ No crashes or error dialogs
✓ Landing page displays: Login, Register, Browse Groups buttons
```

---

### Phase 2: Authentication Flow (15 min)

#### 2.1 Sign Up New Account
```
Landing → Register button → Fill form:
  Email: testuser@sanibonani.test
  Password: TestPass123!
  Confirm: TestPass123!
→ Submit

Expected:
✓ "Creating account..." loading state
✓ JWT stored in EncryptedSharedPreferences
✓ Auto-redirect to Member Dashboard
✓ "Welcome!" message appears
```

#### 2.2 Session Persistence
```
Member Dashboard → Device home button (minimize app)
Wait 5 seconds → Swipe app back up

Expected:
✓ Session restored (no re-login required)
✓ Dashboard data displayed
✓ No "Connecting..." splash
```

#### 2.3 Logout & Re-Login
```
Member Dashboard → Settings/Menu → Logout
→ Confirm logout
→ Back at Landing page
→ Click Login
→ Enter credentials
→ Submit

Expected:
✓ JWT cleared from storage
✓ New JWT obtained on login
✓ Auto-redirect to Dashboard
```

#### 2.4 Sign Up As Group Admin
```
Register → Enter admin details:
  Email: admin@groupname.test
  Password: AdminPass123!
→ Submit

Expected:
✓ Account created
✓ Redirect to Group Creation page
✓ Admin automatically onboarded to group as ACTIVE member
```

---

### Phase 3: Group Management (20 min)

#### 3.1 Create Group
```
Member Dashboard → "Create Group" button
Fill form:
  Name: Test Burial Society
  Type: Burial Society
  Province: KwaZulu-Natal
  City: Durban
  Township: Lamontville
  Description: Community savings group
  Joining Fee: 200
  Monthly Contribution: 500
  Late Fee: 50
→ "Create Group" submit

Expected:
✓ Group created with ID
✓ Fee status: PENDING_ACTIVATION
✓ Platform fee record initialized
✓ Admin auto-registered as ACTIVE member
✓ Redirect to admin dashboard
```

#### 3.2 Group Settings Update
```
Admin Dashboard → Settings tab
Change:
  Monthly Contribution: 600
  Max Members: 75
→ Save

Expected:
✓ Changes saved to Supabase
✓ Changes reflected in Room cache
✓ No error dialogs
```

#### 3.3 Browse Public Groups (as different user)
```
Login as different user
→ Browse Groups tab
→ Search for "Test Burial"

Expected:
✓ Created group appears in list
✓ Shows: Name, Type, Member Count, Location
✓ Click group → Group Profile page
```

#### 3.4 View Group Details
```
Group Profile page shows:
  ✓ Group name, type, location
  ✓ Member count (1 after creation)
  ✓ Joining fee, monthly contribution
  ✓ Current balance
  ✓ Fee status
  ✓ Members list (initially just admin)
  ✓ "Join Group" button (for non-members)
```

---

### Phase 4: Member Management (25 min)

#### 4.1 Join Group
```
(As different user) Group Profile → "Join Group" button
→ Fill member registration form:
  Full Name: John Dlamini
  ID Number: 1234567890123
  Phone: 0712345678
  Email: john@example.com
  Notification Preference: WhatsApp
→ Submit

Expected:
✓ Member created with status = PROBATION
✓ probation_end_at = now + 3 months
✓ group.current_members incremented to 2
✓ Member sees: "Pending approval (probation period: 3 months)"
✓ Redirect to member dashboard
```

#### 4.2 Upload Documents
```
Member Dashboard → Profile → Documents
→ Upload two documents (ID copy, proof of address)
→ Each shows: "Pending verification"

Expected:
✓ document_1_status, document_2_status = PENDING
✓ Files stored in Supabase Storage
✓ URLs saved in member record
```

#### 4.3 Admin Reviews Members
```
(Switch to admin account)
Admin Dashboard → Members tab
→ See list of all members
→ Click member "John Dlamini"
→ View profile: documents, contribution history

Expected:
✓ Document preview available
✓ Status options visible: ACTIVE, SUSPENDED, PENDING_PAYMENT
✓ Update buttons functional
```

#### 4.4 Admin Verifies Documents
```
Admin → Member profile → Approve all documents
→ Confirm action

Expected:
✓ document_1_status, document_2_status = VERIFIED
✓ Notification sent to member
```

---

### Phase 5: Payment Flow (30 min)

#### 5.1 Joining Fee Payment
```
Member Dashboard → "Pay Joining Fee" button
→ Shows modal: $200 joining fee
→ YoCo payment widget appears
→ Use test card: 4242 4242 4242 4242
   Expiry: 12/25
   CVC: 123
→ Submit

Expected:
✓ Payment processing animation
✓ Success message: "Payment received"
✓ Payment recorded in Supabase
✓ Payment cached in Room
✓ Member sees payment in history
✓ Group balance increased by $200
✓ Notification sent (WhatsApp/Email/Push)
```

#### 5.2 Monthly Contribution
```
Member Dashboard → "Pay Contribution" button
→ Shows modal: $500 monthly contribution
→ YoCo payment widget
→ Submit with test card (above)

Expected:
✓ Payment successful
✓ Contribution marked as PAID
✓ Contribution visible in history
✓ Group balance increased by $500
✓ member.total_contributions incremented
```

#### 5.3 Payment History View
```
Member Dashboard → "Payment History" section
Shows table:
  Date | Amount | Type | Status | Method

Expected:
✓ Joining fee ($200) listed
✓ Contribution ($500) listed
✓ All marked as COMPLETED
✓ Filter options work (by type, date range)
```

#### 5.4 Group Payment History (Admin View)
```
Admin Dashboard → Payments tab
Shows all member payments for group

Expected:
✓ Joining fee from new member visible
✓ Contributions visible
✓ Group total balance updated (200 + 500 = $700)
✓ Sortable by date, member, amount
```

---

### Phase 6: Offline & Sync Testing (20 min)

#### 6.1 Offline Data Viewing
```
Member Dashboard (app running) → Turn off WiFi/Mobile data
→ Stay in app

Expected:
✓ Data still displays (from Room cache)
✓ Pull-to-refresh shows "Offline"
✓ All cached data readable
```

#### 6.2 Payment Attempt While Offline
```
While offline:
→ Try to "Pay Contribution" button

Expected:
✓ Either: queues payment for later
✓ Or: gracefully fails with "Check connection" message
✓ No crash, no data corruption
```

#### 6.3 Re-sync After Reconnect
```
Turn WiFi/Mobile back on
→ Pull to refresh

Expected:
✓ New data fetches from Supabase
✓ Merges with local cache
✓ No duplicate entries
✓ Timestamps consistent
✓ All changes reflected
```

---

### Phase 7: Notifications (15 min)

#### 7.1 FCM Token Setup
```
First app launch → Allow notifications permission
Check Firebase console:
  Device tokens registered

Expected:
✓ FCM token saved in member record
✓ Token visible in Firebase console
```

#### 7.2 Payment Notification
```
(From admin account) Make test payment
→ Wait 10 seconds

Expected:
✓ FCM notification received
✓ Contains: "Payment of $X received" + timestamp
✓ Click notification → Opens payment details
```

#### 7.3 Fee Due Notification
```
(Simulated by running FeeEnforcementWorker or manual trigger)
Wait for daily worker or trigger manually

Expected:
✓ Notification sent if fee_status = DUE
✓ Message: "Platform fee of $X is due"
```

---

### Phase 8: Enum Serialization Verification (10 min)

#### 8.1 Fee Status Updates
```
Admin Dashboard → Settings → Test status change
→ Trigger activateGroup() (activate platform fee)

Check Supabase dashboard:
  groups.fee_status → "paid" (lowercase)
  platform_fees.status → "paid" (lowercase)

Expected:
✓ Status in DB is lowercase (not "PAID")
✓ Enum serialized correctly via @SerialName
✓ No "invalid enum" errors in logs
```

#### 8.2 Member Status Updates
```
Admin → Member profile → Change status to SUSPENDED
→ Submit

Check Supabase:
  members.status → "suspended" (lowercase)

Expected:
✓ Lowercase in DB
✓ No serialization errors
✓ Member immediately can't pay contributions
```

---

### Phase 9: Actuarial Metrics (Admin Only) (15 min)

#### 9.1 View Metrics Dashboard
```
Admin Dashboard → Metrics tab

Expected:
✓ No crash
✓ Shows:
  - Member count
  - Risk score (0-100)
  - Average contribution rate
  - Expected payout amount
  - Premium calculation
```

#### 9.2 Metrics Accuracy
```
Create 5 test members with various contributions:
  Member 1: $500 paid
  Member 2: $500 paid, $200 partial
  Member 3: $0 paid
  Member 4: $500 paid
  Member 5: No contributions

Admin dashboard → Verify metrics:
  Total contributions = $1700
  Average rate = $1700/5 = $340
  Payment ratio = members with paid / total = 3/5 = 60%

Expected:
✓ Calculations match
✓ Risk score decreases with more contributions
```

---

### Phase 10: Error Handling & Edge Cases (15 min)

#### 10.1 Invalid Input Handling
```
Try creating group with:
  ✓ Empty name
  ✓ Negative fees
  ✓ Invalid email for admin
  
Expected:
✓ Form validation prevents submission
✓ Error messages appear near fields
✓ Submit button disabled until fixed
```

#### 10.2 Duplicate Email on Signup
```
Register with: test@example.com
→ Register again with same email

Expected:
✓ Friendly error: "Email already registered"
✓ Option to login instead
```

#### 10.3 Group Not Found
```
Manually navigate to: group/invalid_group_id
→ Via deep link or URL

Expected:
✓ "Group not found" message
✓ Back button to previous page
✓ No crash
```

#### 10.4 Network Timeout
```
Enable Airplane mode
→ Try any API call (load groups, make payment)

Expected:
✓ After 30 seconds: "Connection timeout"
✓ Retry button appears
✓ No spinner forever
```

---

### Phase 11: Performance Testing (10 min)

#### 11.1 Large Member List
```
Create group with 200+ members
→ Member list page

Expected:
✓ Page loads within 2 seconds
✓ Scrolling smooth (60 fps)
✓ No memory leaks
```

#### 11.2 Large Payment History
```
Member with 100+ payments
→ Payment history page

Expected:
✓ Initial load within 2 seconds
✓ Pagination works (50 per page)
✓ Smooth scrolling
```

#### 11.3 Large Groups List
```
Browse page with 500+ public groups
→ Search for "test"

Expected:
✓ Search returns results within 1 second
✓ Filter by province/type responsive
✓ No UI freezing
```

---

## ✅ **VERIFICATION CHECKLIST**

### Core Features
- [ ] Authentication (signup, login, logout, session persistence)
- [ ] Group creation & settings
- [ ] Member registration & probation
- [ ] Member status updates
- [ ] Document upload & verification
- [ ] Payment processing (joining fee, contribution)
- [ ] Payment history view
- [ ] Notifications (push, WhatsApp, email)
- [ ] Actuarial metrics display
- [ ] Offline data viewing
- [ ] Sync after reconnect

### Data Integrity
- [ ] Group current_members increments correctly
- [ ] Group balance updates on payment
- [ ] Member total_contributions increments
- [ ] fee_status serializes as lowercase
- [ ] Member status serializes as lowercase
- [ ] No duplicate records after sync
- [ ] Timestamps consistent

### Error Handling
- [ ] Invalid input rejected with messages
- [ ] Network errors gracefully handled
- [ ] Offline mode doesn't crash
- [ ] No sensitive data in logs
- [ ] Error messages user-friendly

### Performance
- [ ] App startup < 3 seconds
- [ ] Page load < 2 seconds
- [ ] Scrolling smooth (60 fps)
- [ ] Memory usage stable
- [ ] No memory leaks

### Security
- [ ] JWT stored in EncryptedSharedPreferences
- [ ] Auto-refresh of expired tokens
- [ ] No hardcoded credentials
- [ ] RLS policies enforced
- [ ] No sensitive data in debug logs

---

## 🐛 **BUG REPORT TEMPLATE**

When issues are found:

```markdown
### Bug Title
Brief description of issue

### Steps to Reproduce
1. ...
2. ...
3. ...

### Expected Behavior
What should happen

### Actual Behavior
What actually happens

### Device Info
- Device: Emulator/Physical
- Android Version: 
- App Version: 1.0.0

### Logs
```
[Paste relevant error logs]
```

### Screenshots
[Attach if applicable]

### Severity
[ ] Critical (App crash, data loss)
[ ] High (Feature broken, major inconvenience)
[ ] Medium (Minor feature issue)
[ ] Low (UI inconsistency, typo)
```

---

## 📊 **TEST RESULT SUMMARY**

After completing all phases, fill in:

| Phase | Test Cases | Passed | Failed | Notes |
|-------|-----------|--------|--------|-------|
| 1. Installation | 2 | ___ | ___ | |
| 2. Auth | 4 | ___ | ___ | |
| 3. Group Mgmt | 4 | ___ | ___ | |
| 4. Member Mgmt | 4 | ___ | ___ | |
| 5. Payment | 4 | ___ | ___ | |
| 6. Offline/Sync | 3 | ___ | ___ | |
| 7. Notifications | 3 | ___ | ___ | |
| 8. Enum Serialization | 2 | ___ | ___ | |
| 9. Actuarial Metrics | 2 | ___ | ___ | |
| 10. Error Handling | 4 | ___ | ___ | |
| 11. Performance | 3 | ___ | ___ | |
| **TOTAL** | **41** | ___ | ___ | |

**Overall Status**: [ ] PASS [ ] CONDITIONAL PASS [ ] FAIL

---

**Test Date**: __________  
**Tester Name**: __________  
**Signed Off**: __________

---

*Last Updated: April 1, 2026 — Complete testing guide with 40+ test cases*

