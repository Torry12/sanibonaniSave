package com.sanibonani.save.features.auth.presentation

import com.sanibonani.save.domain.usecase.auth.RequestPasswordResetUseCase
import com.sanibonani.save.domain.validation.ValidationUtils
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
    private val requestPasswordReset: RequestPasswordResetUseCase
) : BaseViewModel<PasswordRecoveryState>(PasswordRecoveryState()) {

    val state: StateFlow<PasswordRecoveryState> = uiState

    fun updateInput(input: String) {
        updateState { it.copy(input = input, error = null, success = false) }
    }

    fun sendRecovery() {
        val input = uiState.value.input.trim().lowercase()
        if (!ValidationUtils.isValidEmail(input)) {
            updateState { it.copy(error = "Please enter a valid email address.") }
            return
        }
        launchJob("send_recovery") {
            updateState { it.copy(isLoading = true, error = null, success = false) }
            requestPasswordReset(input).onSuccess {
                updateState { it.copy(isLoading = false, success = true, input = "") }
            }.onFailure { e ->
                updateState { it.copy(isLoading = false) }
                handleError(e)
            }
        }
    }
}
