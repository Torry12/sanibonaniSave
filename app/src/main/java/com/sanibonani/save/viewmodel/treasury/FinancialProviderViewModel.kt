package com.sanibonani.save.viewmodel.treasury

import androidx.lifecycle.viewModelScope
import com.sanibonani.save.domain.model.FinancialProvider
import com.sanibonani.save.domain.repository.FinancialProviderRepository
import com.sanibonani.save.viewmodel.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
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
        viewModelScope.launch {
            updateState { it.copy(isLoading = true, error = null) }
            providerRepo.observeProviders().collect { result ->
                result.onSuccess { list ->
                    updateState { it.copy(providers = list, isLoading = false) }
                }.onFailure { e ->
                    updateState { it.copy(isLoading = false, error = e.message) }
                }
            }
        }
    }
}
