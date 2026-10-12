package com.sanibonani.save.features.common.presentation

import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

data class GlobalErrorUiState(
    val message: String? = null,
    val isCritical: Boolean = false,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null
)

@HiltViewModel
class GlobalErrorViewModel @Inject constructor() : BaseViewModel<GlobalErrorUiState>(GlobalErrorUiState()) {

    val errorState: StateFlow<GlobalErrorUiState> = uiState

    fun showError(message: String, isCritical: Boolean = false, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
        updateState {
            it.copy(
                message = message,
                isCritical = isCritical,
                actionLabel = actionLabel,
                onAction = onAction
            )
        }
    }

    fun dismissError() {
        updateState { GlobalErrorUiState() }
    }
}
