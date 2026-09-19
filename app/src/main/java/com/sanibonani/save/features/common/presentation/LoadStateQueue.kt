package com.sanibonani.save.features.common.presentation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * A utility to manage complex multi-step initializations deterministically.
 * Ensures that Step B never starts if Step A is still pending or has failed.
 */
class LoadStateQueue @Inject constructor() {

    private val _steps = MutableStateFlow<Map<String, StepStatus>>(emptyMap())
    val steps = _steps.asStateFlow()

    fun reset() {
        _steps.value = emptyMap()
    }

    fun startStep(key: String) {
        _steps.update { it + (key to StepStatus.Loading) }
    }

    fun completeStep(key: String) {
        _steps.update { it + (key to StepStatus.Success) }
    }

    fun failStep(key: String, error: String) {
        _steps.update { it + (key to StepStatus.Error(error)) }
    }

    fun isStepComplete(key: String): Boolean {
        return _steps.value[key] == StepStatus.Success
    }

    fun canProceed(requiredSteps: List<String>): Boolean {
        return requiredSteps.all { isStepComplete(it) }
    }
}

sealed interface StepStatus {
    data object Idle : StepStatus
    data object Loading : StepStatus
    data object Success : StepStatus
    data class Error(val message: String) : StepStatus
}
