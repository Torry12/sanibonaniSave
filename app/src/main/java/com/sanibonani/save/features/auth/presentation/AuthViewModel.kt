package com.sanibonani.save.features.auth.presentation

import androidx.lifecycle.viewModelScope
import com.sanibonani.save.analytics.AnalyticsTaxonomy
import com.sanibonani.save.analytics.AppAnalytics
import com.sanibonani.save.domain.logging.AppLogger
import com.sanibonani.save.domain.utils.toUserMessage
import com.sanibonani.save.domain.model.AsyncResult
import com.sanibonani.save.domain.model.UserRole
import com.sanibonani.save.domain.repository.system.CredentialsRepository
import com.sanibonani.save.domain.repository.system.SupabaseRepository
import com.sanibonani.save.domain.utils.AuthIdentityUtils
import com.sanibonani.save.domain.utils.UserRoleMapper
import com.sanibonani.save.domain.validation.ValidationResult
import com.sanibonani.save.domain.validation.ValidationUtils
import com.sanibonani.save.service.AdminGroupContextCacheService
import com.sanibonani.save.service.MemberGroupContextCacheService
import com.sanibonani.save.service.UserProfileCacheService
import com.sanibonani.save.viewmodel.BaseViewModel
import com.sanibonani.save.features.auth.presentation.state.AuthState
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.auth.status.SessionStatus
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val supabaseRepo: SupabaseRepository,
    private val credentialsRepo: CredentialsRepository,
    private val adminGroupContextCacheService: AdminGroupContextCacheService,
    private val memberGroupContextCacheService: MemberGroupContextCacheService,
    private val userProfileCache: UserProfileCacheService,
    private val connManager: com.sanibonani.save.data.remote.SupabaseConnectionManager,
    private val realtimeSyncManager: com.sanibonani.save.data.sync.RealtimeSyncManager
) : BaseViewModel<AuthState>(AuthState()) {

    val state: StateFlow<AuthState> = uiState

    /**
     * Declarative session observation.
     * Combines the raw session flow with role resolution.
     */
    val accountStatus: StateFlow<AsyncResult<AuthenticatedAccount>> = supabaseRepo.sessionFlow
        .flatMapLatest { session ->
            if (session == null) {
                flowOf(AsyncResult.Uninitialized)
            } else {
                flow<AsyncResult<AuthenticatedAccount>> {
                    emit(AsyncResult.Loading)
                    val userId = session.user?.id ?: ""
                    coroutineScope {
                        launch { memberGroupContextCacheService.warmUpForUser(userId) }
                        launch { adminGroupContextCacheService.warmUpForUser(userId) }
                        launch {
                            supabaseRepo.getProfile(userId)?.let { userProfileCache.save(it) }
                        }
                    }
                    val role = runCatching { supabaseRepo.getUserRole() }.getOrDefault(UserRole.MEMBER)
                    emit(AsyncResult.Success(AuthenticatedAccount(userId, role)))
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AsyncResult.Uninitialized)

    data class AuthenticatedAccount(val userId: String, val role: UserRole)

    init {
        loadSavedCredentials()
        observeActiveUrl()
        syncLegacyStateWithAccountStatus()
    }

    private fun observeActiveUrl() {
        launchJob("active_url_observation") {
            connManager.currentClient.collect { client ->
                updateState { it.copy(activeSupabaseUrl = client?.supabaseUrl ?: "") }
            }
        }
    }

    private fun loadSavedCredentials() {
        val email = credentialsRepo.getSavedEmail()
        val pass = credentialsRepo.getSavedPassword()
        val remember = credentialsRepo.getRememberMe()
        val hasCreds = credentialsRepo.hasSavedCredentials()
        updateState { it.copy(email = email, password = if (remember) pass else "", rememberMe = remember, hasSavedCredentials = hasCreds, biometricEnabled = credentialsRepo.isBiometricEnabled()) }
    }

    private fun syncLegacyStateWithAccountStatus() {
        viewModelScope.launch {
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

    fun signIn() {
        val s = uiState.value
        launchJob("auth_operation") {
            updateState { it.copy(isLoading = true) }
            supabaseRepo.signIn(s.email, s.password).onSuccess {
                if (s.rememberMe) credentialsRepo.saveCredentials(s.email, s.password, true)
                updateState { it.copy(isLoading = false, isLoggedIn = true) }
            }.onFailure { e ->
                updateState { it.copy(isLoading = false) }
                handleError(e)
            }
        }
    }

    fun signUp(role: UserRole = UserRole.MEMBER) {
        val s = uiState.value
        launchJob("auth_operation") {
            updateState { it.copy(isLoading = true) }
            supabaseRepo.signUp(s.email, s.password, mapOf("role" to UserRoleMapper.toStorageValue(role), "full_name" to s.fullName))
                .onSuccess { updateState { it.copy(isLoading = false, isNewRegistration = true, userRole = role) } }
                .onFailure { e -> updateState { it.copy(isLoading = false) }; handleError(e) }
        }
    }

    fun signOut(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            realtimeSyncManager.stop()
            adminGroupContextCacheService.clearForSignOut()
            memberGroupContextCacheService.clearForSignOut()
            supabaseRepo.signOut()
            updateState { AuthState() }
            onComplete()
        }
    }

    fun updateError(msg: String?) { updateState { it.copy(error = msg) } }

    fun toggleBiometric(enabled: Boolean) {
        credentialsRepo.setBiometricEnabled(enabled)
        updateState { it.copy(biometricEnabled = enabled) }
    }

    fun quickLogin() {
        val email = credentialsRepo.getSavedEmail()
        val pass = credentialsRepo.getSavedPassword()
        if (email.isBlank() || pass.isBlank()) return
        updateState { it.copy(email = email, password = pass) }
        signIn()
    }

    fun reconnect() { connManager.rebuildConnections(); signIn() }

    fun resendVerification() {
        viewModelScope.launch {
            updateState { it.copy(isLoading = true) }
            supabaseRepo.resendVerificationEmail(uiState.value.email).onSuccess {
                updateState { it.copy(isLoading = false, error = "Email sent") }
            }
        }
    }

    fun signInWithMagicLink() {
        viewModelScope.launch {
            updateState { it.copy(isLoading = true) }
            supabaseRepo.signInWithMagicLink(uiState.value.email).onSuccess {
                updateState { it.copy(isLoading = false, error = "Link sent") }
            }
        }
    }

    fun handleDeepLink(url: String) {
        viewModelScope.launch {
            if (url.contains("reset-password")) updateState { it.copy(isResettingPassword = true) }
            supabaseRepo.handleDeepLink(url)
        }
    }

    fun updatePassword() {
        val s = uiState.value
        viewModelScope.launch {
            updateState { it.copy(isLoading = true) }
            supabaseRepo.updatePassword(s.password).onSuccess {
                updateState { it.copy(isLoading = false, navigateTo = "login") }
            }
        }
    }
}
