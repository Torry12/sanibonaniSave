# Code Changes: Before & After

## File 1: AuthViewModel.kt

### Change 1: Add isNewRegistration flag to AuthState

**BEFORE**:
```kotlin
data class AuthState(
    val email: String = "",
    val fullName: String = "",
    val password: String = "",
    val confirmPw: String = "",
    val isLoading: Boolean = false,
    val isLoggedIn: Boolean = false,
    val userRole: UserRole = UserRole.MEMBER,
    val error: String? = null,
    val navigateTo: String? = null,
    val rememberMe: Boolean = false
)
```

**AFTER**:
```kotlin
data class AuthState(
    val email: String = "",
    val fullName: String = "",
    val password: String = "",
    val confirmPw: String = "",
    val isLoading: Boolean = false,
    val isLoggedIn: Boolean = false,
    val userRole: UserRole = UserRole.MEMBER,
    val error: String? = null,
    val navigateTo: String? = null,
    val rememberMe: Boolean = false,
    val isNewRegistration: Boolean = false  // ← NEW FIELD
)
```

---

### Change 2: Add clearNewRegistrationFlag() method

**BEFORE**:
```kotlin
fun clearNavigation() {
    _state.update { it.copy(navigateTo = null) }
}

fun updateError(msg: String?) {
    _state.update { it.copy(error = msg) }
}
```

**AFTER**:
```kotlin
fun clearNavigation() {
    _state.update { it.copy(navigateTo = null) }
}

fun clearNewRegistrationFlag() {  // ← NEW METHOD
    _state.update { it.copy(isNewRegistration = false) }
}

fun updateError(msg: String?) {
    _state.update { it.copy(error = msg) }
}
```

---

### Change 3: Update signUp() to set isNewRegistration flag

**BEFORE**:
```kotlin
fun signUp(role: UserRole = UserRole.MEMBER) {
    // ... validation code ...
    
    viewModelScope.launch {
        _state.update { it.copy(isLoading = true, error = null) }
        supabaseRepo.signUp(
            email = s.email, 
            password = s.password, 
            metadata = mapOf(
                "role" to roleStr,
                "full_name" to s.fullName
            )
        )
            .onSuccess {
                _state.update { it.copy(isLoading = false) }  // ← OLD
            }
            .onFailure { e ->
                _state.update { it.copy(isLoading = false, error = e.message ?: "Registration failed") }
            }
    }
}
```

**AFTER**:
```kotlin
fun signUp(role: UserRole = UserRole.MEMBER) {
    // ... validation code ...
    
    viewModelScope.launch {
        _state.update { it.copy(isLoading = true, error = null) }
        supabaseRepo.signUp(
            email = s.email, 
            password = s.password, 
            metadata = mapOf(
                "role" to roleStr,
                "full_name" to s.fullName
            )
        )
            .onSuccess {
                _state.update { it.copy(isLoading = false, isNewRegistration = true) }  // ← UPDATED
            }
            .onFailure { e ->
                _state.update { it.copy(isLoading = false, error = e.message ?: "Registration failed") }
            }
    }
}
```

---

## File 2: NavGraph.kt

### Change: Update LaunchedEffect to handle new registrations

**BEFORE**:
```kotlin
@Composable
fun SanibonaniNavGraph(
    navController: NavHostController = rememberNavController(),
    supabaseRepo: SupabaseRepository
) {
    val authViewModel: AuthViewModel = hiltViewModel()
    val authState by authViewModel.state.collectAsState()

    // Global session observer for forced navigation
    LaunchedEffect(authState.isLoggedIn, authState.userRole) {  // ← OLD DEPENDENCIES
        val currentRoute = navController.currentDestination?.route
        if (currentRoute == null) return@LaunchedEffect
        
        if (authState.isLoggedIn) {
            // Platform Admins go straight to their dashboard...
            if (authState.userRole == UserRole.PLATFORM_ADMIN) {
                // ... platform admin logic ...
            } else if (currentRoute == Screen.Login.route || 
                       currentRoute == Screen.Register.route || 
                       (currentRoute == Screen.UpdatePassword.route && authState.navigateTo != "login")) {
                // If we've already set navigateTo to "login", don't auto-redirect to dashboards yet.
                if (authState.navigateTo != "login") {
                    val dest = when (authState.userRole) {
                        UserRole.GROUP_ADMIN -> Screen.AdminDashboard.withId(null)
                        UserRole.MEMBER -> Screen.MemberDashboard.withTab(0, null)
                        else -> Screen.Landing.route
                    }
                    navController.navigate(dest) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }
        } else {
            // If not logged in and on a protected screen, go to Landing
            // ... rest of logic ...
        }
    }
    // ... rest of NavGraph ...
}
```

**AFTER**:
```kotlin
@Composable
fun SanibonaniNavGraph(
    navController: NavHostController = rememberNavController(),
    supabaseRepo: SupabaseRepository
) {
    val authViewModel: AuthViewModel = hiltViewModel()
    val authState by authViewModel.state.collectAsState()

    // Global session observer for forced navigation
    LaunchedEffect(authState.isLoggedIn, authState.userRole, authState.isNewRegistration) {  // ← UPDATED DEPENDENCIES
        val currentRoute = navController.currentDestination?.route
        if (currentRoute == null) return@LaunchedEffect
        
        if (authState.isLoggedIn) {
            // Platform Admins go straight to their dashboard...
            if (authState.userRole == UserRole.PLATFORM_ADMIN) {
                // ... platform admin logic ...
            } else if (currentRoute == Screen.Login.route || 
                       currentRoute == Screen.Register.route || 
                       (currentRoute == Screen.UpdatePassword.route && authState.navigateTo != "login")) {
                // If we've already set navigateTo to "login", don't auto-redirect to dashboards yet.
                // If this is a NEW REGISTRATION, redirect to Landing page, not dashboard
                if (authState.navigateTo != "login" && !authState.isNewRegistration) {  // ← UPDATED CONDITION
                    val dest = when (authState.userRole) {
                        UserRole.GROUP_ADMIN -> Screen.AdminDashboard.withId(null)
                        UserRole.MEMBER -> Screen.MemberDashboard.withTab(0, null)
                        else -> Screen.Landing.route
                    }
                    navController.navigate(dest) {
                        popUpTo(0) { inclusive = true }
                    }
                } else if (authState.isNewRegistration && currentRoute == Screen.Register.route) {  // ← NEW BLOCK
                    // New registration: go to Landing page
                    navController.navigate(Screen.Landing.route) {
                        popUpTo(0) { inclusive = true }
                    }
                    authViewModel.clearNewRegistrationFlag()
                }
            }
        } else {
            // If not logged in and on a protected screen, go to Landing
            // ... rest of logic ...
        }
    }
    // ... rest of NavGraph ...
}
```

---

## File 3: AuthScreens.kt (RegisterScreen)

### Change: Add form validation and back button protection

**BEFORE**:
```kotlin
@Composable
fun RegisterScreen(
    onRegistered : () -> Unit,
    onBack       : () -> Unit,
    vm           : AuthViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(state.isLoggedIn) {
        if (state.isLoggedIn) {
            ToastUtils.showSuccess(context, "Account created successfully!")
            onRegistered()
        }
    }

    LaunchedEffect(state.error) {
        state.error?.let { ToastUtils.showError(context, it) }
    }

    Scaffold(topBar = { SanibonaniTopBar("Create Account", onBack = onBack) }) { padding ->
        Column(
            modifier            = Modifier
                .fillMaxSize()
                .background(Cream)
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Join SanibonaniSave", style = MaterialTheme.typography.headlineMedium, color = Forest)
            Text("Create your account to manage or join savings groups",
                style = MaterialTheme.typography.bodyMedium, color = MidGray)
            Spacer(Modifier.height(8.dp))
            
            // ... form fields ...
        }
    }
}
```

**AFTER**:
```kotlin
@Composable
fun RegisterScreen(
    onRegistered : () -> Unit,
    onBack       : () -> Unit,
    vm           : AuthViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(state.isLoggedIn) {
        if (state.isLoggedIn) {
            ToastUtils.showSuccess(context, "Account created successfully!")
            onRegistered()
        }
    }

    LaunchedEffect(state.error) {
        state.error?.let { ToastUtils.showError(context, it) }
    }

    // ← NEW: Determine if all required fields are filled
    val allFieldsFilled = state.fullName.length >= 3 &&
            state.email.isNotBlank() &&
            state.password.isNotBlank() &&
            state.confirmPw.isNotBlank() &&
            state.password == state.confirmPw

    // ← NEW: Prevent back navigation until all required fields are filled
    val onBackAction = if (allFieldsFilled) onBack else { { /* Cannot navigate back */ } }

    Scaffold(topBar = { SanibonaniTopBar("Create Account", onBack = onBackAction) }) { padding ->  // ← UPDATED
        Column(
            modifier            = Modifier
                .fillMaxSize()
                .background(Cream)
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ← NEW: Show warning if form incomplete
            if (!allFieldsFilled) {
                InfoBox(
                    "⚠️ Please fill in all required fields before leaving this form",
                    InfoType.WARNING
                )
            }
            
            Text("Join SanibonaniSave", style = MaterialTheme.typography.headlineMedium, color = Forest)
            Text("Create your account to manage or join savings groups",
                style = MaterialTheme.typography.bodyMedium, color = MidGray)
            Spacer(Modifier.height(8.dp))
            
            // ... form fields ...
        }
    }
}
```

---

## Summary of Changes

| File | Changes | Lines Added |
|------|---------|-------------|
| **AuthViewModel.kt** | Add flag, method, update function | +5 lines |
| **NavGraph.kt** | Add dependency, enhance logic | +10 lines |
| **AuthScreens.kt** | Add validation, back protection | +12 lines |
| **TOTAL** | 3 files, 3 targeted changes | ~27 lines |

---

## Key Takeaways

### Minimal, Focused Changes
- Only 3 files modified
- Total ~27 lines of code added
- No breaking changes to existing code
- Backward compatible with login flow

### Clean Architecture
- ViewModel handles state flag
- NavGraph handles navigation logic
- Screen handles UI feedback
- Clear separation of concerns

### Reactive State Management
- Flag set in signUp() success callback
- Navigation reacts to flag change
- Flag cleared after navigation
- UI updates reflect state in real-time

### Progressive Enhancement
- Enhancement layered on existing code
- Each change is independent
- Can be disabled by removing checks
- Non-invasive implementation


