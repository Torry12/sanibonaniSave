package com.sanibonani.save.viewmodel.dynamics

import androidx.lifecycle.viewModelScope
import com.sanibonani.save.domain.repository.SupabaseRepository
import com.sanibonani.save.viewmodel.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RecoveryUiState(
    val email: String = "",
    val isLoading: Boolean = false,
    val success: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class PasswordRecoveryViewModel @Inject constructor(
    private val supabaseRepo: SupabaseRepository
) : BaseViewModel<RecoveryUiState>(RecoveryUiState()) {

    val state: StateFlow<RecoveryUiState> = uiState

    fun recover(email: String) {
        viewModelScope.launch {
            updateState { it.copy(isLoading = true) }
            supabaseRepo.sendPasswordResetEmail(email).onSuccess {
                updateState { it.copy(isLoading = false, success = true) }
            }
        }
    }
}
