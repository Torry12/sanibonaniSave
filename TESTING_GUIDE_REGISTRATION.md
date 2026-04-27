# Quick Reference: Registration Feature Testing

## 🧪 Test Scenarios

### ✅ Test 1: Happy Path - New Registration
**Objective**: Verify new members land on Landing page after registration

**Steps**:
1. Launch app
2. Tap "Register" button
3. Fill form with valid data:
   - Full Name: "John Doe"
   - Email: "john@example.com"
   - Password: "Test123!"
   - Confirm: "Test123!"
4. Tap "Create Account" button
5. Wait for success toast

**Expected Result**: ✅
- Toast shows: "Account created successfully!"
- User is redirected to **Landing Page** (NOT Member Dashboard)
- Can see "Browse Savings Groups", "Create Group" buttons
- CAN log out from landing if needed

---

### ✅ Test 2: Form Protection - Incomplete Fields
**Objective**: Verify back button is disabled with incomplete form

**Steps**:
1. Launch app → Tap "Register"
2. Leave all fields empty
3. Tap back button (arrow in top bar)
4. Observe behavior

**Expected Result**: ✅
- Back button does NOT respond
- Warning message appears: "⚠️ Please fill in all required fields before leaving this form"
- Cannot navigate back

**Variation A - Partial Fill**:
1. Fill Name: "Jo" (only 2 chars)
2. Leave Email, Password empty
3. Tap back button

**Expected**: ❌ Back button still disabled (Name too short)

**Variation B - Password Mismatch**:
1. Fill all fields correctly
2. Change Confirm Password to something different
3. Tap back button

**Expected**: ❌ Back button still disabled (Passwords don't match)

---

### ✅ Test 3: Form Unlock After Completion
**Objective**: Verify back button enables after all fields valid

**Steps**:
1. Launch app → Tap "Register"
2. Fill Name: "John" ✅
3. Fill Email: "john@example.com" ✅
4. Fill Password: "Test123!" ✅
5. Fill Confirm Password: "Test123!" ✅
6. Observe back button

**Expected Result**: ✅
- Warning message DISAPPEARS
- Back button becomes ENABLED (can tap)
- Can now navigate back to Login screen

---

### ✅ Test 4: Existing Login Still Works Correctly
**Objective**: Verify login flow NOT affected (still goes to dashboard)

**Steps**:
1. Use credentials from Test 1 (just registered)
2. From Landing page, tap "Log In"
3. Enter email & password from Test 1
4. Tap "Log In" button

**Expected Result**: ✅
- User is logged in
- User is redirected to **Member Dashboard** (NOT Landing page)
- Previous behavior preserved

---

### ✅ Test 5: Field Validation in Real-Time
**Objective**: Verify validation updates as user types

**Steps**:
1. Launch app → Tap "Register"
2. Type in Name field one character at a time: "J" → "Jo" → "Joh" → "John"
3. Type in Email: "john@"
4. Type in Password: "test"
5. Type in Confirm Password: First "tes" then "test"
6. Monitor Create Account button

**Expected Result**: ✅
- Button stays disabled until ALL conditions met:
  - Name length ≥ 3
  - Email not blank
  - Password not blank
  - Confirm password matches
- As soon as all valid → button enables
- Back button behavior tracks same conditions

---

### ✅ Test 6: Error Handling Still Works
**Objective**: Verify registration errors don't break the flow

**Steps**:
1. Launch app → Tap "Register"
2. Fill form with valid data
3. BUT use an email that already exists in system
4. Tap "Create Account"

**Expected Result**: ✅
- Error toast appears: "User already registered" (or similar)
- User remains on Register screen
- Form is NOT cleared
- User can:
  - Try different email
  - Go back (now that we understand behavior)
  - Fix and resubmit

---

### ✅ Test 7: Platform Admin Registration (if applicable)
**Objective**: Verify platform admin roles still work

**Steps**:
1. Follow registration flow
2. After account created, verify user role is "member" (default)
3. Check if subsequent logins show appropriate dashboard

**Expected Result**: ✅
- Registration creates member role (correct default)
- Login redirects to Member Dashboard
- Can access group-related features

---

## 📋 Validation Checklist

| Scenario | Before | After | Status |
|----------|--------|-------|--------|
| **Registration Flow** |
| New user lands on | Dashboard | Landing Page | ✅ |
| Back button when incomplete | Enabled | Disabled | ✅ |
| Warning message | None | Shows warning | ✅ |
| **Login Flow** |
| Existing user lands on | Dashboard | Dashboard | ✅ (unchanged) |
| **Navigation** |
| Can explore after register | No | Yes | ✅ |
| Can return to form before submit | N/A | Cannot (protected) | ✅ |
| Form stays intact if user tries back | N/A | Yes | ✅ |

---

## 🐛 Troubleshooting

### Issue: Back button still works with incomplete form
**Possible Cause**: State not updating properly
**Solution**: Verify AuthViewModel has `isNewRegistration` field added

### Issue: User stuck on register screen after account creation
**Possible Cause**: Navigation flag not clearing
**Solution**: Check `clearNewRegistrationFlag()` is called in NavGraph

### Issue: User goes to dashboard instead of landing
**Possible Cause**: `isNewRegistration` flag not set to true
**Solution**: Verify `signUp()` sets `isNewRegistration = true` on success

### Issue: Warning message doesn't appear
**Possible Cause**: InfoBox component not working
**Solution**: Verify `InfoType.WARNING` exists in component definition

### Issue: Cannot tap Create Account button
**Possible Cause**: Validation logic too strict
**Solution**: Debug with breakpoint in `allFieldsFilled` computation

---

## 🔧 Debug Tips

### Check State Values
Add to RegisterScreen temporarily:
```kotlin
LaunchedEffect(Unit) {
    Log.d("RegForm", "fullName: ${state.fullName}")
    Log.d("RegForm", "email: ${state.email}")
    Log.d("RegForm", "password: ${state.password}")
    Log.d("RegForm", "confirmPw: ${state.confirmPw}")
    Log.d("RegForm", "allFieldsFilled: $allFieldsFilled")
}
```

### Check Navigation Flag
Add to NavGraph temporarily:
```kotlin
LaunchedEffect(authState.isNewRegistration) {
    Log.d("NavGraph", "isNewRegistration: ${authState.isNewRegistration}")
    Log.d("NavGraph", "currentRoute: ${navController.currentDestination?.route}")
}
```

### Monitor Back Button Clicks
Add to RegisterScreen temporarily:
```kotlin
val onBackDebug = { 
    Log.d("RegForm", "Back clicked - allFieldsFilled: $allFieldsFilled")
    if (allFieldsFilled) onBack() 
}
```

---

## ✨ Expected User Experience

**New User Journey**:
```
1. User sees Landing page
   ↓
2. Interested → Taps "Create Account"
   ↓
3. Fills registration form
   ├─ Incomplete? → Back button disabled ⛔
   └─ Complete? → Back button works ✅
   ↓
4. Submits form → Account created ✅
   ↓
5. Redirected to Landing Page (not dashboard!)
   ↓
6. Can explore:
   ├─ Browse groups
   ├─ Create group
   └─ View profile
   ↓
7. When ready → Access Member Dashboard
   Or → Log out and re-login fresh
```

---

## 📱 Device Testing

- **Minimum API Level**: 28 (no changes needed)
- **Target API Level**: 35 (no compatibility issues)
- **Recommended Test Devices**:
  - Physical: Pixel 6a+ (Android 13+)
  - Emulator: Pixel 4 API 33

---

## 🚀 Rollout Checklist

- [ ] Build APK successfully
- [ ] Run all 7 test scenarios
- [ ] Verify no crashes or exceptions
- [ ] Test on minimum API level device
- [ ] Test back button behavior
- [ ] Test navigation flow
- [ ] Verify error handling
- [ ] Check analytics (if enabled)
- [ ] Ready for release


