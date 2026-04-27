# Registration Flow Visualization

## Before vs After Comparison

### BEFORE: Direct Dashboard Redirect
```
┌─────────────────────────────────────────────────┐
│ User Registration                               │
├─────────────────────────────────────────────────┤
│ 1. Fill form (Name, Email, Password)            │
│ 2. Click "Create Account"                       │
│ 3. Account created ✅                           │
│ 4. AUTO-REDIRECT → Member Dashboard             │ ❌ Issues:
│                                                  │  - No time to explore
│ 5. User is immediately in dashboard             │  - Confusing for new users
│    (no chance to explore platform)              │  - No landing page context
└─────────────────────────────────────────────────┘
```

### AFTER: Landing Page First
```
┌─────────────────────────────────────────────────┐
│ User Registration                               │
├─────────────────────────────────────────────────┤
│ 1. Fill form (Name, Email, Password)            │
│ 2. Click "Create Account"                       │
│ 3. Account created ✅                           │
│ 4. REDIRECT → Landing Page                      │ ✅ Benefits:
│                                                  │  - Controlled onboarding
│ 5. From Landing, user can:                      │  - Can explore groups
│    - Browse existing groups                     │  - Can read about platform
│    - Create new group                           │  - Can go to dashboard
│    - Learn about platform                       │  - OR logout and login fresh
└─────────────────────────────────────────────────┘
```

---

## Form Protection Flow

### Back Button Behavior

```
┌──────────────────────────────────────────────────────────────────┐
│ REGISTRATION FORM STATE TRACKING                                 │
├──────────────────────────────────────────────────────────────────┤
│                                                                   │
│ allFieldsFilled = (                                              │
│    fullName.length >= 3 AND                                      │
│    email.isNotBlank() AND                                        │
│    password.isNotBlank() AND                                     │
│    confirmPw.isNotBlank() AND                                    │
│    password == confirmPw                                         │
│ )                                                                 │
│                                                                   │
└──────────────────────────────────────────────────────────────────┘
                            ↓
        ┌───────────────────────────────────────┐
        │   allFieldsFilled = FALSE             │
        ├───────────────────────────────────────┤
        │                                        │
        │ Back Button: ❌ DISABLED               │
        │ Warning Message: ⚠️ VISIBLE           │
        │ "Please fill in all required fields"  │
        │                                        │
        │ User cannot:                          │
        │ ❌ Leave form                         │
        │ ❌ Navigate back                      │
        │ ❌ Pop stack                          │
        └───────────────────────────────────────┘
                            ↓
                  [User fills form]
                            ↓
        ┌───────────────────────────────────────┐
        │   allFieldsFilled = TRUE              │
        ├───────────────────────────────────────┤
        │                                        │
        │ Back Button: ✅ ENABLED                │
        │ Warning Message: ❌ HIDDEN            │
        │                                        │
        │ User can:                             │
        │ ✅ Leave form                         │
        │ ✅ Navigate back to Login             │
        │ ✅ Pop stack                          │
        │ ✅ Click "Create Account"            │
        └───────────────────────────────────────┘
```

---

## Navigation Logic State Machine

```
                        ┌─────────────────────┐
                        │   App Launched      │
                        └──────────┬──────────┘
                                   │
                    ┌──────────────┴──────────────┐
                    │                             │
            ┌───────▼───────┐          ┌──────────▼────────┐
            │  isLoggedIn?   │          │  User navigates  │
            │                │          │  to Register     │
            └────┬─────┬────┘          └──────────┬────────┘
                 │     │                          │
              NO │     │ YES              ┌───────▼────────┐
                 │     │                  │ RegisterScreen │
                 │     │                  │ (form shown)   │
                 │     │                  └───────┬────────┘
                 │     │                          │
                 │     │              [User fills & submits]
                 │     │                          │
                 │     │                  ┌───────▼──────────┐
                 │     │                  │ signUp() called  │
                 │     │                  │ isNewReg=true ✅ │
                 │     │                  └───────┬──────────┘
                 │     │                          │
                 │     │            ┌─────────────▼──────────────┐
                 │     │            │ LaunchedEffect detects:    │
                 │     │            │ - isLoggedIn = true        │
                 │     │            │ - isNewRegistration = true │
                 │     │            └─────────────┬──────────────┘
                 │     │                          │
                 │     │            ┌─────────────▼──────────────┐
                 │     │            │ Navigate to Landing Page   │
                 │     │            │ clearNewRegistrationFlag() │
                 │     │            └─────────────┬──────────────┘
                 │     │                          │
                 │     │                  ┌───────▼────────┐
                 │     │                  │ Landing Screen │
                 │     │                  │ (User can now  │
                 │     │                  │ explore or go  │
                 │     │                  │ to dashboard)  │
                 │     │                  └────────────────┘
                 │     │
                 │     └────────────────────────────┐
                 │                                  │
        ┌────────▼─────────┐          ┌────────────▼────┐
        │ Landing/Login    │          │ Member Dashboard│
        │ (or other public │          │ (via isLoggedIn)│
        │  screens)        │          └─────────────────┘
        └──────────────────┘

                Key Difference:
        ┌──────────────────────────────────────────┐
        │ LOGIN: isNewRegistration = FALSE         │
        │ → Auto-redirect to Dashboard             │
        │                                          │
        │ REGISTRATION: isNewRegistration = TRUE   │
        │ → Redirect to Landing Page               │
        └──────────────────────────────────────────┘
```

---

## Field Validation Timeline

```
┌─────────────────────────────────────────────────────────────────┐
│ REAL-TIME VALIDATION AS USER TYPES                              │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│ Field 1: Full Name                                              │
│ ─────────────────────────────────────────                        │
│ User Types: "J"      → ❌ Too short (needs 3+)                  │
│ User Types: "Jo"     → ❌ Too short                              │
│ User Types: "John"   → ✅ Valid (4 chars)                       │
│                                                                  │
│ Field 2: Email                                                  │
│ ─────────────────────────────────────────                        │
│ User Types: ""       → ❌ Empty                                 │
│ User Types: "john@"  → ✅ Not blank (format checked by backend) │
│                                                                  │
│ Field 3: Password                                               │
│ ─────────────────────────────────────────                        │
│ User Types: ""       → ❌ Empty                                 │
│ User Types: "pass"   → ✅ Not blank                             │
│                                                                  │
│ Field 4: Confirm Password                                       │
│ ─────────────────────────────────────────                        │
│ User Types: ""       → ❌ Empty                                 │
│ User Types: "pass"   → ✅ Matches password                      │
│ User Types: "Pass"   → ❌ Doesn't match (case-sensitive)        │
│                                                                  │
│ Back Button State:                                              │
│ ─────────────────────────────────────────                        │
│ Until ALL fields valid → ❌ DISABLED + ⚠️ WARNING              │
│ When ALL fields valid  → ✅ ENABLED                             │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## Code Flow: Registration to Landing Page

```
USER ACTION: Clicks "Create Account"
    ↓
AuthViewModel.signUp()
    ├─ Validates all fields locally
    ├─ Calls supabaseRepo.signUp()
    └─ On Success: 
        _state.update { it.copy(isLoading = false, isNewRegistration = true) }
    ↓
NavGraph LaunchedEffect Triggered
    ├─ Detects: authState.isLoggedIn = true
    ├─ Detects: authState.isNewRegistration = true
    └─ Detects: currentRoute = "register"
    ↓
Navigation Decision
    ├─ Check: authState.navigateTo != "login" && !authState.isNewRegistration
    │   → FALSE (because isNewRegistration = true)
    │
    └─ Check: authState.isNewRegistration && currentRoute == "register"
        → TRUE (both conditions met)
    ↓
Execute Navigation
    ├─ navController.navigate(Screen.Landing.route)
    ├─ popUpTo(0) { inclusive = true } (clear entire stack)
    └─ authViewModel.clearNewRegistrationFlag()
    ↓
Landing Screen Displays
    └─ User can explore groups, create groups, or navigate to dashboard
```

---

## State Transitions Diagram

```
                    ┌──────────────────────┐
                    │  Initial State       │
                    │ isNewReg: false      │
                    │ isLoggedIn: false    │
                    └──────────┬───────────┘
                               │
                    [Form filled & submitted]
                               │
                    ┌──────────▼───────────┐
                    │  Registration State  │
                    │ isNewReg: true  ✅   │
                    │ isLoggedIn: true  ✅ │
                    └──────────┬───────────┘
                               │
                  [Navigation to Landing Page]
                               │
                    ┌──────────▼───────────────┐
                    │  Landing Page State      │
                    │ isNewReg: false (cleared)│
                    │ isLoggedIn: true         │
                    └──────────┬───────────────┘
                               │
              ┌────────────────┼────────────────┐
              │                │                │
        [Browse]         [Create Group]  [View Dashboard]
              │                │                │
              ↓                ↓                ↓
        ┌──────────┐   ┌─────────────┐  ┌──────────────┐
        │ Browse   │   │ CreateGroup │  │ Dashboard    │
        │ Groups   │   │ Screen      │  │ (Member/    │
        │ Screen   │   │             │  │  Admin)      │
        └──────────┘   └─────────────┘  └──────────────┘

                        ✅ Fresh Start
                   User has full platform access
                   No auto-redirect confusion
```

---

## Comparison: Login vs Registration Navigation

```
┌─────────────────────────────────────────────────────────────┐
│ LOGIN FLOW (Existing)                                       │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│ 1. User provides email + password                          │
│ 2. signIn() called                                         │
│ 3. isNewRegistration = FALSE (default)                    │
│ 4. isLoggedIn = TRUE                                       │
│                                                             │
│ Navigation Decision:                                       │
│ if (isNewRegistration) → Landing   ❌ FALSE               │
│ else → Dashboard        ✅ TRUE                            │
│                                                             │
│ Result: IMMEDIATE redirect to Member/Admin Dashboard       │
│                                                             │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│ REGISTRATION FLOW (NEW)                                     │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│ 1. User fills registration form                            │
│ 2. Form validation prevents early exit                     │
│ 3. signUp() called                                         │
│ 4. isNewRegistration = TRUE  ← FLAG SET ✅                │
│ 5. isLoggedIn = TRUE                                       │
│                                                             │
│ Navigation Decision:                                       │
│ if (isNewRegistration) → Landing  ✅ TRUE                │
│ else → Dashboard         ❌ FALSE                          │
│                                                             │
│ Result: Redirect to Landing Page                           │
│ Then: clearNewRegistrationFlag() → isNewRegistration=FALSE │
│                                                             │
│ Next login: Back to normal login flow ↑                    │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```


