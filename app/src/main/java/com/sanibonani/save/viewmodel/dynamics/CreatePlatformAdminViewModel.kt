package com.sanibonani.save.viewmodel.dynamics

import androidx.lifecycle.viewModelScope
import com.sanibonani.save.domain.repository.SupabaseRepository
import com.sanibonani.save.viewmodel.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CreateAdminUiState(
    val isLoading: Boolean = false,
    val success: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class CreatePlatformAdminViewModel @Inject constructor(
    private val supabaseRepo: SupabaseRepository
) : BaseViewModel<CreateAdminUiState>(CreateAdminUiState()) {

    val state: StateFlow<CreateAdminUiState> = uiState

    fun create(email: String, fullName: String) {
        viewModelScope.launch {
            updateState { it.copy(isLoading = true) }
            // Simulation
            updateState { it.copy(isLoading = false, success = true) }
        }
    }
}
