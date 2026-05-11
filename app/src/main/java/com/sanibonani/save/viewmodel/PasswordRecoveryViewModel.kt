package com.sanibonani.save.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanibonani.save.data.utils.toUserMessage
import com.sanibonani.save.domain.repository.NotificationRepository
import com.sanibonani.save.domain.repository.SupabaseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
        _state.update { it.copy(input = input, error = null, success = false) }
    }

    fun sendRecovery(method: RecoveryMethod) {
        val rawInput = _state.value.input.trim()
        val input = when (method) {
            RecoveryMethod.EMAIL -> rawInput.lowercase()
            RecoveryMethod.WHATSAPP -> normalizeWhatsAppNumber(rawInput)
        }

        if (input.isBlank()) {
            _state.update { it.copy(error = "Please enter your ${if (method == RecoveryMethod.EMAIL) "email address" else "WhatsApp number"}.") }
            return
        }

        if (method == RecoveryMethod.EMAIL && !input.contains("@")) {
            _state.update { it.copy(error = "Please enter a valid email address.") }
            return
        }

        if (method == RecoveryMethod.WHATSAPP && input.length !in 11..13) {
            _state.update { it.copy(error = "Please enter a valid South African phone number (0XX-XXX-XXXX).") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, success = false) }
            val result = when (method) {
                RecoveryMethod.EMAIL -> supabaseRepo.sendPasswordResetEmail(input)
                RecoveryMethod.WHATSAPP -> notificationRepo.sendPasswordResetWhatsApp(input)
            }
            result.onSuccess {
                _state.update { it.copy(isLoading = false, success = true, input = "") }
            }.onFailure { e ->
                val userMessage = e.toUserMessage()
                val friendlyMsg = when {
                    userMessage.contains("not found", ignoreCase = true) || 
                    userMessage.contains("doesn't exist", ignoreCase = true) ||
                    userMessage.contains("404", ignoreCase = true) -> 
                        "No account found with this ${if (method == RecoveryMethod.EMAIL) "email" else "WhatsApp number"}. Please check and try again."
                    userMessage.contains("network", ignoreCase = true) || 
                    userMessage.contains("timeout", ignoreCase = true) ||
                    userMessage.contains("failed to reach", ignoreCase = true) ->
                        "Network connection issue. Please check your internet and try again."
                    else -> userMessage
                }
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = friendlyMsg
                    )
                }
            }
        }
    }

    private fun normalizeWhatsAppNumber(input: String): String {
        val digits = input.filter { it.isDigit() }
        return when {
            digits.startsWith("27") -> digits
            digits.startsWith("0") && digits.length == 10 -> "27${digits.drop(1)}"
            else -> digits
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

