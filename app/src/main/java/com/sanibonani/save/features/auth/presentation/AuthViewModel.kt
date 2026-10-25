package com.sanibonani.save.features.auth.presentation

import androidx.lifecycle.viewModelScope
import com.sanibonani.save.domain.model.AsyncResult
import com.sanibonani.save.domain.logging.AppLogger
import com.sanibonani.save.domain.model.UserRole
import com.sanibonani.save.domain.repository.auth.CredentialsRepository
import com.sanibonani.save.domain.repository.auth.SupabaseRepository
import com.sanibonani.save.domain.repository.auth.UserProfileRepository
import com.sanibonani.save.domain.utils.UserRoleMapper
import com.sanibonani.save.domain.validation.InputValidator
import com.sanibonani.save.domain.validation.ValidationUtils
import com.sanibonani.save.service.AdminGroupContextCacheService
import com.sanibonani.save.service.MemberGroupContextCacheService
import com.sanibonani.save.service.UserProfileCacheService
import com.sanibonani.save.features.common.presentation.BaseViewModel
import com.sanibonani.save.features.auth.presentation.state.AuthState
import com.sanibonani.save.features.common.presentation.LoadStateQueue
import com.sanibonani.save.usecase.SignOutUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

/**
 * ViewModel responsible for authentication flows, session management, and hydration.
 * AI Optimization: This ViewModel acts as the gateway for user identity and role-based
 * data hydration. It uses a declarative session observation pattern to ensure that
 * the UI state is always in sync with the underlying Supabase session.
 */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val supabaseRepo: SupabaseRepository,
    private val userProfileRepo: UserProfileRepository,
    private val credentialsRepo: CredentialsRepository,
    private val adminGroupContextCacheService: AdminGroupContextCacheService,
    private val memberGroupContextCacheService: MemberGroupContextCacheService,
    private val userProfileCache: UserProfileCacheService,
    private val connManager: com.sanibonani.save.data.remote.SupabaseConnectionManager,
    private val realtimeSyncManager: com.sanibonani.save.data.sync.RealtimeSyncManager,
    private val loadQueue: LoadStateQueue,
    private val signOutUseCase: SignOutUseCase
) : BaseViewModel<AuthState>(AuthState()) {

    val state: StateFlow<AuthState> = uiState

    val hydrationSteps = loadQueue.steps

    private val sessionHydrator = AuthSessionHydrator(
        supabaseRepo = supabaseRepo,
        userProfileRepo = userProfileRepo,
        adminGroupContextCacheService = adminGroupContextCacheService,
        memberGroupContextCacheService = memberGroupContextCacheService,
        userProfileCache = userProfileCache,
        realtimeSyncManager = realtimeSyncManager,
        loadQueue = loadQueue
    )

    /**
     * Declarative session observation: the profile is cached, the role is resolved fail-closed,
     * and the domain caches are warmed before Success is emitted.
     */
    val accountStatus: StateFlow<AsyncResult<AuthenticatedAccount>> = sessionHydrator.accountStatus()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AsyncResult.Uninitialized)

    data class AuthenticatedAccount(val userId: String, val role: UserRole)

    init {
        loadSavedCredentials()
        syncLegacyStateWithAccountStatus()
    }

    private fun loadSavedCredentials() {
        val email = credentialsRepo.getSavedEmail()
        val pass = credentialsRepo.getSavedPassword()
        val remember = credentialsRepo.getRememberMe()
        val hasCreds = credentialsRepo.hasSavedCredentials()
        updateState { it.copy(email = email, password = if (remember) pass else "", rememberMe = remember, hasSavedCredentials = hasCreds, biometricEnabled = credentialsRepo.isBiometricEnabled()) }
    }

    private fun syncLegacyStateWithAccountStatus() {
        // AI Note: Bridging the declarative session flow with the legacy imperative AuthState.
        launchJob("sync_account_status") {
            accountStatus.collect { status ->
                when (status) {
                    is AsyncResult.Success -> {
                        updateState { it.copy(isLoggedIn = true, userId = status.data.userId, userRole = status.data.role, isLoading = false) }
                    }
                    is AsyncResult.Loading -> {
                        updateState { it.copy(isLoading = true) }
                    }
                    else -> {
                        updateState { it.copy(isLoggedIn = false, isLoading = false) }
                    }
                }
            }
        }
    }

    fun updateEmail(email: String) { updateState { it.copy(email = email) } }
    fun updateRememberMe(remember: Boolean) { updateState { it.copy(rememberMe = remember) } }
    fun updateFullName(name: String) { updateState { it.copy(fullName = name) } }
    fun updatePasswordState(password: String) { updateState { it.copy(password = password) } }
    fun updateConfirmPw(confirmPw: String) { updateState { it.copy(confirmPw = confirmPw) } }
    fun clearNavigation() { updateState { it.copy(navigateTo = null) } }
    fun clearNewRegistrationFlag() { updateState { it.copy(isNewRegistration = false) } }

    /**
     * Executes sign-in with the provided credentials.
     */
    fun signIn() {
        val s = uiState.value
        if (!ValidationUtils.isValidEmail(s.email.trim())) {
            updateState { it.copy(error = "Please enter a valid email address") }
            return
        }
        launchJob("auth_operation") {
            updateState { it.copy(isLoading = true, error = null) }
            val email = s.email.trim().lowercase()
            supabaseRepo.signIn(email, s.password).onSuccess {
                if (s.rememberMe) {
                    credentialsRepo.saveCredentials(email, s.password, true)
                } else {
                    credentialsRepo.clearCredentials()
                }
                // isLoggedIn and userRole are published by [accountStatus] once the session has
                // hydrated. Setting them here would route the login screen on a role that has
                // not been resolved yet, sending admins to the member dashboard first.
            }.onFailure { e ->
                updateState { it.copy(isLoading = false) }
                handleError(e)
            }
        }
    }

    /**
     * Executes sign-up for a new user.
     */
    fun signUp(role: UserRole = UserRole.MEMBER) {
        val s = uiState.value
        val email = s.email.trim().lowercase()
        val fullName = s.fullName.trim()
        val password = s.password

        if (fullName.length < 3) {
            updateState { it.copy(error = "Full Name must be at least 3 characters") }
            return
        }
        if (!ValidationUtils.isValidEmail(email)) {
            updateState { it.copy(error = "Please enter a valid email address") }
            return
        }
        InputValidator.validatePassword(password).getErrorMessage()?.let { message ->
            updateState { it.copy(error = message) }
            return
        }
        if (password != s.confirmPw) {
            updateState { it.copy(error = "Passwords do not match") }
            return
        }

        launchJob("auth_operation") {
            updateState { it.copy(isLoading = true, error = null) }
            supabaseRepo.signUp(email, password, mapOf("role" to UserRoleMapper.toStorageValue(role), "full_name" to fullName))
                .onSuccess {
                    userProfileCache.save(fullName, email, "")
                    updateState { it.copy(isLoading = false, isNewRegistration = true, userRole = role, isLoggedIn = true) }
                }
                .onFailure { e -> updateState { it.copy(isLoading = false) }; handleError(e) }
        }
    }

    /**
     * Logs the user out and clears all sensitive session caches.
     *
     * Uses [launchSafeJob] so a sign-out failure still clears the loading overlay
     * (a stuck overlay otherwise blocks the UI permanently) and surfaces an error.
     */
    fun signOut(onComplete: () -> Unit = {}) {
        launchSafeJob("sign_out", "Sign out failed") {
            updateState { it.copy(isLoading = true) }
            signOutUseCase()
                .onSuccess {
                    updateState { AuthState() }
                    onComplete()
                }
                .onFailure { e ->
                    updateState { it.copy(isLoading = false) }
                    handleError(e)
                }
        }
    }

    fun updateError(msg: String?) { updateState { it.copy(error = msg) } }

    fun toggleBiometric(enabled: Boolean) {
        credentialsRepo.setBiometricEnabled(enabled)
        updateState { it.copy(biometricEnabled = enabled) }
    }

    /**
     * Automatically logs in using saved credentials (if available).
     */
    fun quickLogin() {
        val email = credentialsRepo.getSavedEmail()
        val pass = credentialsRepo.getSavedPassword()
        if (email.isBlank() || pass.isBlank()) return
        updateState { it.copy(email = email, password = pass) }
        signIn()
    }

    /**
     * Forces a reconnection to Supabase and retries sign-in.
     */
    fun reconnect() {
        connManager.rebuildConnections()
        signIn()
    }

    fun resendVerification() {
        launchJob("resend_verification") {
            updateState { it.copy(isLoading = true, error = null) }
            supabaseRepo.resendVerificationEmail(uiState.value.email).onSuccess {
                updateState { it.copy(isLoading = false, error = null) }
                showMessage("Verification email sent! Please check your inbox.")
            }.onFailure { handleError(it) }
        }
    }

    fun signInWithMagicLink() {
        launchJob("magic_link") {
            updateState { it.copy(isLoading = true, error = null) }
            supabaseRepo.signInWithMagicLink(uiState.value.email).onSuccess {
                updateState { it.copy(isLoading = false, error = null) }
                showMessage("Login link sent! Please check your inbox.")
            }.onFailure { handleError(it) }
        }
    }

    fun handleDeepLink(url: String) {
        launchSafeJob("deep_link", "Could not process the link") {
            if (url.contains("reset-password")) {
                updateState { it.copy(isResettingPassword = true) }
                supabaseRepo.handleDeepLink(url)
                    .onFailure { AppLogger.w("AuthViewModel", "Reset-password deep link failed: ${it.message}") }
            } else if (url.contains("bank/linked")) {
                val uri = android.net.Uri.parse(url)
                val connectionId = uri.getQueryParameter("connection_id") ?: ""
                val status = uri.getQueryParameter("status") ?: ""
                com.sanibonani.save.domain.event.EventBus.emit(com.sanibonani.save.domain.event.BankCallbackEvent(connectionId, status))
            } else {
                supabaseRepo.handleDeepLink(url)
                    .onFailure { AppLogger.w("AuthViewModel", "Deep link failed: ${it.message}") }
            }
        }
    }

    fun updatePassword() {
        val s = uiState.value
        launchJob("update_password") {
            updateState { it.copy(isLoading = true) }
            supabaseRepo.updatePassword(s.password).onSuccess {
                // Re-arm the login route guard, otherwise RouteGuard keeps bypassing
                // the auth-entry check for the rest of this ViewModel's lifetime.
                updateState { it.copy(isLoading = false, isResettingPassword = false, navigateTo = "login") }
            }.onFailure { handleError(it) }
        }
    }
}
