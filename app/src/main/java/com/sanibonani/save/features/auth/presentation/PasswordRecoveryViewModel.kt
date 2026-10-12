package com.sanibonani.save.features.auth.presentation

import com.sanibonani.save.domain.repository.auth.SupabaseRepository
import com.sanibonani.save.features.common.presentation.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

data class PasswordRecoveryState(
    val input: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val success: Boolean = false
)

@HiltViewModel
class PasswordRecoveryViewModel @Inject constructor(
    private val supabaseRepo: SupabaseRepository
) : BaseViewModel<PasswordRecoveryState>(PasswordRecoveryState()) {

    val state: StateFlow<PasswordRecoveryState> = uiState

    fun updateInput(input: String) {
        updateState { it.copy(input = input, error = null, success = false) }
    }

    fun sendRecovery() {
        val input = uiState.value.input.trim().lowercase()
        if (input.isBlank() || !input.contains("@")) {
            updateState { it.copy(error = "Please enter a valid email address.") }
            return
        }
        launchJob("send_recovery") {
            updateState { it.copy(isLoading = true, error = null, success = false) }
            supabaseRepo.sendPasswordResetEmail(input).onSuccess {
                updateState { it.copy(isLoading = false, success = true, input = "") }
            }.onFailure { e ->
                updateState { it.copy(isLoading = false) }
                handleError(e)
            }
        }
    }
}
