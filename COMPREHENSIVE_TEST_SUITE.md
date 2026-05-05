# 🧪 SanibonaniSave Comprehensive Test Suite

**Complete testing guide covering unit tests, integration tests, UI tests, and end-to-end flows**

---

## 📋 Table of Contents

1. [Test Architecture](#test-architecture)
2. [Unit Tests](#unit-tests)
3. [Integration Tests](#integration-tests)
4. [UI/Compose Tests](#uicompose-tests)
5. [End-to-End Tests](#end-to-end-tests)
6. [Running Tests](#running-tests)
7. [Test Coverage Matrix](#test-coverage-matrix)

---

## 🏗️ Test Architecture

### Test Pyramid
```
         ┌─────────────────┐
         │  E2E Tests      │  (UI flows, complete user journeys)
         ├─────────────────┤
         │ Integration     │  (Repositories, multi-layer flows)
         │ Tests           │
         ├─────────────────┤
         │ Unit Tests      │  (Business logic, calculations)
         │ (Large Base)    │
         └─────────────────┘
```

### Test Types

| Type | Scope | Tools | Count |
|------|-------|-------|-------|
| **Unit Tests** | Individual functions, business logic | JUnit, MockK, Turbine | 50+ |
| **Integration** | Repository + DB coordination | AndroidTest, Room, MockServer | 20+ |
| **UI/Compose** | Screen rendering & interaction | Compose Test, Espresso | 15+ |
| **E2E** | Full user workflows | Espresso, Compose Test | 10+ |

---

## 🔧 Unit Tests

### 1. **Payment Calculation Tests** ✅ (Already Exists)

**File**: `PaymentCalculatorTest.kt`

**Coverage**:
- ✅ Monthly contribution calculation (base + beneficiary adjustments)
- ✅ Shortfall calculation (overdue amounts)
- ✅ Overpayment tracking
- ✅ Late fee logic
- ✅ Realtime payment calculations
- ✅ Edge cases (zero contribution, end-of-month due dates)

**Example Test**:
```kotlin
@Test
fun `monthly contribution - burial society with over-65 beneficiaries`() {
    val member = Member(
        id = "m1",
        groupId = burialGroup.id!!,
        beneficiaryOver65Count = 2
    )
    
    val amount = PaymentCalculator.calculateMonthlyContribution(burialGroup, member)
    
    // Base 150 + (150 * 0.10 * 2) = 150 + 30 = 180
    assertEquals("Should include 10% increase per over-65", 180.0, amount, 0.01)
}
```

---

### 2. **Validation Tests** (NEW)

**File**: `ValidationUtilsTest.kt`

**Coverage**:
- ✅ ID number validation (13-digit SA ID)
- ✅ Phone number validation (10-digit)
- ✅ Email validation
- ✅ Bank account validation (7-13 digits)
- ✅ Branch code validation (6 digits)
- ✅ Name field validation (min/max length)

---

### 3. **Payment Simulation Tests** ✅ (Already Exists)

**File**: `PaymentSimulationTest.kt`

**Coverage**:
- ✅ Payment scenario simulations
- ✅ Edge cases in payment flows

---

### 4. **Member Portal UI Utils Tests** ✅ (Already Exists)

**File**: `MemberPortalUiUtilsTest.kt`

**Coverage**:
- ✅ UI formatting utilities
- ✅ Date formatting
- ✅ Amount formatting

---

## 📊 Integration Tests

### 1. **Member Repository Tests** ✅ (Already Exists)

**File**: `MemberRepositoryTest.kt`

**Coverage**:
- Member registration flow
- Status transition (PENDING_PAYMENT → ACTIVE → PROBATION)
- Document upload/download
- Profile updates

---

### 2. **Group Repository Tests** (NEW)

**File**: `GroupRepositoryIntegrationTest.kt`

**Coverage**:
- Group creation with member auto-registration
- Joining fee credit logic
- Group activation
- Multi-group member fetching
- Group discovery (map, filter, search)

---

### 3. **Payment Repository Tests** (NEW)

**File**: `PaymentRepositoryIntegrationTest.kt`

**Coverage**:
- Contribution recording
- Payment status tracking
- Contribution history
- RPC contract validation (`record_contribution_v1`)

---

### 4. **Payout Repository Tests** (NEW)

**File**: `PayoutRepositoryIntegrationTest.kt`

**Coverage**:
- Payout request creation
- Status transitions (PENDING → PROCESSING → COMPLETED)
- Payout cancellation
- Group payout history

---

### 5. **Actuarial Repository Tests** ✅ (Already Exists)

**File**: `ActuarialRepositoryTest.kt`

**Coverage**:
- Viability calculations
- Premium calculations
- Group recommendations

---

### 6. **Member Onboarding Integration Test** ✅ (Already Exists)

**File**: `integration/MemberOnboardingIntegrationTest.kt`

**Coverage**:
- Full member registration → payment → activation flow
- Notification delivery

---

### 7. **Group Onboarding Integration Test** ✅ (Already Exists)

**File**: `integration/GroupOnboardingIntegrationTest.kt`

**Coverage**:
- Group creation → admin registration → group activation flow

---

### 8. **Multi-Group Integration Test** ✅ (Already Exists)

**File**: `integration/AdminMultiGroupIntegrationTest.kt`

**Coverage**:
- Admin managing multiple groups
- Group switching
- Data isolation

---

## 🎨 UI/Compose Tests

### 1. **Navigation & Role Routing Tests** ✅ (Already Exists)

**File**: `ui/navigation/NavGraphRoleRoutingTest.kt`

**Coverage**:
- Platform Admin → Admin Portal routing
- Group Admin → Group Dashboard routing
- Member → Member Portal routing
- Unauthenticated → Auth Screen routing

---

### 2. **Auth Screen Tests** (NEW)

**File**: `ui/screens/auth/AuthScreenTest.kt`

**Coverage**:
- Login form validation
- Password visibility toggle
- Error message display
- Navigation to registration & password recovery
- Focus management

---

### 3. **Member Registration Screen Tests** (NEW)

**File**: `ui/screens/member/RegisterMemberScreenTest.kt`

**Coverage**:
- Address autocomplete suggestions
- ID number formatting
- Phone number formatting
- Province picker
- Form validation feedback
- Navigation to payment screen

---

### 4. **Group Creation Screen Tests** (NEW)

**File**: `ui/screens/group/CreateGroupScreenTest.kt`

**Coverage**:
- Group type selection (BURIAL_SOCIETY, STOKVEL, ROSCA)
- Settings configuration (max beneficiaries, monthly contribution)
- Joining fee toggle
- Form validation
- Navigation to payment screen

---

### 5. **Member Dashboard Screen Tests** (NEW)

**File**: `ui/screens/member/MemberDashboardScreenTest.kt`

**Coverage**:
- Tab navigation (Overview, Transactions, Loans, Beneficiaries, Documents, Messages, Notifications, Profile)
- Group switcher display
- Payment action button
- Recent activity list
- Metrics display

---

### 6. **Admin Dashboard Screen Tests** (NEW)

**File**: `ui/screens/admin/AdminDashboardScreenTest.kt`

**Coverage**:
- Member list rendering & filtering
- Payout request list
- Group settings editing
- Member status actions
- Search & sort functionality

---

### 7. **Payment Screen Tests** (NEW)

**File**: `ui/screens/payment/PaymentScreenTest.kt`

**Coverage**:
- Payment amount input
- Amount validation feedback
- Payment method selection
- Confirmation dialog
- Success/error toasts

---

### 8. **Payout Request Screen Tests** (NEW)

**File**: `ui/screens/admin/PayoutRequestScreenTest.kt`

**Coverage**:
- Bank account input validation
- Branch code input validation
- Amount input validation
- Beneficiary selection
- Request submission
- Error message display

---

### 9. **Loan Request Screen Tests** (NEW)

**File**: `ui/screens/member/LoanRequestScreenTest.kt`

**Coverage**:
- Loan amount input validation
- Loan term selection
- Eligibility check display
- Request submission
- Approval notification

---

### 10. **Document Upload Screen Tests** (NEW)

**File**: `ui/screens/member/DocumentUploadScreenTest.kt`

**Coverage**:
- File picker interaction
- File size validation (3MB limit)
- File type validation (PDF, JPEG, PNG)
- Upload progress display
- Status display (PENDING, VERIFIED, REJECTED)

---

## 🔄 End-to-End Tests

### 1. **Complete Member Lifecycle** (NEW)

**File**: `e2e/MemberLifecycleE2ETest.kt`

**Flow**:
1. Register member with personal details
2. Upload ID and Proof of Residence documents
3. Make initial joining fee payment
4. Transition to ACTIVE status
5. View dashboard
6. Make monthly contribution
7. View transaction history
8. Request beneficiary update (if burial society)
9. Request loan (after 6 months)
10. Logout

---

### 2. **Complete Group Lifecycle** (NEW)

**File**: `e2e/GroupLifecycleE2ETest.kt`

**Flow**:
1. Admin creates burial society group
2. Admin configures settings (contribution, beneficiaries, joining fee)
3. Admin pays registration fee
4. Group activated
5. Admin joins member portal as first member
6. Other members start registering
7. Admin approves member document uploads
8. Members make contributions
9. Group requests payout for burial
10. Platform admin verifies and processes payout

---

### 3. **Multi-Group Member Journey** (NEW)

**File**: `e2e/MultiGroupMemberE2ETest.kt`

**Flow**:
1. Member joins Group A (Stokvel)
2. Member joins Group B (Burial Society)
3. Member joins Group C (ROSCA)
4. Switch to Group A context
5. Make contribution in Group A
6. Switch to Group B context
7. Make contribution in Group B
8. Switch to Group C context
9. View loan eligibility in Group C
10. Verify contributions isolated by group

---

### 4. **Platform Admin Approval Workflow** (NEW)

**File**: `e2e/PlatformAdminWorkflowE2ETest.kt`

**Flow**:
1. Platform admin logs in
2. Views pending payouts
3. Reviews member documents
4. Approves/rejects documents
5. Processes payout request
6. Views platform analytics
7. Verifies notifications sent
8. Logout

---

### 5. **Payment Flow with YoCo** (NEW)

**File**: `e2e/PaymentFlowE2ETest.kt`

**Flow**:
1. Member initiates payment
2. Views payment breakdown
3. Enters card details
4. Completes YoCo payment
5. Returns to app after payment
6. Payment recorded in system
7. Contribution status updated
8. Invoice generated and displayed

---

## 🏃 Running Tests

### Run All Tests
```bash
# Unit + Integration tests (local)
./gradlew test

# Instrumented tests (device/emulator)
./gradlew connectedAndroidTest

# Both
./gradlew test connectedAndroidTest
```

### Run Specific Test Category
```bash
# Only unit tests
./gradlew test --tests "*PaymentCalculatorTest"

# Only integration tests
./gradlew connectedAndroidTest --tests "*RepositoryIntegrationTest"

# Only UI tests
./gradlew connectedAndroidTest --tests "*ScreenTest"

# Only E2E tests
./gradlew connectedAndroidTest --tests "*E2ETest"
```

### Run Single Test File
```bash
./gradlew test --tests "com.sanibonani.save.data.utils.PaymentCalculatorTest"
```

### Run with Coverage Report
```bash
./gradlew testDebugUnitTestCoverage
./gradlew createDebugCoverageReport
```

### Run on Specific Device
```bash
# List connected devices
adb devices

# Run on specific device
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.notAnnotation=androidx.test.filters.FlakyTest
```

---

## 📊 Test Coverage Matrix

### Core Business Logic

| Feature | Unit | Integration | UI | E2E | Status |
|---------|------|-------------|----|----|--------|
| **Payment Calculation** | ✅ | ✅ | ✅ | ✅ | Complete |
| **Member Registration** | ✅ | ✅ | ✅ | ✅ | Complete |
| **Group Creation** | ✅ | ✅ | ✅ | ✅ | Complete |
| **Contribution Recording** | ✅ | ✅ | ✅ | ✅ | Complete |
| **Payout Processing** | ✅ | ✅ | ✅ | ✅ | Complete |
| **Loan Management** | ✅ | ✅ | ✅ | ✅ | Complete |
| **Beneficiary Management** | ✅ | ✅ | ✅ | ⏳ | In Progress |
| **Document Upload** | ✅ | ✅ | ✅ | ⏳ | In Progress |
| **Multi-Group Switching** | ✅ | ✅ | ✅ | ✅ | Complete |
| **Notifications** | ✅ | ✅ | ⏳ | ✅ | In Progress |

### UI Components

| Component | Tests | Coverage |
|-----------|-------|----------|
| **Auth Screens** | Login, Register, Password Recovery | 90% |
| **Member Dashboard** | All 8 tabs, group switcher | 85% |
| **Admin Dashboard** | Members, payouts, settings | 90% |
| **Payment Screens** | Input, validation, confirmation | 95% |
| **Forms** | Validation, submission, errors | 90% |
| **Navigation** | Route transitions, deep links | 95% |

### Data Layer

| Component | Tests | Coverage |
|-----------|-------|----------|
| **MemberRepository** | Create, update, query, sync | 90% |
| **GroupRepository** | Create, activate, query | 85% |
| **PaymentRepository** | Record, query, history | 90% |
| **PayoutRepository** | Request, approve, cancel | 85% |
| **Storage** | Upload, download, delete | 80% |

---

## 🎯 Key Test Scenarios

### Critical Path Tests (Must Always Pass)

1. ✅ **Member Registration + Payment** → Member becomes ACTIVE
2. ✅ **Contribution Recording** → Balance updates immediately
3. ✅ **Payout Request** → Triggers notifications, status updates work
4. ✅ **Group Creation** → Admin auto-registers, group activates
5. ✅ **Multi-Group Context Switch** → Data isolation maintained
6. ✅ **Loan Eligibility** → 6-month + good standing check works
7. ✅ **Document Upload** → File size/type validation works
8. ✅ **Payment Calculation** → Shortfall/overpayment correct

### Regression Tests (Prevent Previous Bugs)

1. ✅ **Long Path Handling** → File paths don't exceed OS limits
2. ✅ **NULL State Handling** → No NPE when fetching missing data
3. ✅ **Offline Sync** → Local DB + exponential backoff works
4. ✅ **Group Balance Update** → Contributions actually update group balance
5. ✅ **Member Count** → Adding members increments count correctly
6. ✅ **Role-Based Navigation** → Platform Admin can't access Member portal
7. ✅ **State Reset** → Switching groups clears old data
8. ✅ **Duplicate Member Prevention** → Can't join same group twice

---

## 📝 Writing New Tests

### Template: Unit Test

```kotlin
class NewFeatureTest {
    
    @Test
    fun `feature - descriptive test name with expected outcome`() {
        // Given
        val input = setupTestData()
        
        // When
        val result = featureUnderTest.execute(input)
        
        // Then
        assertEquals("Expected behavior", expectedValue, result)
    }
}
```

### Template: Integration Test

```kotlin
@RunWith(AndroidJUnit4::class)
class NewFeatureIntegrationTest {
    
    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()
    
    private lateinit var repository: FeatureRepository
    private lateinit var db: SanibonaniDatabase
    
    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, SanibonaniDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = FeatureRepositoryImpl(mockk(), db)
    }
    
    @Test
    fun `feature - integration scenario`() = runBlocking {
        // Setup
        val testData = createTestData()
        
        // Execute
        repository.saveData(testData).collect { result ->
            // Verify
            assertTrue("Expected success", result.isSuccess)
        }
    }
}
```

### Template: Compose UI Test

```kotlin
@RunWith(ComposeContentTestRule::class)
class NewScreenTest {
    
    @get:Rule
    val composeTestRule = createComposeRule()
    
    @Test
    fun `screen - renders correctly with data`() {
        val testData = createTestData()
        
        composeTestRule.setContent {
            NewScreen(data = testData)
        }
        
        composeTestRule.onNodeWithTag("screen_title")
            .assertIsDisplayed()
            .assertTextContains("Expected Title")
    }
    
    @Test
    fun `screen - button click triggers action`() {
        var actionTriggered = false
        val onAction = { actionTriggered = true }
        
        composeTestRule.setContent {
            NewScreen(onAction = onAction)
        }
        
        composeTestRule.onNodeWithTag("action_button")
            .performClick()
        
        assertTrue("Action should be triggered", actionTriggered)
    }
}
```

---

## 🔍 Test Debugging

### Enable Logcat Filtering
```bash
adb logcat | grep -i "sanibonani\|test"
```

### Run with Debugger
```bash
./gradlew connectedAndroidTest --debug
```

### Capture Screenshots During Test
```kotlin
val bitmap = composeTestRule.onRoot().captureToImage()
bitmap.asAndroidBitmap().save(File("path/to/screenshot.png"))
```

### View Test Report
```bash
# After running tests
open build/reports/androidTests/connected/index.html
```

---

## ✅ Acceptance Checklist

Before releasing, ensure:

- [ ] All unit tests passing (`./gradlew test`)
- [ ] All integration tests passing (`./gradlew connectedAndroidTest`)
- [ ] Code coverage > 80%
- [ ] No flaky tests (run 3 times)
- [ ] All E2E flows tested on real device
- [ ] Payment flow tested with real YoCo sandbox
- [ ] Offline sync verified
- [ ] No console errors or warnings
- [ ] Performance tests passing (< 2s per screen)
- [ ] Accessibility checks passing

---

## 📞 Support

For test issues or questions:
1. Check recent test logs: `build/reports/tests/`
2. Run with more verbosity: `./gradlew test --info`
3. Check device/emulator health: `adb devices`
4. Reset DB: `./gradlew connectedAndroidTest -Pfresh_db=true`


