package com.sanibonani.save.features.admin.presentation

import com.sanibonani.save.domain.usecase.auth.CreatePlatformAdminUseCase
import com.sanibonani.save.domain.utils.toUserMessage
import com.sanibonani.save.features.common.presentation.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

data class CreatePlatformAdminState(
    val isLoading: Boolean = false,
    val success: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class CreatePlatformAdminViewModel @Inject constructor(
    private val createPlatformAdminUseCase: CreatePlatformAdminUseCase
) : BaseViewModel<CreatePlatformAdminState>(CreatePlatformAdminState()) {

    val state: StateFlow<CreatePlatformAdminState> = uiState

    fun createPlatformAdmin(email: String, pass: String, confirm: String) {
        if (pass != confirm) {
            updateState { it.copy(error = "Passwords do not match") }
            return
        }

        launchJob("create_admin") {
            updateState { it.copy(isLoading = true, error = null) }
            createPlatformAdminUseCase(email = email, password = pass)
                .onSuccess {
                    updateState { it.copy(isLoading = false, success = true) }
                }
                .onFailure { e ->
                    updateState { it.copy(isLoading = false, error = e.toUserMessage()) }
                }
        }
    }
}
