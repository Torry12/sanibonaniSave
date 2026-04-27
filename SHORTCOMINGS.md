# SanibonaniSave — Code Quality & Architecture Shortcomings Analysis

Generated: March 2026 | Based on codebase review of SanibonaniSave_Full

---

## 🔴 Critical Issues (Must Fix Before Production)

### 1. **Incomplete Actuarial Repository Implementation**
**Severity**: CRITICAL  
**File**: `ActuarialRepositoryImpl.kt`  
**Issue**: The `computeMetrics(groupId: String)` method is stubbed:
```kotlin
override suspend fun computeMetrics(groupId: String): Result<ActuarialMetrics> {
    return Result.failure(Exception("Not implemented for group ID"))
}
```
- **Impact**: AdminViewModel calls this method but always gets failure
- **Fix Required**: 
  - Fetch group + members + contributions from repository
  - Calculate APV (Actuarial Present Value) using actual historical claims
  - Call the synchronous version with real data
  - Cache results in Room for offline access

### 2. **Incomplete FCM Token Management**
**Severity**: CRITICAL  
**File**: `SanibonaniFirebaseService.kt` (line 28)  
**Issue**: `onNewToken()` has a TODO comment:
```kotlin
override fun onNewToken(token: String) {
    // TODO: POST token to Supabase members table for this user
}
```
- **Impact**: Push notifications won't work; tokens are never persisted
- **Fix Required**:
  - Inject MemberRepository or auth manager
  - Fetch current user ID from SupabaseManager
  - Update member's `fcm_token` field in Supabase
  - Handle case where user is not yet logged in (store in local preference)

### 3. **Missing Payment History Sync**
**Severity**: HIGH  
**File**: `Repositories.kt` - Missing `PaymentRepository` implementation  
**Issue**: PaymentRepository interface exists but implementation details are incomplete:
- No fetch of payment history for member dashboard display
- No reconciliation logic between Supabase and YoCo webhook records
- No duplicate transaction detection

**Fix Required**:
- Implement `fetchPaymentHistory(memberId: String): Flow<Result<List<Payment>>>`
- Add payment status reconciliation task
- Validate YoCo transaction IDs before recording

### 4. **No Input Validation in Key Operations**
**Severity**: HIGH  
**Areas**:
- `RegisterMemberViewModel.submit()` validates ID number length but not format
- `AdminViewModel.updateSetting()` casts without null checking:
  ```kotlin
  "joiningFee" -> s.settings.copy(joiningFee = value as String)
  ```
- Group creation accepts empty descriptions
- Bank account number not validated for actual account existence

**Fix Required**:
- Add regex validators for SA ID numbers (13 digits, specific checksum)
- Add null-safe casting with error states
- Validate bank account format per bank type (ABSA vs Capitec)
- Require non-empty group descriptions

---

## 🟠 High Priority Issues

### 5. **Insufficient Error Logging**
**Severity**: HIGH  
**Issue**: Exception handling exists but logging is minimal:
```kotlin
} catch (e: Exception) {
    emit(Result.failure(e))  // Error lost — no log, stack trace gone
}
```
**Impact**:
- Production errors undetectable without crash reporting
- Difficult to debug field issues
- Firebase Crashlytics won't automatically capture repository errors

**Fix Required**:
```kotlin
catch (e: Exception) {
    Log.e("GroupRepository", "Failed to fetch groups: ${e.message}", e)
    emit(Result.failure(e))
}
```

### 6. **No Offline Fallback for Repository Queries**
**Severity**: HIGH  
**File**: `Repositories.kt` - All implementations  
**Issue**: When Supabase fails, repositories don't fall back to Room cache:
```kotlin
override fun getPublicGroups(): Flow<Result<List<Group>>> = flow {
    try {
        val groups = supabase.postgrest["groups"].select().decodeList<Group>()
        db.groupDao().upsertGroups(groups.map { it.toEntity() })
        emit(Result.success(groups))
    } catch (e: Exception) {
        emit(Result.failure(e))  // ❌ Cache is ignored on failure
    }
}
```

**Fix Required**:
```kotlin
catch (e: Exception) {
    val cachedGroups = db.groupDao().getPublicGroups().firstOrNull()
    if (cachedGroups != null) {
        emit(Result.success(cachedGroups.map { it.toModel() }))
    } else {
        emit(Result.failure(e))
    }
}
```

### 7. **State Machine Missing for Member Onboarding**
**Severity**: HIGH  
**Issue**: Member status transitions (PROBATION → ACTIVE) are manual and error-prone:
- No automated trigger when probation ends
- No validation that member has completed documents before probation ends
- No notification when transitioning to ACTIVE
- FeeEnforcementWorker doesn't process probation completions

**Fix Required**:
- Add scheduled job to check `probation_end_at` timestamps
- Validate document statuses BEFORE auto-promoting
- Create `ProbationCompletionWorker` alongside FeeEnforcementWorker

### 8. **Weak Bank Account Validation**
**Severity**: MEDIUM-HIGH  
**File**: `Models.kt` & `GroupSettings`  
**Issue**: Bank account fields stored without validation:
```kotlin
@SerialName("account_number")  val accountNumber: String = "",
@SerialName("branch_code")     val branchCode: String = "",
```
- No regex for account number format (varies by bank)
- Branch code not validated against real FNB/Nedbank/ABSA lists
- No balance verification before accepting payments

**Fix Required**:
- Validate account number format per bank type
- Fetch valid branch codes from Supabase lookup table
- Consider Supabase function to verify account via Payfast/Peach API

### 9. **Room Schema Version Mismatch Risk**
**Severity**: HIGH  
**File**: `SanibonaniDatabase.kt` (line 142)  
**Issue**: Using `.fallbackToDestructiveMigration()`:
```kotlin
.fallbackToDestructiveMigration()
.build()
```
- Development only — will nuke user data on schema updates
- No actual migrations defined for production schema changes
- No version increment strategy documented

**Fix Required**:
- Remove destructive migration fallback from production build
- Create `Migration_5_to_6.kt`, etc. for each schema change
- Document migration path in DEVELOPERS.md

### 10. **State Flow Recomposition Issues**
**Severity**: MEDIUM-HIGH  
**Files**: All screens  
**Issue**: ViewModels expose `StateFlow<UiState>` but screens may not be using `collectAsState()`:
```kotlin
// ❌ Not shown in screens, but potential pattern:
val state by viewModel.state.collect()  // causes constant recomposition
```

**Fix Required**:
- Audit all screen Composables for `collectAsState()` usage
- Add lint rule or ktlint custom rule to flag direct `.collect()` in Compose

---

## 🟡 Medium Priority Issues

### 11. **Missing Navigation State Restoration**
**Severity**: MEDIUM  
**File**: `NavGraph.kt`  
**Issue**: Deep links don't validate arguments exist before navigating:
```kotlin
data object GroupProfile : Screen("group/{groupId}") {
    fun withId(id: String) = "group/$id"
}
```
- No check that group actually exists before showing GroupProfileScreen
- Could show blank screen if groupId doesn't exist in Supabase
- User confusion on invalid deep links

**Fix Required**:
- Add `onAppear` LaunchedEffect in GroupProfileScreen to validate group exists
- Show error dialog + navigation back if group not found
- Log invalid navigation attempts

### 12. **Incomplete ViewModel State Cleanup**
**Severity**: MEDIUM  
**Issue**: ViewModels don't reset state on logout:
```kotlin
// In AuthViewModel.signOut():
// State should be cleared but code is missing
```
- Sensitive data (member email, group names) may persist in memory
- Re-login shows previous user's data briefly
- Battery drain from observing stale Flows

**Fix Required**:
- Add `onCleared()` to reset all MutableStateFlow instances
- Clear Room cache on logout
- Cancel active subscriptions in ViewModel destruction

### 13. **No Pagination for Large Lists**
**Severity**: MEDIUM  
**File**: `Repositories.kt` - `getGroupMembers()`, `observeContributions()`  
**Issue**: Fetches entire list without limit:
```kotlin
val members = supabase.postgrest["members"]
    .select { filter { eq("group_id", groupId) } }  // No limit(100) or range()
    .decodeList<Member>()
```
- Groups with 1000+ members cause OOM on UI thread
- Network payload ballooning
- No infinite scroll or lazy loading

**Fix Required**:
- Add `.range(0, 100)` to initial queries
- Implement `loadMoreMembers()` function
- Use LazyColumn with itemsIndexed for efficient rendering

### 14. **Missing Retry Logic for Network Operations**
**Severity**: MEDIUM  
**File**: `Repositories.kt`  
**Issue**: Single attempt on network failure:
```kotlin
val groups = supabase.postgrest["groups"].select().decodeList<Group>()
// ❌ If Supabase is flaky, immediate failure
```
**Fix Required**:
- Add exponential backoff retry using `retry(3)` from Supabase SDK
- Or wrap in custom retry logic with jitter

### 15. **Actuarial Calculations Hardcoded**
**Severity**: MEDIUM  
**File**: `ActuarialRepositoryImpl.kt`  
**Issue**: Mortality rate and safety loading are test constants:
```kotlin
fun computeMetrics(
    members: Int,
    balance: Double,
    mortalityRatePct: Double,        // Passed in, not fetched from DB
    avgClaim: Double,                // Passed in, not fetched
    safetyLoadingPct: Double,        // Passed in, not calculated
    ...
)
```
- No connection to actual group claim history
- Safety loading not based on solvency margin
- Can't be called from AdminDashboardScreen without complex setup

**Fix Required**:
- Fetch group's historical claims from contributions table
- Calculate mortality from actual member claims
- Compute safety loading based on funding ratio

---

## 🟡 Medium-Low Priority Issues

### 16. **Missing Constants/Magic Numbers**
**Severity**: MEDIUM-LOW  
**Examples**:
- `memberCount * 10.0` hardcoded platform fee in `Group.kt`
- "28" as payment due day magic number in schema
- "3" months probation hardcoded in multiple places
- "6" for reserve adequacy multiplier in `ActuarialRepositoryImpl`

**Fix Required**:
```kotlin
// Create Constants.kt
object PlatformFees {
    const val MONTHLY_PER_MEMBER = 10.0
    const val REGISTRATION = 700.0
}
object GroupDefaults {
    const val PROBATION_MONTHS = 3
    const val PAYMENT_DUE_DAY = 28
}
```

### 17. **Incomplete Supabase Schema Documentation**
**Severity**: MEDIUM-LOW  
**File**: `supabase/schema.sql`  
**Issue**:
- No RLS policy documentation in code comments
- No explanation of fee_status enum values and transitions
- No trigger documentation (e.g., auto-update updated_at)

**Fix Required**:
- Add SQL comments for each table and policy
- Document state machine for fee_status (DUE → WARNING → SUSPENDED → PAID)

### 18. **Missing Notification Preference Validation**
**Severity**: MEDIUM-LOW  
**File**: `NotificationRepository`  
**Issue**: Member notification_pref is stored but never validated:
- WhatsApp selected but no phone number
- Email selected but invalid email format
- Both selected but both invalid

**Fix Required**:
- Add validation in `sendNotification()` to check preferences
- Mark notification as failed if channel unavailable
- Notify admin if member's preferred channel can't be reached

### 19. **Test Coverage Extremely Low**
**Severity**: MEDIUM-LOW  
**Files**: `app/src/test/java/com/sanibonani/save/`  
**Status**:
- Only 2 test files: `SupabaseConnectionTest.kt` (mock only) + `ActuarialRepositoryTest.kt` (8 tests)
- No ViewModel tests
- No Repository tests
- No UI tests
- No integration tests

**Coverage**: <5% of codebase  
**Fix Required**:
- Add ViewModel state transition tests (AuthViewModel, AdminViewModel)
- Add Repository tests with mocked Supabase client
- Add integration tests for user journeys (register → join → contribute → claim)

### 20. **Missing Feature Flags**
**Severity**: MEDIUM-LOW  
**Issue**: No feature toggles for:
- Disabling payment processing in dev
- Toggling WhatsApp notifications on/off
- A/B testing different fee schedules
- Rolling out new actuarial functions gradually

**Fix Required**:
- Add Supabase `feature_flags` table
- Create `FeatureFlagRepository` to fetch flags on app start
- Wrap optional features with flag checks

---

## 🔵 Low Priority Issues (Quality of Life)

### 21. **Inconsistent Error Messages**
**Severity**: LOW  
**Examples**:
- Some errors: "Please provide a valid name and 13-digit ID."
- Others: "e.message" (often technical: "Unknown error 404")
- Inconsistent capitalization and tone

**Fix Required**: Create centralized error message mapper

### 22. **No Accessibility (a11y) Support**
**Severity**: LOW  
**Issue**:
- No contentDescription on icons/images
- No semantic labels on buttons
- Color alone conveys status (red = error) — not WCAG compliant

**Fix Required**:
- Add Compose semantics to critical components
- Ensure color + text/icon conveys meaning

### 23. **Missing Data Export Feature**
**Severity**: LOW  
**Issue**: No way for admins to export member lists or contribution history as CSV/PDF

**Fix Required**: Add export function using Supabase Functions to generate CSV

### 24. **No Localization**
**Severity**: LOW  
**Issue**: All strings hardcoded in English; no support for isiZulu, Afrikaans, or other SA languages

**Fix Required**: Add `strings-zu.xml`, `strings-af.xml` resources

### 25. **Missing Analytics**
**Severity**: LOW  
**Issue**: Firebase Analytics imported but never used
- No tracking of user actions
- No funnel analysis for signup → join → contribute
- No error rate monitoring

**Fix Required**:
```kotlin
FirebaseAnalytics.getInstance(context).logEvent("group_created") {
    param(FirebaseAnalytics.Param.GROUP_ID, groupId)
    param("member_count", memberCount)
}
```

---

## 📊 Summary by Severity

| Severity | Count | Examples |
|----------|-------|----------|
| 🔴 CRITICAL | 4 | Actuarial stub, FCM tokens, payment sync, input validation |
| 🟠 HIGH | 6 | Error logging, offline fallback, member state machine, bank validation, schema migration, recomposition |
| 🟡 MEDIUM | 9 | Navigation validation, state cleanup, pagination, retry logic, hardcoded calculations, constants, schema docs, notification validation, test coverage |
| 🟡 MEDIUM-LOW | 2 | Feature flags, magic numbers |
| 🔵 LOW | 5 | Error messages, a11y, exports, localization, analytics |

**Total Issues: 26**

---

## 🎯 Recommended Fix Order

1. **Week 1 (Critical)**: Actuarial metrics, FCM tokens, payment sync, input validation
2. **Week 2 (High)**: Error logging, offline fallback, member state machine, Room migrations
3. **Week 3 (Medium)**: Pagination, retry logic, test suite (at least ViewModels)
4. **Week 4+**: Constants, a11y, analytics, localization

---

## 📝 Developer Workflow Improvements

### Missing Documentation
- No `DEVELOPERS.md` with local setup instructions
- No `ARCHITECTURE.md` beyond AGENTS.md
- No `API.md` documenting Supabase table schemas for frontend devs
- No `.env.example` for required Supabase functions

### Missing CI/CD
- No GitHub Actions workflow for:
  - Lint checks (ktlint)
  - Unit test runs
  - Build verification
  - Automated APK generation for testing

### Missing Debugging Tools
- No Logcat filtering script for development
- No Network inspector for Supabase/YoCo calls
- No Room database viewer for offline cache inspection

---

*This analysis is based on static code review. Runtime issues may exist that require integration testing.*

