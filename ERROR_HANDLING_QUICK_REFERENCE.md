# Quick Reference: Error Handling & Validation

## 🚀 Quick Start for Developers

### Showing Toast Messages

```kotlin
val context = LocalContext.current

// Success
ToastUtils.showSuccess(context, "Payment successful!")

// Error
ToastUtils.showError(context, "Invalid card number")

// Warning
ToastUtils.showWarning(context, "Payment overdue")

// Info
ToastUtils.showInfo(context, "Processing...")

// Custom short
ToastUtils.showShort(context, "Done")

// Custom long
ToastUtils.showLong(context, "This is a longer message")
```

---

### Validating Form Fields

```kotlin
// Email validation
val emailValidation = ValidationUtils.validateEmailField(email)
if (emailValidation !is ValidationResult.Valid) {
    showError(emailValidation.getErrorMessage())
    return
}

// Password validation
val pwValidation = ValidationUtils.validatePasswordField(password)

// Password match
val matchValidation = ValidationUtils.validatePasswordMatch(password, confirmPw)

// Member registration
val memberValidation = ValidationUtils.validateMemberFields(
    fullName, idNumber, phone, email
)

// Group registration (step by step)
val step1 = ValidationUtils.validateGroupStep1(name, type, description)
val step2 = ValidationUtils.validateGroupStep2(province, city, township)
val step3 = ValidationUtils.validateGroupStep3(joining, contribution, late, maxMembers)
val step4 = ValidationUtils.validateGroupStep4(email, password)

// Payment fields
val paymentValidation = ValidationUtils.validatePaymentFields(card, expiry, cvv)
```

---

### Error Handling in ViewModels

```kotlin
// Pattern 1: Repository call with error handling
myRepository.someOperation()
    .onSuccess { result ->
        _state.update { it.copy(data = result, isLoading = false) }
    }
    .onFailure { e ->
        val errorMsg = e.getErrorMessage() ?: "Operation failed"
        AppLogger.e("MyViewModel", errorMsg, e)
        _state.update { it.copy(error = errorMsg, isLoading = false) }
    }

// Pattern 2: With validation first
val validation = ValidationUtils.validateSomething(value)
if (validation !is ValidationResult.Valid) {
    _state.update { it.copy(error = validation.getErrorMessage()) }
    return
}

// Pattern 3: Try-catch for unexpected errors
try {
    myRepository.operation()
        .onSuccess { ... }
        .onFailure { ... }
} catch (e: Exception) {
    AppLogger.e("MyViewModel", "Unexpected: ${e.message}", e)
    _state.update { it.copy(error = e.getErrorMessage() ?: "An error occurred") }
}
```

---

### Error Handling in Screens

```kotlin
@Composable
fun MyScreen(..., vm: MyViewModel = hiltViewModel()) {
    val state by vm.uiState.collectAsState()
    val context = LocalContext.current

    // Show error toast when state.error changes
    LaunchedEffect(state.error) {
        state.error?.let { ToastUtils.showError(context, it) }
    }

    // Show success toast when operation completes
    LaunchedEffect(state.isSuccess) {
        if (state.isSuccess) {
            ToastUtils.showSuccess(context, "Operation successful!")
            onNavigateNext()
        }
    }

    // Show validation error in UI and toast
    if (state.error != null) {
        InfoBox(state.error!!, InfoType.ERROR)
    }
}
```

---

### Common Error Messages

| Scenario | Message |
|----------|---------|
| Network error | "Network error. Check your internet" |
| Timeout | "Connection timeout. Check your internet" |
| Not found | "Data not found" |
| Permission denied | "Permission denied" |
| Invalid input | From specific validation (e.g., "Invalid email format") |
| Empty field | From specific validation (e.g., "Full name is required") |

---

### Validation Result Handling

```kotlin
val result = ValidationUtils.validateEmail(email)

// Check if valid
if (result.isValid()) {
    // Proceed
}

// Check if error and get message
if (result !is ValidationResult.Valid) {
    val errorMsg = result.getErrorMessage() ?: "Unknown error"
    // Show error
}

// Pattern match
when (result) {
    is ValidationResult.Valid -> { /* proceed */ }
    is ValidationResult.Error -> { /* show error */ }
}
```

---

### Adding New Validations

```kotlin
// In ValidationUtils.kt, add:
fun validateNewField(value: String): ValidationResult {
    return when {
        value.isBlank() -> ValidationResult.Error("Field is required")
        value.length < 3 -> ValidationResult.Error("Minimum 3 characters")
        !value.matches("^[A-Z]".toRegex()) -> ValidationResult.Error("Must start with uppercase")
        else -> ValidationResult.Valid
    }
}

// Use anywhere:
val validation = ValidationUtils.validateNewField(userInput)
if (validation !is ValidationResult.Valid) {
    _state.update { it.copy(error = validation.getErrorMessage()) }
}
```

---

### Safe Result Operations

```kotlin
// Convert exception to user message
val userFriendlyMsg = exception.toUserMessage()

// Get error from result
val result: Result<Data> = repository.getData()
val errorMsg = result.getErrorMessage()

// Recover with fallback
val data = result.recoverValue(emptyList())

// Safe execution with logging
val result = safeCall("MyTag") {
    // Code that might throw
    doSomething()
}
```

---

### Field Update Pattern (Simplified)

```kotlin
// In ViewModel:
fun updateField(field: String, value: Any) {
    _state.update {
        when (field) {
            "email" -> it.copy(email = value.toString())
            "amount" -> it.copy(amount = value.toString())
            "status" -> if (value is Status) it.copy(status = value) else it
            else -> it
        }
    }
}

// In Screen:
SanibonaniTextField(
    value = state.email,
    onValueChange = { vm.updateField("email", it) },
    label = "Email"
)
```

---

### Logging with Context

```kotlin
// Error with full context
AppLogger.e("PaymentViewModel", "Payment failed: Card declined", exception)

// Warning for validation issues
AppLogger.w("GroupViewModel", "Step 1 validation failed: ${result.getErrorMessage()}")

// Debug for normal flow
AppLogger.d("MemberViewModel", "Member loaded: ${member.id}")

// Info for important events
AppLogger.i("AuthViewModel", "Sign-in successful for: $email (role: $role)")
```

---

## 📋 Checklist for New Features

- [ ] Import `ToastUtils` if showing user messages
- [ ] Use `ValidationUtils` for all field validation
- [ ] Handle errors with `.onFailure { e -> e.getErrorMessage() }`
- [ ] Add try-catch for unexpected errors
- [ ] Show error toasts in screens via `LaunchedEffect`
- [ ] Log errors with appropriate level (error, warn, debug, info)
- [ ] No hardcoded validation logic
- [ ] No raw `Toast.makeText()` calls
- [ ] No null-coalescing for errors (use `?.let {}`)

---

## 🎯 Best Practices

✅ **DO**:
- Centralize validation logic
- Use `ValidationResult` sealed classes
- Show descriptive error messages
- Log errors for debugging
- Use ToastUtils for user feedback
- Handle null user IDs with error messages

❌ **DON'T**:
- Mix validation across screens/ViewModels
- Throw exceptions to UI layer
- Show raw exception messages
- Use `Toast.makeText()` directly
- Silently catch and ignore errors
- Return null on errors without logging

---

## 🔧 Common Patterns

### Complete Flow Example

```kotlin
@Composable
fun MyFormScreen(onSuccess: () -> Unit, vm: MyViewModel = hiltViewModel()) {
    val state by vm.uiState.collectAsState()
    val context = LocalContext.current

    // Handle errors
    LaunchedEffect(state.error) {
        state.error?.let { ToastUtils.showError(context, it) }
    }

    // Handle success
    LaunchedEffect(state.isSuccess) {
        if (state.isSuccess) {
            ToastUtils.showSuccess(context, "Done!")
            onSuccess()
        }
    }

    // Form UI
    SanibonaniTextField(
        value = state.email,
        onValueChange = { vm.updateField("email", it) },
        label = "Email"
    )

    // Show validation error in UI
    if (state.error != null) {
        InfoBox(state.error!!, InfoType.ERROR)
    }

    SanibonaniButton(
        text = "Submit",
        onClick = { vm.submit() },
        enabled = !state.isLoading
    )
}

// In ViewModel
fun submit() {
    // Validate
    val validation = ValidationUtils.validateEmail(state.email)
    if (validation !is ValidationResult.Valid) {
        _state.update { it.copy(error = validation.getErrorMessage()) }
        return
    }

    // Submit
    viewModelScope.launch {
        try {
            _state.update { it.copy(isLoading = true, error = null) }
            repository.submit(state.email)
                .onSuccess {
                    _state.update { it.copy(isLoading = false, isSuccess = true) }
                }
                .onFailure { e ->
                    _state.update { it.copy(isLoading = false, error = e.getErrorMessage()) }
                }
        } catch (e: Exception) {
            _state.update { it.copy(isLoading = false, error = e.getErrorMessage() ?: "An error occurred") }
        }
    }
}
```

This is now the standard pattern for all forms and operations in SanibonaniSave!

