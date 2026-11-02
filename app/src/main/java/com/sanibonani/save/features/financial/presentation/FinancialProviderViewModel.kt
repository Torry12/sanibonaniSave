package com.sanibonani.save.features.financial.presentation

import com.sanibonani.save.domain.model.FinancialProvider
import com.sanibonani.save.domain.usecase.payment.ObserveFinancialProvidersUseCase
import com.sanibonani.save.features.common.presentation.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

data class FinancialProviderUiState(
    val providers: List<FinancialProvider> = emptyList(),
    val isLoading: Boolean = false
)

@HiltViewModel
class FinancialProviderViewModel @Inject constructor(
    private val observeFinancialProviders: ObserveFinancialProvidersUseCase
) : BaseViewModel<FinancialProviderUiState>(FinancialProviderUiState()) {

    val state: StateFlow<FinancialProviderUiState> = uiState

    init {
        loadProviders()
    }

    fun loadProviders() {
        launchJob("load_providers") {
            updateState { it.copy(isLoading = true) }
            observeFinancialProviders().collect { result ->
                result.onSuccess { list ->
                    updateState { it.copy(providers = list, isLoading = false) }
                }.onFailure { e ->
                    updateState { it.copy(isLoading = false) }
                    // The state field this used to set was never rendered, so the failure has to
                    // reach the shared error channel to be visible at all.
                    handleError(e)
                }
            }
        }
    }
}
