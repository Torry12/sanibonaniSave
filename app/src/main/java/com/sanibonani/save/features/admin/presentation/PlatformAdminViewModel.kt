package com.sanibonani.save.features.admin.presentation

import com.sanibonani.save.domain.model.UserRole
import com.sanibonani.save.domain.repository.auth.SupabaseRepository
import com.sanibonani.save.domain.utils.toUserMessage
import com.sanibonani.save.features.admin.presentation.state.PlatformAdminUiState
import com.sanibonani.save.features.common.presentation.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class PlatformAdminViewModel @Inject constructor(
    private val supabaseRepo: SupabaseRepository
) : BaseViewModel<PlatformAdminUiState>(PlatformAdminUiState()) {

    val state: StateFlow<PlatformAdminUiState> = uiState

    private val _mfaVerified = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val mfaVerified: Flow<Unit> = _mfaVerified.asSharedFlow()

    init {
        launchJob("init_authorization") {
            val role = supabaseRepo.getUserRole()
            val authorized = role == UserRole.PLATFORM_ADMIN
            updateState { it.copy(isAuthorized = authorized) }
        }
    }

    fun setTab(index: Int) {
        updateState { it.copy(selectedTab = index) }
    }

    fun clearError() {
        updateState { it.copy(error = null) }
    }

    fun onMfaRequired() {
        updateState { it.copy(showMfaChallenge = true, mfaChallengeError = null) }
    }

    fun verifyMfaChallenge(code: String) {
        launchJob("verify_mfa") {
            updateState { it.copy(isVerifyingMfa = true) }
            supabaseRepo.getMfaFactors().onSuccess { factors ->
                val factor = factors.firstOrNull() ?: return@onSuccess
                supabaseRepo.verifyMfa(factor.id, code, true).onSuccess {
                    updateState { it.copy(isVerifyingMfa = false, showMfaChallenge = false) }
                    _mfaVerified.tryEmit(Unit)
                    showMessage("MFA Verified")
                }.onFailure { e ->
                    updateState { it.copy(isVerifyingMfa = false, mfaChallengeError = e.toUserMessage()) }
                }
            }
        }
    }

    fun dismissMfaChallenge() {
        updateState { it.copy(showMfaChallenge = false) }
    }
}
