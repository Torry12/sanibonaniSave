package com.sanibonani.save.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanibonani.save.data.logging.AppLogger
import com.sanibonani.save.data.utils.toUserMessage
import com.sanibonani.save.domain.model.UserRole
import com.sanibonani.save.domain.repository.CredentialsRepository
import com.sanibonani.save.domain.repository.SupabaseRepository
import com.sanibonani.save.domain.utils.PlatformAdminAuthPolicy
import com.sanibonani.save.domain.utils.UserRoleMapper
import com.sanibonani.save.service.AdminGroupContextCacheService
import com.sanibonani.save.service.MemberGroupContextCacheService
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

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
    val isNewRegistration: Boolean = false,
    val biometricEnabled: Boolean = false,
    val hasSavedCredentials: Boolean = false
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val supabaseRepo: SupabaseRepository,
    private val credentialsRepo: CredentialsRepository,
    private val adminGroupContextCacheService: AdminGroupContextCacheService,
    private val memberGroupContextCacheService: MemberGroupContextCacheService
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
                hasSavedCredentials = false
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
                        val role = runCatching { supabaseRepo.getUserRole() }.getOrDefault(UserRole.MEMBER)
                        AppLogger.d(
                            tag = "AuthViewModel",
                            message = "Session authenticated userId=${supabaseRepo.currentUserId} resolvedRole=$role"
                        )
                        _state.update { it.copy(
                            isLoggedIn = true,
                            userRole = role,
                            isLoading = false,
                            error = null
                        ) }
                    }
                    is SessionStatus.NotAuthenticated -> {
                        AppLogger.d(tag = "AuthViewModel", message = "Session not authenticated. Clearing caches and auth state.")
                        adminGroupContextCacheService.clearForSignOut()
                        memberGroupContextCacheService.clearForSignOut()
                        _state.update { it.copy(isLoggedIn = false, userRole = UserRole.MEMBER, isLoading = false) }
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

    fun signIn() {
        val s = _state.value
        val normalizedEmail = s.email.trim()
        
        // Trim password for manual entries, but not for the pre-filled platform admin password
        // which we know is correct.
        val rawPassword = s.password.trim()
        val normalizedPassword = PlatformAdminAuthPolicy.normalizeSignInPassword(normalizedEmail, rawPassword)
        
        if (normalizedEmail.isBlank()) {
            _state.update { it.copy(error = "Please enter your email") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }

            val result = if (rawPassword.isBlank()) {
                supabaseRepo.signInWithMagicLink(normalizedEmail)
            } else {
                supabaseRepo.signIn(normalizedEmail, normalizedPassword)
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
                    _state.update { it.copy(isLoading = false, error = "Magic link sent to your email!") }
                } else {
                    _state.update { it.copy(isLoading = false, email = normalizedEmail) }
                }
            }
            .onFailure { e ->
				_state.update { it.copy(isLoading = false, error = e.toUserMessage()) }
            }
        }
    }


    fun signUp(role: UserRole = UserRole.MEMBER) {
        val s = _state.value
        val normalizedEmail = s.email.trim()
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
                    _state.update { it.copy(isLoading = false, isNewRegistration = true, email = normalizedEmail) }
                }
                .onFailure { e ->
					_state.update { it.copy(isLoading = false, error = e.toUserMessage()) }
                }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            adminGroupContextCacheService.clearForSignOut()
            memberGroupContextCacheService.clearForSignOut()
            supabaseRepo.signOut()
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
            
            if (!supabaseRepo.isLoggedIn) {
                kotlinx.coroutines.delay(1000)
                if (!supabaseRepo.isLoggedIn) {
                    _state.update { it.copy(
                        isLoading = false, 
                        error = "No active session found. Your reset link may have expired or is invalid. Please try requesting a new reset link."
                    ) }
                    return@launch
                }
            }

            supabaseRepo.updatePassword(s.password)
                .onSuccess {
                    _state.update { it.copy(isLoading = false, navigateTo = "login") }
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
                password = PlatformAdminAuthPolicy.PASSWORD,
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
}
