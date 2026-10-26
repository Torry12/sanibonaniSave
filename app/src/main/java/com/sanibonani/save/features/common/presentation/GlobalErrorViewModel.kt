package com.sanibonani.save.features.common.presentation

import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

data class GlobalErrorUiState(
    val message: String? = null,
    val isCritical: Boolean = false
)

@HiltViewModel
class GlobalErrorViewModel @Inject constructor() : BaseViewModel<GlobalErrorUiState>(GlobalErrorUiState()) {

    val errorState: StateFlow<GlobalErrorUiState> = uiState

    fun showError(message: String, isCritical: Boolean = false) {
        updateState { it.copy(message = message, isCritical = isCritical) }
    }

    fun dismissError() {
        updateState { GlobalErrorUiState() }
    }
}
