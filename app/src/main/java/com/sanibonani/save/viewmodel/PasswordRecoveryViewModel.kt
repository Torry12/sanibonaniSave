package com.sanibonani.save.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanibonani.save.domain.repository.NotificationRepository
import com.sanibonani.save.domain.repository.SupabaseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PasswordRecoveryViewModel @Inject constructor(
    private val supabaseRepo: SupabaseRepository,
    private val notificationRepo: NotificationRepository
) : ViewModel() {
    private val _state = MutableStateFlow(PasswordRecoveryState())
    val state: StateFlow<PasswordRecoveryState> = _state.asStateFlow()

    fun updateInput(input: String) {
        _state.value = _state.value.copy(input = input, error = null, success = false)
    }

    fun sendRecovery(method: RecoveryMethod) {
        val input = _state.value.input.trim()
        if (input.isBlank()) {
            _state.value = _state.value.copy(error = "Please enter your email or WhatsApp number.")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null, success = false)
            val result = when (method) {
                RecoveryMethod.EMAIL -> supabaseRepo.sendPasswordResetEmail(input)
                RecoveryMethod.WHATSAPP -> notificationRepo.sendPasswordResetWhatsApp(input)
            }
            result.onSuccess {
                _state.value = _state.value.copy(isLoading = false, success = true)
            }.onFailure { e ->
                _state.value = _state.value.copy(isLoading = false, error = e.message ?: "Failed to send recovery instructions.")
            }
        }
    }
}

data class PasswordRecoveryState(
    val input: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val success: Boolean = false
)

enum class RecoveryMethod { EMAIL, WHATSAPP }

