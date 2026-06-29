package com.sanibonani.save.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanibonani.save.analytics.AnalyticsTaxonomy
import com.sanibonani.save.analytics.AppAnalytics
import com.sanibonani.save.data.logging.AppLogger
import com.sanibonani.save.data.utils.toUserMessage
import com.sanibonani.save.domain.model.UserRole
import com.sanibonani.save.domain.repository.CredentialsRepository
import com.sanibonani.save.domain.repository.SupabaseRepository
import com.sanibonani.save.domain.utils.AuthIdentityUtils
import com.sanibonani.save.domain.utils.UserRoleMapper
import com.sanibonani.save.domain.validation.ValidationResult
import com.sanibonani.save.domain.validation.ValidationUtils
import com.sanibonani.save.service.AdminGroupContextCacheService
import com.sanibonani.save.service.MemberGroupContextCacheService
import com.sanibonani.save.service.UserProfileCacheService
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI state for authentication flows.
 * errorType: null, "network", "invalid_credentials", "generic" for granular UI feedback.
 */
data class AuthState(
    val email: String = "",
    val fullName: String = "",
    val password: String = "",
    val confirmPw: String = "",
    val isLoading: Boolean = false,
    val isLoggedIn: Boolean = false,
    val userRole: UserRole = UserRole.MEMBER,
    val error: String? = null,
    val errorType: String? = null,
    val navigateTo: String? = null,
    val rememberMe: Boolean = false,
    val isNewRegistration: Boolean = false,
    val isResettingPassword: Boolean = false,
    val biometricEnabled: Boolean = false,
    val hasSavedCredentials: Boolean = false
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val supabaseRepo: SupabaseRepository,
    private val credentialsRepo: CredentialsRepository,
    private val adminGroupContextCacheService: AdminGroupContextCacheService,
    private val memberGroupContextCacheService: MemberGroupContextCacheService,
    private val userProfileCacheService: UserProfileCacheService
) : ViewModel() {

    private val _state = MutableStateFlow(AuthState())
    val state: StateFlow<AuthState> = _state.asStateFlow()

    private fun buildSignedOutState(current: AuthState? = null): AuthState {
        val savedEmail = credentialsRepo.getSavedEmail()
        val remember = credentialsRepo.getRememberMe()
        val biometric = credentialsRepo.isBiometricEnabled()
        val hasCreds = credentialsRepo.hasSavedCredentials()

        return AuthState(
            email = if (hasCreds && savedEmail.isNotBlank()) savedEmail else "",
            rememberMe = remember,
            biometricEnabled = biometric && remember && hasCreds,
            hasSavedCredentials = hasCreds,
            isResettingPassword = current?.isResettingPassword == true
        )
    }

    init {
        loadSavedCredentials()
        observeSession()
    }

    private fun loadSavedCredentials() {
        val savedEmail = credentialsRepo.getSavedEmail()
        val savedPass = credentialsRepo.getSavedPassword()
        val remember = credentialsRepo.getRememberMe()
        val biometric = credentialsRepo.isBiometricEnabled()
        val hasCreds = credentialsRepo.hasSavedCredentials()

		// Always surface the saved email if credentials exist (for biometric sign-in hint),
		// but only prefill the password field when Remember Me is enabled.
		_state.update {
			it.copy(
				email = if (hasCreds && savedEmail.isNotBlank()) savedEmail else it.email,
				password = if (remember && savedEmail.isNotBlank()) savedPass else it.password,
				rememberMe = remember,
                biometricEnabled = biometric && remember && hasCreds,
				hasSavedCredentials = hasCreds
			)
		}

        if ((!remember || !hasCreds) && biometric) {
            credentialsRepo.setBiometricEnabled(false)
        }
    }

    private fun saveCredentials() {
        val s = _state.value
		if (!s.rememberMe) return

		credentialsRepo.saveCredentials(s.email, s.password, true)
		_state.update {
			it.copy(
				hasSavedCredentials = true,
				biometricEnabled = credentialsRepo.isBiometricEnabled()
			)
		}
    }

    private fun clearSavedLoginData() {
        credentialsRepo.clearCredentials()
        _state.update {
            it.copy(
                biometricEnabled = false,
                hasSavedCredentials = false,
                password = "",
                error = null,
                errorType = null
            )
        }
    }

    fun toggleBiometric(enabled: Boolean) {
        if (enabled && (!_state.value.rememberMe || !credentialsRepo.hasSavedCredentials())) {
            _state.update {
                it.copy(error = "Turn on Remember Me and sign in with your password first.")
            }
            return
        }
        credentialsRepo.setBiometricEnabled(enabled)
        _state.update { it.copy(biometricEnabled = enabled) }
    }

    /**
     * Observes Supabase session status and orchestrates state/caches accordingly.
     * On login: warms up group/member context caches, resolves user role.
     * On logout: clears all caches and resets state.
     */
    private fun observeSession() {
        viewModelScope.launch {
            supabaseRepo.sessionStatus.collect { status ->
                when (status) {
                    is SessionStatus.Authenticated -> {
                        supabaseRepo.currentUserId?.let { userId ->
                            // Warm member/group contexts as soon as the session is authenticated.
                            launch { memberGroupContextCacheService.warmUpForUser(userId) }
                            launch { adminGroupContextCacheService.warmUpForUser(userId) }
                        }
                        // Cache basic profile for downstream form pre-fill
                        val sessionUser = supabaseRepo.currentSession?.user
                        val cachedEmail   = sessionUser?.email ?: ""
                        val cachedName    = sessionUser?.userMetadata
                            ?.get("full_name")
                            ?.let { runCatching { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }.getOrNull() }
                            ?: ""
                        if (cachedEmail.isNotBlank() || cachedName.isNotBlank()) {
                            userProfileCacheService.save(cachedName, cachedEmail)
                        }

                        val role = runCatching { supabaseRepo.getUserRole() }.getOrDefault(UserRole.MEMBER)
                        AppLogger.d(
                            tag = "AuthViewModel",
                            message = "Session authenticated userId=${supabaseRepo.currentUserId} resolvedRole=$role"
                        )
                        _state.update { it.copy(
                            isLoggedIn = true,
                            userRole = role,
                            isLoading = false,
                            error = null,
                            errorType = null
                        ) }
                    }
                    is SessionStatus.NotAuthenticated -> {
                        AppLogger.d(tag = "AuthViewModel", message = "Session not authenticated. Clearing caches and auth state.")
                        adminGroupContextCacheService.clearForSignOut()
                        memberGroupContextCacheService.clearForSignOut()
                        userProfileCacheService.clear()
                        // Keep saved-login flags consistent so quick login does not clear credentials.
                        _state.update { buildSignedOutState(it) }
                    }
                    else -> {}
                }
            }
        }
    }

    fun updateEmail(email: String) {
        _state.update { it.copy(email = email, error = null) }
    }

    fun updateRememberMe(remember: Boolean) {
        _state.update { it.copy(rememberMe = remember) }
        if (!remember) {
            clearSavedLoginData()
        }
    }

    fun updateFullName(name: String) {
        _state.update { it.copy(fullName = name, error = null) }
    }

    fun updatePasswordState(password: String) {
        _state.update { it.copy(password = password, error = null) }
    }

    fun updateConfirmPw(confirmPw: String) {
        _state.update { it.copy(confirmPw = confirmPw, error = null) }
    }

    fun clearNavigation() {
        _state.update { it.copy(navigateTo = null) }
    }

    fun clearNewRegistrationFlag() {
        _state.update { it.copy(isNewRegistration = false) }
    }

    fun updateError(msg: String?) {
        _state.update { it.copy(error = msg) }
    }

    /**
     * Orchestrates login logic: normalizes credentials, calls Supabase, handles all error/success states.
     * Sets errorType for granular UI feedback.
     */
    fun signIn() {
        val s = _state.value
        val normalizedEmail = AuthIdentityUtils.normalizeEmail(s.email)
        val rawPassword = s.password
        AppAnalytics.track(
            AnalyticsTaxonomy.Events.AUTH_SIGN_IN_ATTEMPT,
            mapOf(
                AnalyticsTaxonomy.Params.ENTRY_POINT to if (rawPassword.isBlank()) "magic_link" else "password"
            )
        )
        AppLogger.d("AuthViewModel", "Sign-in attempt for $normalizedEmail")

        if (normalizedEmail.isBlank()) {
            _state.update { it.copy(error = "Please enter your email", errorType = "generic") }
            return
        }

        if (!AuthIdentityUtils.isPlausibleEmail(normalizedEmail)) {
            _state.update { it.copy(error = "Please enter a valid email address.", errorType = "invalid_credentials") }
            return
        }

        // Fix: Explicitly check if we have a password, otherwise enforce it for sign-in
        if (rawPassword.isBlank()) {
            _state.update { it.copy(error = "Please enter your password to sign in.", errorType = "invalid_credentials") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, errorType = null) }
            val result = supabaseRepo.signIn(normalizedEmail, rawPassword)
            result.onSuccess {
                val shouldRemember = _state.value.rememberMe
                if (shouldRemember) {
                    credentialsRepo.saveCredentials(normalizedEmail, rawPassword, true)
                    _state.update {
                        it.copy(
                            email = normalizedEmail,
                            rememberMe = true,
                            hasSavedCredentials = true,
                            biometricEnabled = credentialsRepo.isBiometricEnabled()
                        )
                    }
                } else {
                    clearSavedLoginData()
                }
                _state.update { it.copy(isLoading = false, email = normalizedEmail, errorType = null) }
                AppAnalytics.track(
                    AnalyticsTaxonomy.Events.AUTH_SIGN_IN_SUCCESS,
                    mapOf(AnalyticsTaxonomy.Params.ENTRY_POINT to "password")
                )
            }.onFailure { e ->
                val msg = e.toUserMessage()
                val errorType = when {
                    msg.contains("network", ignoreCase = true) -> "network"
                    msg.contains("credentials", ignoreCase = true) || msg.contains("password", ignoreCase = true) -> "invalid_credentials"
                    else -> "generic"
                }
                _state.update { it.copy(isLoading = false, error = msg, errorType = errorType) }
                AppAnalytics.track(
                    AnalyticsTaxonomy.Events.AUTH_SIGN_IN_FAILURE,
                    mapOf(AnalyticsTaxonomy.Params.ERROR_TYPE to errorType)
                )
            }
        }
    }


    fun signUp(role: UserRole = UserRole.MEMBER) {
        val s = _state.value
        val normalizedEmail = AuthIdentityUtils.normalizeEmail(s.email)
        AppAnalytics.track(
            AnalyticsTaxonomy.Events.AUTH_SIGN_UP_ATTEMPT,
            mapOf(AnalyticsTaxonomy.Params.ROLE to role.name.lowercase())
        )
        if (normalizedEmail.isBlank() || s.fullName.isBlank() || s.password.isBlank() || s.confirmPw.isBlank()) {
            _state.update { it.copy(error = "Please fill in all fields") }
            return
        }

        if (!AuthIdentityUtils.isPlausibleEmail(normalizedEmail)) {
            _state.update { it.copy(error = "Please enter a valid email address") }
            return
        }

        if (s.fullName.length < 3) {
            _state.update { it.copy(error = "Full Name must be at least 3 characters") }
            return
        }

        val passwordValidation = ValidationUtils.validatePasswordField(s.password)
        if (passwordValidation !is ValidationResult.Valid) {
            _state.update { it.copy(error = passwordValidation.getErrorMessage()) }
            return
        }

        if (s.password != s.confirmPw) {
            _state.update { it.copy(error = "Passwords do not match") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val roleStr = UserRoleMapper.toStorageValue(role)
            supabaseRepo.signUp(
                email = normalizedEmail,
                password = s.password,
                metadata = mapOf(
                    "role" to roleStr,
                    "full_name" to s.fullName
                )
            )
                .onSuccess {
                    // Cache basic profile immediately so downstream forms can pre-fill
                    userProfileCacheService.save(
                        fullName = s.fullName.trim(),
                        email    = normalizedEmail
                    )
                    _state.update {
                        it.copy(
                            isLoading = false,
                            isNewRegistration = true,
                            email = normalizedEmail,
                            userRole = role
                        )
                    }
                    AppAnalytics.track(
                        AnalyticsTaxonomy.Events.AUTH_SIGN_UP_SUCCESS,
                        mapOf(AnalyticsTaxonomy.Params.ROLE to role.name.lowercase())
                    )
                }
                .onFailure { e ->
					_state.update { it.copy(isLoading = false, error = e.toUserMessage()) }
                    AppAnalytics.track(
                        AnalyticsTaxonomy.Events.AUTH_SIGN_UP_FAILURE,
                        mapOf(AnalyticsTaxonomy.Params.ROLE to role.name.lowercase())
                    )
                }
        }
    }

    /**
     * Signs out the user, clears all caches, and resets UI state for security.
     */
    fun signOut() {
        viewModelScope.launch {
            adminGroupContextCacheService.clearForSignOut()
            memberGroupContextCacheService.clearForSignOut()
            userProfileCacheService.clear()
            supabaseRepo.signOut()
            _state.update { buildSignedOutState(it) }
        }
    }

    fun updatePassword() {
        val s = _state.value
        if (s.password.isBlank() || s.confirmPw.isBlank()) {
            _state.update { it.copy(error = "Please fill in all fields") }
            return
        }

        val passwordValidation = ValidationUtils.validatePasswordField(s.password)
        if (passwordValidation !is ValidationResult.Valid) {
            _state.update { it.copy(error = passwordValidation.getErrorMessage()) }
            return
        }

        if (s.password != s.confirmPw) {
            _state.update { it.copy(error = "Passwords do not match") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            
            // Wait for session status to be Authenticated (with timeout)
            // This handles the gap between deep link import and state update
            var attempts = 0
            while (!supabaseRepo.isLoggedIn && attempts < 10) {
                AppLogger.d("AuthViewModel", "Waiting for session... attempt $attempts")
                kotlinx.coroutines.delay(500)
                attempts++
            }

            if (!supabaseRepo.isLoggedIn) {
                AppLogger.e("AuthViewModel", "Update password failed: No session after waiting.")
                _state.update { it.copy(
                    isLoading = false, 
                    error = "No active session found. Your reset link may have expired or is invalid. Please try requesting a new reset link."
                ) }
                return@launch
            }

            AppLogger.d("AuthViewModel", "Updating password for userId=${supabaseRepo.currentUserId}")
            supabaseRepo.updatePassword(s.password)
                .onSuccess {
                    AppLogger.d("AuthViewModel", "Password updated successfully!")
                    // If they have Remember Me enabled, update the saved password so biometric/quick login keeps working.
                    if (s.rememberMe && s.email.isNotBlank()) {
                        credentialsRepo.saveCredentials(AuthIdentityUtils.normalizeEmail(s.email), s.password, true)
                    } else {
                        // Otherwise clear old invalid credentials
                        clearSavedLoginData()
                    }
                    _state.update { it.copy(isLoading = false, navigateTo = "login", isResettingPassword = false) }
                }
                .onFailure { e ->
                    val errorMsg = e.toUserMessage()
                    AppLogger.e("AuthViewModel", "Update password failed: $errorMsg", e)
                    val finalMsg = if (errorMsg.contains("claim", ignoreCase = true)) {
                        "Session authentication failed. Please request a new reset link."
                    } else {
                        errorMsg
                    }
                    _state.update { it.copy(isLoading = false, error = finalMsg) }
                }
        }
    }

    fun quickLogin() {
    if (!_state.value.biometricEnabled) {
      _state.update {
        it.copy(error = "Biometric login is not enabled. Enable 'Remember Me' first to use biometric login.")
      }
      return
    }

        val savedEmail = credentialsRepo.getSavedEmail()
		val savedPassword = credentialsRepo.getSavedPassword()

		if (savedEmail.isBlank() || savedPassword.isBlank()) {
			_state.update {
				it.copy(error = "Saved credentials not found. Please log in with your password and enable Remember Me.")
			}
			credentialsRepo.setBiometricEnabled(false)
			return
		}

		// Ensure signIn() runs with the saved credentials
    _state.update { it.copy(email = savedEmail, password = savedPassword, rememberMe = true, error = null) }
		signIn()
    }

    fun handleDeepLink(url: String) {
        viewModelScope.launch {
            AppLogger.d("AuthViewModel", "Handling deep link: $url")
            if (url.contains("reset-password")) {
                _state.update { it.copy(isResettingPassword = true) }
            }
            supabaseRepo.handleDeepLink(url)
                .onFailure { e ->
                    AppLogger.e("AuthViewModel", "Failed to handle deep link", e)
                    _state.update { it.copy(error = "Invalid or expired reset link. Please request a new one.", isResettingPassword = false) }
                }
        }
    }
}
