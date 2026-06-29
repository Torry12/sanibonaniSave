package com.sanibonani.save.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanibonani.save.data.errors.ErrorMessageMapper
import com.sanibonani.save.viewmodel.state.UIEvent
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * Base class for ViewModels that manage a single UI state and one-time events.
 */
abstract class BaseViewModel<S>(initialState: S) : ViewModel() {

    protected val _uiState = MutableStateFlow(initialState)
    val uiState: StateFlow<S> = _uiState.asStateFlow()

    protected val _events = Channel<UIEvent>(Channel.BUFFERED)
    val events: Flow<UIEvent> = _events.receiveAsFlow()

    /**
     * Updates the UI state atomically.
     */
    protected fun updateState(reducer: (S) -> S) {
        _uiState.update(reducer)
    }

    /**
     * Sends a one-time event to the UI.
     */
    protected fun sendEvent(event: UIEvent) {
        viewModelScope.launch {
            _events.send(event)
        }
    }

    /**
     * Reports an error via GlobalErrorViewModel (if needed) or directly to the UI.
     */
    protected fun handleError(throwable: Throwable, userMessage: String? = null) {
        val message = userMessage ?: ErrorMessageMapper.mapThrowableToUserMessage(throwable)
        sendEvent(UIEvent.ShowError(message))
    }
    
    protected fun showMessage(message: String) {
        sendEvent(UIEvent.ShowMessage(message))
    }
}
