package com.sanibonani.save.viewmodel.credit

import androidx.lifecycle.viewModelScope
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.credit.LoanRepository
import com.sanibonani.save.domain.usecase.CalculateLoanRepaymentScheduleUseCase
import com.sanibonani.save.domain.usecase.TransitionLoanStatusUseCase
import com.sanibonani.save.viewmodel.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoanUiState(
    val loan: Loan? = null,
    val previewSchedule: CalculateLoanRepaymentScheduleUseCase.Result? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val success: Boolean = false
)

@HiltViewModel
class LoanViewModel @Inject constructor(
    private val loanRepository: LoanRepository,
    private val calculateScheduleUseCase: CalculateLoanRepaymentScheduleUseCase,
    private val transitionStatusUseCase: TransitionLoanStatusUseCase
) : BaseViewModel<LoanUiState>(LoanUiState()) {

    val state: StateFlow<LoanUiState> = uiState
    private val isActive = MutableStateFlow(false)

    fun setActive(active: Boolean) {
        isActive.value = active
    }

    fun calculatePreview(amount: Money, rate: Double, months: Int, type: AmortizationType) {
        val result = calculateScheduleUseCase(
            CalculateLoanRepaymentScheduleUseCase.Params(
                principal = amount,
                annualInterestRate = rate,
                termMonths = months,
                amortizationType = type
            )
        )
        updateState { it.copy(previewSchedule = result) }
    }

    fun approveAndDisburseLoan(loanId: String, adminId: String) {
        viewModelScope.launch {
            updateState { it.copy(isLoading = true, error = null, success = false) }
            withLoading {
                transitionStatusUseCase(loanId, LoanStatus.APPROVED, adminId)
                    .onSuccess {
                        updateState { it.copy(success = true, isLoading = false) }
                    }
                    .onFailure { e ->
                        updateState { it.copy(isLoading = false) }
                        handleError(e)
                    }
            }
        }
    }

    fun getLoanById(loanId: String) {
        viewModelScope.launch {
            updateState { it.copy(isLoading = true, error = null) }
            loanRepository.getLoanById(loanId)
                .onSuccess { loan ->
                    updateState { it.copy(loan = loan, isLoading = false) }
                }
                .onFailure { e ->
                    updateState { it.copy(isLoading = false) }
                    handleError(e)
                }
        }
    }

    override fun onCleared() {
        super.onCleared()
        updateState { LoanUiState() }
    }
}
