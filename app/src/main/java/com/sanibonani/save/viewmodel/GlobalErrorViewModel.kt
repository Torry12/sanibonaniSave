package com.sanibonani.save.viewmodel

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class GlobalErrorUiState(
    val message: String? = null,
    val isCritical: Boolean = false,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null
)

@HiltViewModel
class GlobalErrorViewModel @Inject constructor() : ViewModel() {
    private val _errorState = MutableStateFlow(GlobalErrorUiState())
    val errorState: StateFlow<GlobalErrorUiState> = _errorState.asStateFlow()

    fun showError(message: String, isCritical: Boolean = false, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
        _errorState.update {
            it.copy(
                message = message,
                isCritical = isCritical,
                actionLabel = actionLabel,
                onAction = onAction
            )
        }
    }

    fun dismissError() {
        _errorState.update { GlobalErrorUiState() }
    }
}
