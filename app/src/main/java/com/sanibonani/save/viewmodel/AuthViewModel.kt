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
import com.sanibonani.save.domain.utils.PlatformAdminAuthPolicy
import com.sanibonani.save.domain.utils.UserRoleMapper
import com.sanibonani.save.service.AdminGroupContextCacheService
import com.sanibonani.save.service.MemberGroupContextCacheService
import com.sanibonani.save.service.UserProfileCacheService
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.*
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
                        // Full state reset for security and clean UI
                        _state.update { AuthState(
                            biometricEnabled = credentialsRepo.isBiometricEnabled(),
                            hasSavedCredentials = credentialsRepo.hasSavedCredentials()
                        ) }
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
        val normalizedEmail = s.email.trim()
        val rawPassword = s.password.trim()
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
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, errorType = null) }
            val result = if (rawPassword.isBlank()) {
                AppAnalytics.track(AnalyticsTaxonomy.Events.AUTH_MAGIC_LINK_REQUESTED)
                supabaseRepo.signInWithMagicLink(normalizedEmail)
            } else {
                supabaseRepo.signIn(normalizedEmail, rawPassword)
            }
            result.onSuccess {
                if (s.password.isNotBlank()) {
                    // Only persist credentials when the user opted in.
                    if (s.rememberMe) {
                        credentialsRepo.saveCredentials(normalizedEmail, s.password, true)
                        _state.update {
                            it.copy(
                                email = normalizedEmail,
                                hasSavedCredentials = true,
                                biometricEnabled = credentialsRepo.isBiometricEnabled()
                            )
                        }
                    } else {
                        clearSavedLoginData()
                    }
                }
                if (s.password.isBlank()) {
                    _state.update { it.copy(isLoading = false, error = "Magic link sent to your email!", errorType = null) }
                } else {
                    _state.update { it.copy(isLoading = false, email = normalizedEmail, errorType = null) }
                }
                AppAnalytics.track(
                    AnalyticsTaxonomy.Events.AUTH_SIGN_IN_SUCCESS,
                    mapOf(AnalyticsTaxonomy.Params.ENTRY_POINT to if (s.password.isBlank()) "magic_link" else "password")
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


    fun signUp(role: UserRole = UserRole.PLATFORM_ADMIN) {
        val s = _state.value
        val normalizedEmail = s.email.trim()
        AppAnalytics.track(
            AnalyticsTaxonomy.Events.AUTH_SIGN_UP_ATTEMPT,
            mapOf(AnalyticsTaxonomy.Params.ROLE to role.name.lowercase())
        )
        if (normalizedEmail.isBlank() || s.fullName.isBlank() || s.password.isBlank() || s.confirmPw.isBlank()) {
            _state.update { it.copy(error = "Please fill in all fields") }
            return
        }

        if (s.fullName.length < 3) {
            _state.update { it.copy(error = "Full Name must be at least 3 characters") }
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
            // Full state reset for security and clean UI
            _state.update {
                AuthState(
                    biometricEnabled = credentialsRepo.isBiometricEnabled(),
                    hasSavedCredentials = credentialsRepo.hasSavedCredentials()
                )
            }
        }
    }

    fun updatePassword() {
        val s = _state.value
        if (s.password.isBlank() || s.confirmPw.isBlank()) {
            _state.update { it.copy(error = "Please fill in all fields") }
            return
        }

        if (s.password != s.confirmPw) {
            _state.update { it.copy(error = "Passwords do not match") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            
            // Check if we have an active session. If not, wait briefly for the deep link processing to finish.
            if (!supabaseRepo.isLoggedIn) {
                AppLogger.d("AuthViewModel", "No active session in updatePassword, waiting 2s for deep link import...")
                kotlinx.coroutines.delay(2000)
            }

            if (!supabaseRepo.isLoggedIn) {
                AppLogger.e("AuthViewModel", "Update password failed: No session after wait.")
                _state.update { it.copy(
                    isLoading = false, 
                    error = "No active session found. Your reset link may have expired or is invalid. Please try requesting a new reset link."
                ) }
                return@launch
            }

            AppLogger.d("AuthViewModel", "Updating password for userId=${supabaseRepo.currentUserId}")
            supabaseRepo.updatePassword(s.password)
                .onSuccess {
                    _state.update { it.copy(isLoading = false, navigateTo = "login", isResettingPassword = false) }
                }
                .onFailure { e ->
                    val errorMsg = e.message ?: "Failed to update password"
                    val finalMsg = if (errorMsg.contains("claim", ignoreCase = true)) {
                        "Session authentication failed. Please request a new reset link."
                    } else {
                        errorMsg
                    }
                    _state.update { it.copy(isLoading = false, error = finalMsg) }
                }
        }
    }

    fun prefillPlatformAdmin() {
        _state.update {
            it.copy(
                email = PlatformAdminAuthPolicy.EMAIL,
                password = "",
                error = null
            )
        }
    }

    fun quickLogin() {
    if (!_state.value.biometricEnabled) {
      _state.update {
        it.copy(error = "Biometric login is not enabled. Turn it on after choosing Remember Me.")
      }
      return
    }

		val current = _state.value
		val email = current.email.ifBlank { credentialsRepo.getSavedEmail() }
		val password = current.password.ifBlank { credentialsRepo.getSavedPassword() }

		if (email.isBlank() || password.isBlank()) {
			_state.update {
				it.copy(error = "No saved credentials found. Please log in with your password first and enable 'Remember Me'.")
			}
			return
		}

		// Ensure signIn() runs with the credentials we just resolved.
		_state.update { it.copy(email = email, password = password, error = null) }
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
