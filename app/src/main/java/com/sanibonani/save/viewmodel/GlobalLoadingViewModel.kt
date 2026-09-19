package com.sanibonani.save.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GlobalLoadingService @Inject constructor() {
    private val _loadingCount = MutableStateFlow(0)
    private val _showSpinner = MutableStateFlow(false)
    val showSpinner: StateFlow<Boolean> = _showSpinner.asStateFlow()

    private var delayJob: Job? = null

    fun startLoading() {
        _loadingCount.update { it + 1 }
        updateSpinnerState()
    }

    fun stopLoading() {
        _loadingCount.update { (it - 1).coerceAtLeast(0) }
        updateSpinnerState()
    }

    private fun updateSpinnerState() {
        val count = _loadingCount.value
        if (count > 0) {
            // Only show spinner after a short delay (e.g. 500ms) to avoid flicker for fast loads
            if (delayJob == null || delayJob?.isCompleted == true) {
                delayJob = kotlinx.coroutines.GlobalScope.launch {
                    delay(800)
                    if (_loadingCount.value > 0) {
                        _showSpinner.value = true
                    }
                }
            }
        } else {
            delayJob?.cancel()
            _showSpinner.value = false
        }
    }
}

@HiltViewModel
class GlobalLoadingViewModel @Inject constructor(
    private val loadingService: GlobalLoadingService
) : ViewModel() {
    val showSpinner = loadingService.showSpinner
}
