package com.sanibonani.save.features.common.presentation

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GlobalLoadingService @Inject constructor() {
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main)
    private val _loadingCount = MutableStateFlow(0)
    private val _showSpinner = MutableStateFlow(false)
    val showSpinner: StateFlow<Boolean> = _showSpinner.asStateFlow()

    private var delayJob: Job? = null
    private var hideJob: Job? = null

    init {
        LoadingManager.setService(this)
    }

    fun startLoading() {
        hideJob?.cancel() // Stop any pending hide
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
            if (delayJob == null || delayJob?.isCompleted == true) {
                delayJob = scope.launch {
                    delay(1500) // 1.5s delay to prevent "flashing" on fast operations
                    if (_loadingCount.value > 0) {
                        _showSpinner.value = true
                    }
                }
            }
        } else {
            delayJob?.cancel()
            hideJob?.cancel()
            hideJob = scope.launch {
                delay(300)
                if (_loadingCount.value == 0) {
                    _showSpinner.value = false
                }
            }
        }
    }
}

object LoadingManager {
    private var service: GlobalLoadingService? = null

    fun setService(s: GlobalLoadingService) {
        service = s
    }

    fun start() = service?.startLoading()
    fun stop() = service?.stopLoading()
}

@HiltViewModel
class GlobalLoadingViewModel @Inject constructor(
    loadingService: GlobalLoadingService
) : ViewModel() {
    val showSpinner = loadingService.showSpinner
}
