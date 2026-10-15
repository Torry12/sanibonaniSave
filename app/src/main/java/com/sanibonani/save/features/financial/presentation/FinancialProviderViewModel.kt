package com.sanibonani.save.features.financial.presentation

import com.sanibonani.save.domain.model.FinancialProvider
import com.sanibonani.save.domain.repository.payment.FinancialProviderRepository
import com.sanibonani.save.domain.utils.toUserMessage
import com.sanibonani.save.features.common.presentation.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

data class FinancialProviderUiState(
    val providers: List<FinancialProvider> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class FinancialProviderViewModel @Inject constructor(
    private val providerRepo: FinancialProviderRepository
) : BaseViewModel<FinancialProviderUiState>(FinancialProviderUiState()) {

    val state: StateFlow<FinancialProviderUiState> = uiState

    init {
        loadProviders()
    }

    fun loadProviders() {
        launchJob("load_providers") {
            updateState { it.copy(isLoading = true, error = null) }
            providerRepo.observeProviders().collect { result ->
                result.onSuccess { list ->
                    updateState { it.copy(providers = list, isLoading = false) }
                }.onFailure { e ->
                    updateState { it.copy(isLoading = false, error = e.toUserMessage()) }
                }
            }
        }
    }
}
