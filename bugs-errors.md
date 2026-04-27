# Bugs & Errors Log — SanibonaniSave

This document tracks identified crashes, logic errors, and their resolutions to ensure system stability.

---

## 🛑 Critical Crashes (Fixed)

### 1. **Document Download Button Null Pointer**
- **Location**: `SharedComponents.kt` -> `DocumentAdminCard`
- **Trigger**: Clicking the download button on an admin document card when the URL is null or has not yet synced from the database.
- **Error**: `KotlinNullPointerException` due to `url!!` force-unwrap.
- **Fix**: Replaced `url!!` with a safe call `url?.let { ... }` or an explicit null check before invoking `onDownload`.
- **Status**: ✅ FIXED

### 2. **CSV Export Storage Access Exception**
- **Location**: `ExportRepositoryImpl.kt` -> `exportPaymentsToCsv`
- **Trigger**: Attempting to export a group statement on devices where external storage (Documents directory) is unavailable or restricted.
- **Error**: `IllegalStateException` when `getExternalFilesDir` returns null.
- **Fix**: Implemented a fallback mechanism. If `getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)` is null, the app now uses `context.filesDir/documents` (internal storage), ensuring the export always succeeds.
- **Status**: ✅ FIXED

### 3. **Admin Dashboard "Empty State" Retry Crash**
- **Location**: `AdminViewModel.kt` -> `selectGroup`
- **Trigger**: Rapidly switching groups or retrying a group selection when the UI is in an error or empty state.
- **Error**: Race condition in flow collection leading to inconsistent state.
- **Fix**: Improved `selectGroup` logic to cancel existing observation jobs and correctly manage the `isLoading` and `error` states during transitions.
- **Status**: ✅ FIXED

### 4. **Register Screen / InfoBox Navigation Crash**
- **Location**: `SharedComponents.kt` -> `InfoBox`
- **Trigger**: Navigating to or from a screen (like `RegisterScreen`) that contains an `InfoBox` inside another scrollable container.
- **Error**: `IllegalStateException: Nesting verticalScroll and other scrolling components is not supported`.
- **Fix**: Removed `.verticalScroll(rememberScrollState())` from the `InfoBox` component. Layouts should manage scrolling at the screen level.
- **Status**: ✅ FIXED

### 5. **Admin Dashboard Verification Feedback**
- **Location**: `AdminDashboardScreen.kt`
- **Issue**: Administrative actions (verify, reject, payout) previously lacked immediate visual feedback (Toast/Snackbar) upon completion, leading to potential "double-clicking" by users.
- **Fix**: Integrated `SnackbarHost` and a `LaunchedEffect` to display success messages from the `AdminViewModel`.
- **Status**: ✅ FIXED

---

## ⚠️ Known Issues & Integration Failures

### 1. **Instrumented Test: Supabase `UnknownRestException`**
- **Location**: `BroadcastFormTest.kt` -> `testBroadcastMessageFunctionality`
- **Trigger**: Running instrumented tests that require user signup/authentication.
- **Error**: `io.github.jan.supabase.exceptions.UnknownRestException: Unknown Error` at `signup` URL.
- **Cause**: likely due to "Email Confirmation" being enabled in the Supabase dashboard (preventing test accounts from being immediately usable) or the API key lacking necessary permissions for the test environment.
- **Resolution**: Extended `SupabaseRepository` and `SupabaseManager` with `adminSignUp` to allow programmatic user creation with `autoConfirm = true` using `client.auth.admin.createUserWithEmail`. This allows tests to bypass the email verification flow.
- **Status**: ✅ FIXED

---

## 📉 Logic Defects

### 1. **Automatic Member Suspension on Rejection**
- **Defect**: When an admin rejected a document, the member remained "ACTIVE" or "PROBATION," potentially allowing continued participation despite invalid credentials.
- **Correction**: Integrated `UpdateMemberStatusUseCase` into the verification flow. Rejecting a document now automatically sets the member status to `SUSPENDED`.
- **Status**: ✅ FIXED

### 2. **Unsafe Force-Unwraps in Repositories**
- **Defect**: Critical paths in `MemberRepositoryImpl` and `GroupRepositoryImpl` used `!!` on nullable fields (like `member.id`), which could cause crashes if database sync was incomplete.
- **Correction**: Refactored `registerMember`, `recordContribution`, and `updateGroup` to use safe calls or throw descriptive `IllegalStateException`s.
- **Status**: ✅ FIXED

---

## 🔍 Investigation Backlog

- [ ] **Logcat Monitoring**: Track `IllegalStateException` from `ExportRepository` to identify if a fallback to internal storage or MediaStore is required for specific Android versions.
- [ ] **Supabase Auth Config**: Audit "Allow new users to sign up" and "Confirm email" settings to stabilize the CI/CD test suite.
- **Logic State Safety**: Conducted a broad audit of ViewModels (`MemberViewModel`, `AdminViewModel`, `PaymentViewModel`, `AuthViewModel`) and UseCases to replace unsafe force-unwraps (`!!`) and `getOrThrow()` calls with safe-calls, `getOrNull()`, or descriptive error handling. This prevents runtime crashes during intermittent network failures or partial data syncs.
- **Status**: ✅ FIXED
