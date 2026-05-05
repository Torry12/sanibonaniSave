package com.sanibonani.save.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanibonani.save.data.utils.toUserMessage
import com.sanibonani.save.domain.model.UserRole
import com.sanibonani.save.domain.repository.SupabaseRepository
import com.sanibonani.save.domain.utils.UserRoleMapper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI state for creating a new Platform Admin user.
 * Tracks loading, success, and error state for the registration flow.
 */
data class CreatePlatformAdminState(
    val isLoading: Boolean = false,
    val success: Boolean = false,
    val error: String? = null
)

/**
 * ViewModel for creating a new Platform Admin user.
 * Handles validation, registration, and error handling for the admin creation flow.
 * Uses StateFlow for state and Hilt for DI.
 */
@HiltViewModel
class CreatePlatformAdminViewModel @Inject constructor(
    private val supabaseRepo: SupabaseRepository
) : ViewModel() {

    private val _state = MutableStateFlow(CreatePlatformAdminState())
    val state: StateFlow<CreatePlatformAdminState> = _state.asStateFlow()

    fun createPlatformAdmin(email: String, password: String, confirmPw: String) {
        if (email.isBlank() || password.isBlank() || confirmPw.isBlank()) {
            _state.update { it.copy(error = "All fields are required") }
            return
        }

        if (password != confirmPw) {
            _state.update { it.copy(error = "Passwords do not match") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, success = false) }
            
            // Note: Per security requirements, portal-created admins default to GROUP_ADMIN 
            // and cannot create subsequent PLATFORM_ADMIN users.
            supabaseRepo.adminSignUp(
                email = email.trim(),
                password = password,
                metadata = mapOf(
                    "role" to UserRoleMapper.toStorageValue(UserRole.GROUP_ADMIN),
                    "full_name" to "Administrative User"
                ),
                confirm = true
            ).onSuccess {
                _state.update { it.copy(isLoading = false, success = true) }
            }.onFailure { e ->
                _state.update { it.copy(isLoading = false, error = e.toUserMessage()) }
            }
        }
    }
}
