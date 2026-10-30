package com.sanibonani.save.features.auth.presentation

import androidx.lifecycle.viewModelScope
import com.sanibonani.save.domain.model.AsyncResult
import com.sanibonani.save.domain.logging.AppLogger
import com.sanibonani.save.domain.model.UserRole
import com.sanibonani.save.domain.service.RealtimeSyncService
import com.sanibonani.save.domain.usecase.auth.GetAttributableActorIdUseCase
import com.sanibonani.save.domain.usecase.auth.GetCurrentUserRoleUseCase
import com.sanibonani.save.domain.usecase.auth.GetSavedLoginCredentialsUseCase
import com.sanibonani.save.domain.usecase.auth.GetUserProfileUseCase
import com.sanibonani.save.domain.usecase.auth.HandleAuthDeepLinkUseCase
import com.sanibonani.save.domain.usecase.auth.ObserveAuthSessionUseCase
import com.sanibonani.save.domain.usecase.auth.RebuildConnectionsUseCase
import com.sanibonani.save.domain.usecase.auth.RegisterAccountUseCase
import com.sanibonani.save.domain.usecase.auth.ResendVerificationEmailUseCase
import com.sanibonani.save.domain.usecase.auth.RequestMagicLinkUseCase
import com.sanibonani.save.domain.usecase.auth.SetBiometricEnabledUseCase
import com.sanibonani.save.domain.usecase.auth.SignInUseCase
import com.sanibonani.save.domain.usecase.auth.UpdatePasswordUseCase
import com.sanibonani.save.domain.validation.InputValidator
import com.sanibonani.save.domain.validation.ValidationUtils
import com.sanibonani.save.service.AdminGroupContextCacheService
import com.sanibonani.save.service.MemberGroupContextCacheService
import com.sanibonani.save.service.UserProfileCacheService
import com.sanibonani.save.features.common.presentation.BaseViewModel
import com.sanibonani.save.features.auth.presentation.state.AuthState
import com.sanibonani.save.features.common.presentation.LoadStateQueue
import com.sanibonani.save.features.auth.usecase.SignOutUseCase
import com.sanibonani.save.worker.BackgroundWorkScheduler
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
    observeAuthSession: ObserveAuthSessionUseCase,
    getUserProfile: GetUserProfileUseCase,
    private val getCurrentUserRole: GetCurrentUserRoleUseCase,
    private val getAttributableActorId: GetAttributableActorIdUseCase,
    private val getSavedLoginCredentials: GetSavedLoginCredentialsUseCase,
    private val signInUseCase: SignInUseCase,
    private val registerAccount: RegisterAccountUseCase,
    private val resendVerificationEmail: ResendVerificationEmailUseCase,
    private val requestMagicLink: RequestMagicLinkUseCase,
    private val handleAuthDeepLink: HandleAuthDeepLinkUseCase,
    private val updatePasswordUseCase: UpdatePasswordUseCase,
    private val setBiometricEnabled: SetBiometricEnabledUseCase,
    private val rebuildConnections: RebuildConnectionsUseCase,
    adminGroupContextCacheService: AdminGroupContextCacheService,
    memberGroupContextCacheService: MemberGroupContextCacheService,
    private val userProfileCache: UserProfileCacheService,
    realtimeSyncService: RealtimeSyncService,
    private val loadQueue: LoadStateQueue,
    private val signOutUseCase: SignOutUseCase,
    private val backgroundWorkScheduler: BackgroundWorkScheduler
) : BaseViewModel<AuthState>(AuthState()) {

    val state: StateFlow<AuthState> = uiState

    val hydrationSteps = loadQueue.steps

    private val sessionHydrator = AuthSessionHydrator(
        observeAuthSession = observeAuthSession,
        getUserProfile = getUserProfile,
        getCurrentUserRole = getCurrentUserRole,
        adminGroupContextCacheService = adminGroupContextCacheService,
        memberGroupContextCacheService = memberGroupContextCacheService,
        userProfileCache = userProfileCache,
        realtimeSyncService = realtimeSyncService,
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
        val saved = getSavedLoginCredentials()
        updateState { it.copy(
            email = saved.email,
            password = if (saved.rememberMe) saved.password else "",
            rememberMe = saved.rememberMe,
            hasSavedCredentials = saved.hasSavedCredentials,
            biometricEnabled = saved.biometricEnabled
        ) }
    }

    private fun syncLegacyStateWithAccountStatus() {
        // AI Note: Bridging the declarative session flow with the legacy imperative AuthState.
        launchJob("sync_account_status") {
            accountStatus.collect { status ->
                when (status) {
                    is AsyncResult.Success -> {
                        updateState { it.copy(isLoggedIn = true, userId = status.data.userId, userRole = status.data.role, isLoading = false) }
                        // The session is proven now, so this is the point at which the background
                        // automations the role is entitled to are enqueued. Off the main thread:
                        // WorkManager is initialised on demand and its first call does disk I/O.
                        viewModelScope.launch(Dispatchers.IO) {
                            backgroundWorkScheduler.refresh()
                        }
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
            signInUseCase(email, s.password, s.rememberMe).onSuccess {
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
            registerAccount(email, password, fullName, role)
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

    /**
     * Re-resolves the signed-in account's role. Creating a group elevates the creator on the
     * server without emitting a new session, so the role captured during hydration stays stale
     * until this runs.
     */
    fun refreshUserRole() {
        val userId = getAttributableActorId() ?: uiState.value.userId ?: return
        launchJob("refresh_role") {
            val role = getCurrentUserRole()
            updateState { it.copy(userId = userId, userRole = role) }
        }
    }

    fun toggleBiometric(enabled: Boolean) {
        setBiometricEnabled(enabled)
        updateState { it.copy(biometricEnabled = enabled) }
    }

    /**
     * Automatically logs in using saved credentials (if available).
     */
    fun quickLogin() {
        val saved = getSavedLoginCredentials()
        if (saved.email.isBlank() || saved.password.isBlank()) return
        updateState { it.copy(email = saved.email, password = saved.password) }
        signIn()
    }

    /**
     * Forces a reconnection to Supabase and retries sign-in.
     */
    fun reconnect() {
        launchJob("rebuild_connections") {
            rebuildConnections()
            signIn()
        }
    }

    fun resendVerification() {
        launchJob("resend_verification") {
            updateState { it.copy(isLoading = true, error = null) }
            resendVerificationEmail(uiState.value.email).onSuccess {
                updateState { it.copy(isLoading = false, error = null) }
                showMessage("Verification email sent! Please check your inbox.")
            }.onFailure { handleError(it) }
        }
    }

    fun signInWithMagicLink() {
        launchJob("magic_link") {
            updateState { it.copy(isLoading = true, error = null) }
            requestMagicLink(uiState.value.email).onSuccess {
                updateState { it.copy(isLoading = false, error = null) }
                showMessage("Login link sent! Please check your inbox.")
            }.onFailure { handleError(it) }
        }
    }

    fun handleDeepLink(url: String) {
        launchSafeJob("deep_link", "Could not process the link") {
            if (url.contains("reset-password")) {
                updateState { it.copy(isResettingPassword = true) }
                handleAuthDeepLink(url)
                    .onFailure { AppLogger.w("AuthViewModel", "Reset-password deep link failed: ${it.message}") }
            } else if (url.contains("bank/linked")) {
                val uri = android.net.Uri.parse(url)
                val connectionId = uri.getQueryParameter("connection_id") ?: ""
                val status = uri.getQueryParameter("status") ?: ""
                com.sanibonani.save.domain.event.EventBus.emit(com.sanibonani.save.domain.event.BankCallbackEvent(connectionId, status))
            } else {
                handleAuthDeepLink(url)
                    .onFailure { AppLogger.w("AuthViewModel", "Deep link failed: ${it.message}") }
            }
        }
    }

    fun updatePassword() {
        val s = uiState.value
        launchJob("update_password") {
            updateState { it.copy(isLoading = true) }
            updatePasswordUseCase(s.password).onSuccess {
                // Re-arm the login route guard, otherwise RouteGuard keeps bypassing
                // the auth-entry check for the rest of this ViewModel's lifetime.
                updateState { it.copy(isLoading = false, isResettingPassword = false, navigateTo = "login") }
            }.onFailure { handleError(it) }
        }
    }
}
