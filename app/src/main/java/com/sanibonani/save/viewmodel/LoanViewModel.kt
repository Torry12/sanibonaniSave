package com.sanibonani.save.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.sanibonani.save.domain.repository.LoanRepository
import com.sanibonani.save.domain.model.Loan

@HiltViewModel
class LoanViewModel @Inject constructor(
    private val loanRepository: LoanRepository
) : ViewModel() {

    private val _state = MutableStateFlow(LoanUiState())
    val state: StateFlow<LoanUiState> = _state.asStateFlow()

    fun approveAndDisburseLoan(loanId: String, adminId: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, success = false) }
            loanRepository.approveLoan(loanId)
                .onSuccess {
                    _state.update { it.copy(success = true, isLoading = false) }
                }
                .onFailure { e ->
                    _state.update { it.copy(error = e.toUserMessage(), isLoading = false) }
                }
        }
    }

    fun getLoanById(loanId: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            loanRepository.getLoanById(loanId)
                .onSuccess { loan ->
                    _state.update { it.copy(loan = loan, isLoading = false) }
                }
                .onFailure { e ->
                    _state.update { it.copy(error = e.toUserMessage(), isLoading = false) }
                }
        }
    }
}

data class LoanUiState(
    val loan: Loan? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val success: Boolean = false
)

