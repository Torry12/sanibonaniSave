# Testing & Error Handling Guide - SanibonaniSave

## 🧪 Complete Testing Workflow

### Phase 1: Unit Tests (Validation & Logic)

#### InputValidator Tests
```kotlin
class InputValidatorTest {
    @Test
    fun testValidateName_ValidInput() {
        val result = InputValidator.validateName("John Doe")
        assert(result is ValidationResult.Valid)
    }
    
    @Test
    fun testValidateName_TooShort() {
        val result = InputValidator.validateName("Jo")
        assert(result is ValidationResult.Error)
        assert(result.getErrorMessage()?.contains("at least") == true)
    }
    
    @Test
    fun testValidateSAIdNumber_InvalidChecksum() {
        val result = InputValidator.validateSAIdNumber("0000000000000")
        assert(result is ValidationResult.Error)
        assert(result.getErrorMessage()?.contains("checksum") == true)
    }
    
    @Test
    fun testValidateEmail_InvalidFormat() {
        val result = InputValidator.validateEmail("notanemail")
        assert(result is ValidationResult.Error)
    }
}
```

#### Enum Serialization Tests
```kotlin
class AdminFeeStateTest {
    @Test
    fun testEnumSerialization() {
        val state = AdminFeeState.DUE
        val json = Json.encodeToString(state)
        assert(json.contains("due"))  // Should be lowercase
        assert(!json.contains("DUE")) // Should NOT be uppercase
    }
    
    @Test
    fun testEnumDeserialization() {
        val json = "\"due\""
        val state = Json.decodeFromString<AdminFeeState>(json)
        assert(state == AdminFeeState.DUE)
    }
}
```

---

### Phase 2: Integration Tests (ViewModel & Repository)

#### GroupViewModel Tests
```kotlin
class GroupViewModelTest {
    private lateinit var vm: GroupViewModel
    private lateinit var mockGroupRepo: GroupRepository
    
    @Before
    fun setup() {
        mockGroupRepo = mockk()
        vm = GroupViewModel(mockGroupRepo)
    }
    
    @Test
    fun testSubmitGroup_ValidInput() = runTest {
        // Setup
        val testGroup = Group(
            name = "Test Group",
            type = GroupType.BURIAL_SOCIETY,
            description = "Test",
            province = "Gauteng",
            city = "Johannesburg",
            township = "Soweto",
            joiningFee = "100.0",
            monthlyContribution = "50.0",
            lateFee = "10.0",
            maxMembers = "50"
        )
        
        coEvery { mockGroupRepo.createGroup(any(), any(), any()) } returns 
            Result.success("test-group-id")
        
        // Execute
        vm.updateField("name", "Test Group")
        vm.updateField("type", GroupType.BURIAL_SOCIETY)
        // ... update other fields ...
        vm.submitGroup()
        
        // Assert
        val state = vm.registerState.value
        assert(state.success)
        assert(state.createdGroupId == "test-group-id")
        assert(state.error == null)
    }
    
    @Test
    fun testSubmitGroup_ValidationFails() = runTest {
        // Setup with invalid data
        vm.updateField("name", "XY")  // Too short
        
        // Execute
        vm.submitGroup()
        
        // Assert
        val state = vm.registerState.value
        assert(!state.success)
        assert(state.error?.contains("at least") == true)
    }
}
```

#### GroupRepository Tests
```kotlin
class GroupRepositoryTest {
    private lateinit var repo: GroupRepository
    private lateinit var mockSupabase: SupabaseClient
    private lateinit var mockDB: SanibonaniDatabase
    
    @Test
    fun testCreateGroup_InitializesPlatformFee() = runTest {
        // Setup
        val testGroup = Group(name = "Test", /* ... */)
        
        coEvery { mockSupabase.postgrest["groups"].insert(any(), any()) } returns
            mockk { coEvery { decodeSingle<Group>() } returns testGroup.copy(id = "123") }
        
        coEvery { mockSupabase.postgrest["platform_fees"].insert(any()) } returns mockk()
        
        // Execute
        val result = repo.createGroup(testGroup)
        
        // Assert
        assert(result.isSuccess)
        coVerify { mockSupabase.postgrest["platform_fees"].insert(any()) }
    }
}
```

---

### Phase 3: UI Tests (Compose)

#### RegisterGroupScreen Tests
```kotlin
class RegisterGroupScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()
    
    @Test
    fun testStep1_CanEnterGroupName() {
        composeTestRule.setContent {
            RegisterGroupScreen(
                onGroupCreated = {},
                onBack = {}
            )
        }
        
        composeTestRule.onNodeWithText("Group Name *")
            .performTextInput("Ubuntu Burial Society")
        
        composeTestRule.onNodeWithText("Ubuntu Burial Society")
            .assertExists()
    }
    
    @Test
    fun testStep4_SubmitButtonVisible() {
        composeTestRule.setContent {
            RegisterGroupScreen(
                onGroupCreated = {},
                onBack = {}
            )
        }
        
        // Navigate to step 4
        repeat(3) {
            composeTestRule.onNodeWithText("Continue →").performClick()
        }
        
        composeTestRule.onNodeWithText("Create Group & Pay Admin Fee (R700)")
            .assertExists()
    }
}
```

---

## ⚠️ Error Handling Best Practices

### 1. **Validation Errors** (User Input)
```kotlin
// ❌ BAD: Showing raw exception
_state.update { it.copy(error = e.toString()) }

// ✅ GOOD: User-friendly message
when (validation) {
    is ValidationResult.Valid -> proceedWithSubmission()
    is ValidationResult.Error -> {
        val message = validation.getErrorMessage()
        _state.update { it.copy(error = message) }
        AppLogger.w("ViewModel", "Validation failed: $message")
    }
}
```

### 2. **Network Errors** (Supabase)
```kotlin
// ❌ BAD: Failing silently
try {
    val group = supabase.postgrest["groups"].select().decodeSingle<Group>()
} catch (e: Exception) {
    // Ignored!
}

// ✅ GOOD: Fallback to cache with logging
try {
    val group = supabase.postgrest["groups"].select().decodeSingle<Group>()
    db.groupDao().upsertGroup(group.toEntity())
    emit(Result.success(group))
} catch (e: Exception) {
    AppLogger.e("Repository", "Network fetch failed, using cache: ${e.message}", e)
    try {
        val cached = db.groupDao().getGroupById(groupId)
        emit(Result.success(cached.toModel()))
    } catch (cacheError: Exception) {
        emit(Result.failure(e))  // Network error takes precedence
    }
}
```

### 3. **Auxiliary Record Failures** (Should not fail main operation)
```kotlin
// ❌ BAD: Fails entire group creation if fee init fails
val created = insertGroup(group)
val fee = createPlatformFee(created.id)  // Could throw!
return created.id

// ✅ GOOD: Logs warning but continues
val created = insertGroup(group)
try {
    createPlatformFee(created.id)
} catch (e: Exception) {
    AppLogger.w("Repository", "Failed to init platform fee: ${e.message}")
    // Continue - group creation succeeded
}
return created.id
```

### 4. **Payment Error Recovery**
```kotlin
// In PaymentViewModel
fun processPayment(/* ... */) {
    viewModelScope.launch {
        try {
            _state.update { it.copy(isProcessing = true, error = null) }
            
            // Validate input
            val validation = ValidationUtils.validatePaymentFields(card, expiry, cvv)
            if (validation !is ValidationResult.Valid) {
                _state.update { it.copy(
                    isProcessing = false,
                    error = validation.getErrorMessage() ?: "Validation failed"
                ) }
                return@launch
            }
            
            // Process payment via YoCo
            val result = paymentRepo.processPayment(/* ... */)
            result.onSuccess {
                // Update group status
                groupRepo.activateGroup(groupId)
                _state.update { it.copy(isSuccess = true, isProcessing = false) }
            }.onFailure { e ->
                _state.update { it.copy(
                    isProcessing = false,
                    error = e.getErrorMessage() ?: "Payment failed. Please try again."
                ) }
            }
        } catch (e: Exception) {
            _state.update { it.copy(
                isProcessing = false,
                error = "Unexpected error: ${e.message}"
            ) }
            AppLogger.e("PaymentViewModel", "Payment error: ${e.message}", e)
        }
    }
}
```

---

## 🔍 Common Error Messages & Fixes

| Error | Cause | Fix |
|-------|-------|-----|
| "AdminFeeState does not contain 'due'" | Missing @SerialName on enum | Add `@SerialName("due")` to enum value |
| "Failed to create admin account" | Email already exists | Generate unique email or use different provider |
| "Network error" | No internet connection | Fall back to Room cache; show offline mode |
| "Validation failed: Name is required" | User left field empty | Highlight field, show helper text |
| "Payment failed" | YoCo API error or invalid card | Show YoCo error; allow retry |
| "Group creation succeeded but platform fee failed" | Auxiliary record error | This is expected - log warning; continue |

---

## 🧬 Error Tracking & Logging

### Error Categories
```kotlin
enum class ErrorCategory {
    VALIDATION,     // User input validation
    NETWORK,        // Supabase/API errors
    AUTH,           // Authentication failures
    PAYMENT,        // Payment processing errors
    UNKNOWN         // Unexpected errors
}

// Log with context
fun logError(category: ErrorCategory, message: String, throwable: Throwable? = null) {
    when (category) {
        ErrorCategory.VALIDATION -> AppLogger.w("Input", message)
        ErrorCategory.NETWORK -> AppLogger.e("Network", message, throwable)
        ErrorCategory.AUTH -> AppLogger.e("Auth", message, throwable)
        ErrorCategory.PAYMENT -> AppLogger.e("Payment", message, throwable)
        ErrorCategory.UNKNOWN -> AppLogger.e("Unknown", message, throwable)
    }
}
```

---

## 📋 Checklist for Error-Free Registration

- [ ] **Step 1 Validation**
  - Group name: 3-100 chars, letters/spaces/hyphens/apostrophes only
  - Group type: Must be selected
  - Description: 3+ chars

- [ ] **Step 2 Validation**
  - Province: Must be selected from SA_PROVINCES list
  - City: Required, 2+ chars
  - Township: Required, 2+ chars

- [ ] **Step 3 Validation**
  - Joining fee: Positive decimal number
  - Monthly contribution: Positive decimal number
  - Late fee: Positive decimal number
  - Max members: Positive integer

- [ ] **Step 4 Validation**
  - Admin email: Valid email format
  - Admin password: 8+ chars
  - Bank name: Optional but if provided, valid bank
  - Account number: Optional but if provided, correct length

- [ ] **Group Creation**
  - Group inserted with status = PENDING_ACTIVATION
  - Admin user created with role "group_admin"
  - Platform fee initialized with status = DUE
  - Group cached in Room database

- [ ] **Payment Flow**
  - Amount calculated correctly (R700 for registration)
  - YoCo modal launches with correct amount
  - Payment success triggers group activation
  - group.registration_paid set to true

- [ ] **Error Messages**
  - Validation errors show specific field error
  - Network errors offer retry option
  - Auxiliary record failures don't block main flow
  - User can navigate back and retry

---

## 🚀 Quick Start Testing

### Test 1: Create Group (Happy Path)
1. Launch app → Register Group
2. Step 1: Enter "Test Burial Society", type: Burial Society, description: "Test group"
3. Step 2: Select Gauteng, enter "Johannesburg", "Soweto"
4. Step 3: Enter 100, 50, 10, 50 (fees and max members)
5. Step 4: Enter test@example.com, password123, skip bank details
6. Click "Create Group & Pay Admin Fee (R700)"
7. Verify: Group created in Supabase, platform fee initialized
8. Complete YoCo payment with test card
9. Verify: group.registration_paid = true

### Test 2: Validation Errors
1. Step 1: Enter "XY" for name (too short)
2. Click Continue → Should show error
3. Fix to "Test Group"
4. Click Continue → Should advance

### Test 3: Network Error Recovery
1. Turn off WiFi
2. Try to create group
3. Should show "Network error" toast
4. Turn on WiFi, retry
5. Should succeed

### Test 4: Admin Account Already Exists
1. Create first group with email: admin@test.com
2. Try to create second group with same email
3. Should show "Email already exists" error
4. Use different email

---

*Last Updated: March 24, 2026*

