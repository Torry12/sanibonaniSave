package com.sanibonani.save.features.auth.presentation

import androidx.lifecycle.viewModelScope
import com.sanibonani.save.domain.utils.toUserMessage
import com.sanibonani.save.domain.repository.system.SupabaseRepository
import com.sanibonani.save.viewmodel.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PasswordRecoveryState(
    val input: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val success: Boolean = false
)

enum class RecoveryMethod { EMAIL }

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
        viewModelScope.launch {
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
