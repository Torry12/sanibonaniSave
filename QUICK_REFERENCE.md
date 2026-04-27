# 🚀 Quick Reference Guide - SanibonaniSave Development

## 📍 Finding What You Need

### I need to...

#### **Understand the Architecture**
→ Read: `AGENTS.md`  
Key sections: Architecture Overview, Critical Patterns 1-12

#### **Implement Group Registration**
→ Read: `REGISTRATION_FLOW.md`  
Key sections: High-Level Flow, Step-by-Step Details, Supabase Tables

#### **Write Tests**
→ Read: `TESTING_AND_ERROR_HANDLING.md`  
Key sections: Unit Tests, Integration Tests, Testing Checklist

#### **Handle Errors Properly**
→ Read: `TESTING_AND_ERROR_HANDLING.md` → Error Handling Best Practices

#### **Debug Enum Serialization**
→ Read: `AGENTS.md` → Pattern 1: AdminFeeState Enum Serialization

#### **Fix a Bug**
1. Check `IMPLEMENTATION_STATUS.md` → Common Issues section
2. Search code with grep: `grep_search` tool
3. Review similar patterns in AGENTS.md

---

## ⚡ Quick Code Snippets

### Creating a Group
```kotlin
val group = Group(
    name = "Ubuntu Burial Society",
    type = GroupType.BURIAL_SOCIETY,
    description = "Community burial fund",
    province = "Gauteng",
    city = "Johannesburg",
    township = "Soweto",
    joiningFee = 100.0,
    monthlyContribution = 50.0,
    lateFee = 10.0,
    maxMembers = 50
)

groupRepo.createGroup(group, "admin@example.com", "password123")
    .onSuccess { groupId ->
        println("Group created: $groupId")
        // Platform fee auto-initialized
        // Admin user auto-created
    }
    .onFailure { e ->
        println("Error: ${e.message}")
    }
```

### Validating User Input
```kotlin
// Validate name
val nameValidation = InputValidator.validateName(userInput)
if (nameValidation is ValidationResult.Valid) {
    // Proceed
} else {
    showError(nameValidation.getErrorMessage())
}

// Validate email
val emailValidation = InputValidator.validateEmail(userInput)
```

### Updating Fee Status
```kotlin
// ✅ CORRECT: Use enum directly
groupRepo.updateFeeStatus(groupId, AdminFeeState.DUE)

// ❌ WRONG: Don't convert to string manually
// groupRepo.updateFeeStatus(groupId, "due")  // NO!
```

### Handling Result<T>
```kotlin
repo.doSomething().onSuccess { data ->
    // Handle success
}.onFailure { error ->
    // Handle error - error is Throwable
    val message = error.getErrorMessage() ?: "Unknown error"
    state.copy(error = message)
}
```

### Using StateFlow
```kotlin
// Update state immutably
_state.update { currentState ->
    currentState.copy(
        field = newValue,
        isLoading = false,
        error = null
    )
}

// Collect in Composable
val state by vm.state.collectAsState()
```

### Logging
```kotlin
// Info
AppLogger.i("Tag", "This happened")

// Warning
AppLogger.w("Tag", "This might be a problem")

// Error (with exception)
AppLogger.e("Tag", "Something failed: ${e.message}", e)

// Debug (dev only)
AppLogger.d("Tag", "Debug info")
```

---

## 🔑 Critical Constants & Values

| Item | Value | File |
|------|-------|------|
| Platform fee per member | R50 | PlatformFees.MONTHLY_PER_MEMBER |
| Registration fee | R700 | Hard-coded in PaymentScreen |
| Min group name length | 3 chars | InputValidator |
| Max group name length | 100 chars | InputValidator |
| Min password length | 8 chars | ValidationUtils |
| SA ID number length | 13 digits | MemberValidation |
| Phone min/max | 10-15 digits | MemberValidation |

---

## 🗂️ File Organization

```
src/main/java/com/sanibonani/save/

├── data/
│   ├── model/Models.kt           ← Domain models + enums
│   ├── validation/
│   │   ├── InputValidator.kt     ← Validation functions
│   │   └── ValidationUtils.kt    ← Composite validators
│   ├── local/SanibonaniDatabase.kt ← Room entities + DAOs
│   ├── repository/
│   │   └── Repositories.kt       ← All 6 repositories
│   └── remote/SupabaseManager.kt ← Auth management
│
├── viewmodel/ViewModels.kt       ← All 5 ViewModels
│
├── ui/screens/
│   ├── group/GroupScreens.kt     ← Registration form
│   ├── member/MemberScreens.kt   ← Member portal
│   ├── payment/PaymentScreen.kt  ← YoCo integration
│   ├── landing/LandingScreen.kt  ← Home/landing
│   ├── browse/BrowseGroupsScreen.kt ← Public groups
│   └── admin/AdminDashboardScreen.kt ← Admin tools
│
└── di/AppModule.kt               ← Dependency injection
```

---

## 🧪 Running Tests

```bash
# All tests
./gradlew test

# Specific test class
./gradlew test --tests InputValidatorTest

# With logging
./gradlew test --info

# Clean rebuild
./gradlew clean build -x test
```

---

## 🐛 Debugging Common Issues

### "ValidationResult does not exist"
**Cause**: Duplicate import or missing import  
**Fix**: Use `import com.sanibonani.save.data.validation.ValidationResult`

### "AdminFeeState enum constant not found"
**Cause**: Typo (e.g., `AdminFeeState.Due` instead of `AdminFeeState.DUE`)  
**Fix**: Check enum names are UPPERCASE

### "Failed to create group" with "already exists"
**Cause**: Admin email already in Supabase Auth  
**Fix**: Use unique email or implement "already exists" handler

### "Platform fee not created"
**Cause**: No platform fee record in Supabase  
**Fix**: Check if `try { ... }` block failed; check logs for warning

### "Room cache shows stale data"
**Cause**: Room not updated after network success  
**Fix**: Verify `db.groupDao().upsertGroup()` is called

### "Offline mode shows nothing"
**Cause**: Room cache fallback not triggered or empty  
**Fix**: Add data to Room during online sync first

---

## 📝 Code Style Guide

### Naming
```kotlin
// Functions: camelCase, verb-first for actions
fun validateGroupName(name: String): ValidationResult
fun loadGroups()
fun updateField(field: String, value: Any)

// Variables: camelCase
val groupId = "..."
var isLoading = false

// Constants: UPPER_SNAKE_CASE
const val MAX_NAME_LENGTH = 100
const val REGISTRATION_FEE = 700.0

// Enums: UPPER_CASE for values
enum class AdminFeeState { PAID, DUE, WARNING }

// Sealed classes: PascalCase for subtypes
sealed class Screen
data object GroupProfile : Screen()
```

### Error Handling
```kotlin
// ✅ Always use Result<T>
override suspend fun doSomething(): Result<String> = runCatching {
    // implementation
}

// ✅ Log before returning error
AppLogger.e("Repository", "Failed to X: ${e.message}", e)

// ✅ Safe navigation with onSuccess/onFailure
result.onSuccess { /* handle */ }.onFailure { /* handle */ }

// ❌ Don't throw to UI layer
// throw Exception("error")  // NO!
```

### StateFlow Updates
```kotlin
// ✅ Immutable updates
_state.update { it.copy(field = value) }

// ❌ Don't mutate
// _state.value.field = value  // NO!
```

---

## 🔗 Important URLs & Resources

- **Supabase Dashboard**: https://app.supabase.com/
- **YoCo Developer Docs**: https://yoco.com/developers/
- **Kotlin Serialization**: https://github.com/Kotlin/kotlinx.serialization/
- **Jetpack Compose**: https://developer.android.com/jetpack/compose/
- **Room Database**: https://developer.android.com/training/data-storage/room/

---

## 🆘 Getting Help

### I found a bug
1. Check IMPLEMENTATION_STATUS.md → Common Issues
2. Search code for similar patterns
3. Review git history for context
4. Add logging and reproduce

### I'm stuck on a feature
1. Find similar feature in codebase
2. Copy pattern (Group registration, Member joining, etc.)
3. Adapt for your use case
4. Test thoroughly

### I need to add a new screen
1. Copy similar screen from `ui/screens/`
2. Create ViewModel in `ViewModels.kt`
3. Add route to `NavGraph.kt`
4. Wire navigation
5. Test navigation flow

### Code is not compiling
1. Check error message for file:line
2. Run `./gradlew clean build`
3. Invalidate caches in IDE
4. Check imports are correct
5. Look for typos in enum names

---

## ✅ Pre-Commit Checklist

Before committing code:

- [ ] Code compiles: `./gradlew build`
- [ ] Errors: `get_errors()` tool shows 0 critical errors
- [ ] Tests pass: `./gradlew test` (if applicable)
- [ ] No sensitive data (keys, tokens, passwords)
- [ ] Comments explain complex logic
- [ ] Error handling complete (try-catch, logging)
- [ ] ValidationResult used for validation
- [ ] Result<T> returned from repositories
- [ ] StateFlow.update() used for state changes
- [ ] Documentation updated if new pattern

---

## 🎓 Learning Path

**Day 1: Understand Architecture**
1. Read AGENTS.md → Architecture Overview
2. Explore file structure
3. Review Models.kt for data types

**Day 2: Study Registration Flow**
1. Read REGISTRATION_FLOW.md
2. Trace code from UI → ViewModel → Repository
3. Understand how Supabase records are created

**Day 3: Implement a Feature**
1. Pick a small feature (e.g., member profile view)
2. Follow registration flow as template
3. Create Screen, ViewModel, Repository
4. Wire into NavGraph
5. Test thoroughly

**Day 4: Testing & Debugging**
1. Read TESTING_AND_ERROR_HANDLING.md
2. Write unit tests for validation
3. Write integration tests for repository
4. Debug actual device/emulator

**Day 5: Polish & Documentation**
1. Add error messages
2. Improve UI/UX
3. Document code
4. Review AGENTS.md patterns
5. Commit and deploy

---

## 🚀 Next Steps

1. **Build the project**
   ```bash
   cd /path/to/SanibonaniSave_Full
   ./gradlew clean build -x test
   ```

2. **Run on emulator/device**
   - Open in Android Studio
   - Select target device
   - Click "Run" or press Shift+F10

3. **Test registration flow**
   - Register Group with test data
   - Verify group created in Supabase
   - Complete YoCo payment
   - Check group status activated

4. **Review logs**
   - Logcat should show successful operations
   - No ERROR-level messages
   - Platform fee initialization logged

5. **Fix any issues**
   - Check error messages
   - Review IMPLEMENTATION_STATUS.md
   - Search for similar code patterns
   - Apply fixes following AGENTS.md patterns

---

*This guide should answer 90% of your questions. For the rest, check AGENTS.md, REGISTRATION_FLOW.md, or search the code.*

**Last Updated**: March 24, 2026

