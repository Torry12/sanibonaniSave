# Error Handling Architecture Diagram

## 🏗️ Error Handling Flow

```
┌────────────────────────────────────────────────────────────────────────────┐
│                            USER INTERFACE (Screen)                         │
│                                                                             │
│  ┌─────────────────┐  ┌──────────────────┐  ┌──────────────────┐          │
│  │  LoginScreen    │  │  PaymentScreen   │  │ GroupRegisterScreen         │
│  └────────┬────────┘  └────────┬─────────┘  └────────┬─────────┘          │
│           │                    │                     │                      │
│           ▼                    ▼                     ▼                      │
│  ┌────────────────────────────────────────────────────────────┐            │
│  │  LaunchedEffect(state.error) {                            │            │
│  │    ToastUtils.showError(context, it)                      │            │
│  │  }                                                         │            │
│  └────────────────────────────────────────────────────────────┘            │
└────────────────────┬─────────────────────────────────────────────────────┘
                     │
                     │ observes state.error
                     ▼
┌────────────────────────────────────────────────────────────────────────────┐
│                          VIEW MODEL LAYER                                  │
│                                                                             │
│  ┌──────────────────────────────────────────────────────────┐             │
│  │  AuthViewModel / PaymentViewModel / GroupViewModel       │             │
│  │                                                          │             │
│  │  fun doSomething() {                                    │             │
│  │    repository.operation()                               │             │
│  │      .onSuccess { ... }                                │             │
│  │      .onFailure { e ->                                 │             │
│  │        val msg = e.getErrorMessage()                   │             │
│  │        _state.update { it.copy(error = msg) }          │             │
│  │      }                                                  │             │
│  │  }                                                       │             │
│  └──────────────────────────────────────────────────────────┘             │
│                                 ▲                                          │
│                                 │ calls                                    │
└─────────────────────────────────┼──────────────────────────────────────────┘
                                  │
                    ┌─────────────┴─────────────┐
                    │                           │
                    ▼                           ▼
        ┌──────────────────────┐     ┌──────────────────────────┐
        │ Validation Layer     │     │ Safe Result Extensions   │
        │ (ValidationUtils)    │     │ (SafeResultExtensions)   │
        └──────────────────────┘     └──────────────────────────┘
                    │                           │
                    │                           │
    ┌───────────────┴────────────────┬──────────┴────────────────┐
    │                                │                           │
    ▼                                ▼                           ▼
┌──────────────────┐  ┌──────────────────────┐  ┌──────────────────┐
│ Validate Email   │  │ getErrorMessage()    │  │ toUserMessage()  │
│ Validate Phone   │  │ Returns user-friendly│  │ Converts:        │
│ Validate Payment │  │ text for display     │  │ Exception → String
│ Validate Group   │  └──────────────────────┘  │                  │
│ Returns:         │                             │ Maps:            │
│ ValidationResult │                             │ - Timeout        │
└──────────────────┘                             │ - NetworkError   │
         │                                       │ - NotFound       │
         │                                       │ - Permission     │
         ▼                                       ▼
    ┌──────────────────────────────────────────────────────┐
    │  ValidationResult (Sealed Class)                    │
    │  - Valid                                            │
    │  - Error(message: String)                           │
    └──────────────────────────────────────────────────────┘

```

---

## 📊 Error Message Flow

```
REPOSITORY LAYER
        │
        ▼
   Result<T>
   (onSuccess/onFailure)
        │
        ├─► onSuccess → Show success toast
        │
        └─► onFailure(exception) → exception
                         │
                         ▼
                exception.getErrorMessage()
                         │
        ┌────────────────┼────────────────┐
        │                │                │
    IOException    SocketTimeoutException   ...
        │                │
        ▼                ▼
  "Network error.   "Connection timeout.
   Check internet"   Check internet"
        │                │
        └────────────────┼────────────────┘
                         │
                         ▼
            _state.update { 
                it.copy(error = msg)
            }
                         │
                         ▼
            LaunchedEffect(state.error) {
                ToastUtils.showError(
                    context, 
                    state.error
                )
            }
                         │
                         ▼
            ┌─────────────────────────────┐
            │  ❌ Connection timeout.     │
            │  Check your internet        │
            └─────────────────────────────┘
```

---

## 🔄 Validation Flow

```
USER INPUT (TextField)
        │
        ▼
onValueChange { vm.updateField("email", it) }
        │
        ▼
vm.updateField(field, value)
        │
        ▼
_state.update { it.copy(email = value) }
        │
        ▼
User clicks "Submit"
        │
        ▼
vm.submit() {
    // Validate BEFORE attempting operation
    val validation = ValidationUtils.validateEmail(email)
    
    if (validation !is ValidationResult.Valid) {
        _state.update { it.copy(error = validation.getErrorMessage()) }
        return  // Exit early, don't submit
    }
    
    // Validation passed, proceed
    repository.operation()
        .onSuccess { _state.update { it.copy(success = true) } }
        .onFailure { e -> _state.update { it.copy(error = e.getErrorMessage()) } }
}
        │
        ├─► Validation Failed
        │   _state.error = "Invalid email format"
        │   │
        │   ▼
        │   LaunchedEffect(state.error) { showError() }
        │   │
        │   ▼
        │   Toast: ❌ Invalid email format
        │
        └─► Validation Passed
            │
            ▼
            repository.operation()
            │
            ├─► Success
            │   _state.success = true
            │   │
            │   ▼
            │   Toast: ✅ Operation successful!
            │
            └─► Failure
                _state.error = e.getErrorMessage()
                │
                ▼
                Toast: ❌ [Error message]
```

---

## 🎯 Toast Message Types

```
┌─────────────────────────────────────────────────────┐
│            ToastUtils.showSuccess()                 │
│  ┌───────────────────────────────────────────┐     │
│  │ ✅ Account created successfully!          │     │
│  └───────────────────────────────────────────┘     │
│  Duration: SHORT (2 seconds)                       │
└─────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────┐
│            ToastUtils.showError()                   │
│  ┌───────────────────────────────────────────┐     │
│  │ ❌ Invalid email format                    │     │
│  └───────────────────────────────────────────┘     │
│  Duration: LONG (4 seconds)                        │
└─────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────┐
│            ToastUtils.showWarning()                 │
│  ┌───────────────────────────────────────────┐     │
│  │ ⚠️ Group suspended due to unpaid fees     │     │
│  └───────────────────────────────────────────┘     │
│  Duration: LONG (4 seconds)                        │
└─────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────┐
│            ToastUtils.showInfo()                    │
│  ┌───────────────────────────────────────────┐     │
│  │ ℹ️ Payment processing                      │     │
│  └───────────────────────────────────────────┘     │
│  Duration: SHORT (2 seconds)                       │
└─────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────┐
│           ToastUtils.showProcessing()               │
│  ┌───────────────────────────────────────────┐     │
│  │ ⏳ Signing you in...                       │     │
│  └───────────────────────────────────────────┘     │
│  Duration: SHORT (2 seconds)                       │
└─────────────────────────────────────────────────────┘
```

---

## 📱 Example: Complete Payment Flow

```
1. USER ENTERS CARD DETAILS
        │
        ▼
   TextField updates state
        │
        ▼
   vm.updateField("cardNumber", value)
        │
        ▼
2. USER CLICKS "PAY"
        │
        ▼
   vm.processPayment(type, amount, groupId, card, expiry, cvv)
        │
        ▼
   Validation Phase:
   ValidationUtils.validatePaymentFields(card, expiry, cvv)
        │
        ├─► Invalid (e.g., card="123")
        │   │
        │   ▼
        │   ValidationResult.Error("Invalid card number")
        │   │
        │   ▼
        │   _state.update { it.copy(error = "Invalid card number") }
        │   │
        │   ▼
        │   return  ← Exit early, don't process payment
        │   │
        │   ▼
        │   LaunchedEffect(state.error) { showError(context, it) }
        │   │
        │   ▼
        │   Toast: ❌ Invalid card number
        │
        └─► Valid
            │
            ▼
            _state.update { it.copy(isProcessing = true, error = null) }
            │
            ▼
            paymentRepo.recordPayment(payment)
            │
            ├─► Success
            │   │
            │   ▼
            │   _state.update { it.copy(isProcessing = false, isSuccess = true) }
            │   │
            │   ▼
            │   LaunchedEffect(state.isSuccess) {
            │       ToastUtils.showSuccess(context, "Payment successful!")
            │       onPaymentComplete()
            │   }
            │   │
            │   ▼
            │   Toast: ✅ Payment successful!
            │   Navigate to next screen
            │
            └─► Failure
                │
                ▼
                exception.getErrorMessage()
                │
                ▼
                _state.update { it.copy(isProcessing = false, error = msg) }
                │
                ▼
                LaunchedEffect(state.error) {
                    ToastUtils.showError(context, it)
                }
                │
                ▼
                Toast: ❌ [User-friendly error message]
                Stay on payment screen
```

---

## 🔒 Error Containment Strategy

```
┌─────────────────────────────────────┐
│     REPOSITORY LAYER                │
│  (May throw exceptions)             │
│  ▲ getGroupById()                   │
│  ▲ recordPayment()                  │
└────────────┬────────────────────────┘
             │ throws Exception
             │
             ▼
┌─────────────────────────────────────┐
│    VIEWMODEL LAYER                  │
│  (Catch & Convert)                  │
│                                     │
│  repo.operation()                   │
│    .onFailure { e →                 │
│      val msg = e.getErrorMessage()  │
│      _state.update {                │
│        it.copy(error = msg)         │
│      }                              │
│    }                                │
│                                     │
│  Exception is NEVER thrown          │
│  beyond this point!                 │
└────────────┬────────────────────────┘
             │ state.error: String?
             │
             ▼
┌─────────────────────────────────────┐
│     SCREEN LAYER                    │
│  (Display Feedback)                 │
│                                     │
│  LaunchedEffect(state.error) {      │
│    state.error?.let {               │
│      ToastUtils.showError(          │
│        context, it                  │
│      )                              │
│    }                                │
│  }                                  │
│                                     │
│  No exceptions, only messages!      │
└─────────────────────────────────────┘

KEY PRINCIPLE:
- Exceptions are caught at ViewModel layer
- Only safe strings reach UI layer
- UI layer never crashes due to backend errors
- All errors are logged for debugging
```

---

## 🎓 Learning Path

```
1. UNDERSTAND VALIDATION
   └─ Read: ValidationUtils.kt
   └ Learn: ValidationResult sealed class
   └─ Practice: Add new validation rule

2. UNDERSTAND ERROR CONVERSION
   └─ Read: SafeResultExtensions.kt
   └─ Learn: Exception → String mapping
   └─ Practice: Call exception.getErrorMessage()

3. UNDERSTAND TOAST MESSAGING
   └─ Read: ToastUtils.kt
   └─ Learn: 6 different toast types
   └─ Practice: Show toast in screen

4. UNDERSTAND FULL FLOW
   └─ Read: PaymentViewModel + PaymentScreen
   └─ Learn: Complete error handling pattern
   └─ Practice: Implement new form with validation

5. APPLY TO NEW FEATURES
   └─ Copy validation pattern
   └─ Add toasts for user feedback
   └─ Reuse error handling code
```

---

## 📋 Checklist: Is Your Code Error-Safe?

```
VIEWMODEL
☑ Imports ValidationUtils, ValidationResult, getErrorMessage
☑ Validates BEFORE calling repository
☑ Uses ValidationResult.Valid check (not isValid())
☑ Calls e.getErrorMessage() on errors
☑ Updates state.error with error message
☑ Never throws exceptions to UI
☑ Has try-catch for unexpected errors
☑ Logs errors with context tag

SCREEN
☑ Imports ToastUtils, LocalContext
☑ Has LaunchedEffect(state.error) { showError() }
☑ Has LaunchedEffect(state.success) { showSuccess() }
☑ Shows error via InfoBox if needed
☑ Never catches exceptions
☑ Never calls Toast.makeText() directly
☑ Never displays raw exception messages

REPOSITORY
☑ Returns Result<T>
☑ All exceptions are caught
☑ Logging happens here
☑ ViewModel handles failures

DATABASE
☑ Is read-only transaction
☑ Errors propagate to repository
```

This architecture ensures errors are handled at the right layer!

