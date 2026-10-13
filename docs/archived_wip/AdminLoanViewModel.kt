package com.sanibonani.save.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanibonani.save.data.logging.AppLogger
import com.sanibonani.save.data.utils.toUserMessage
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.LoanRepository
import com.sanibonani.save.domain.usecase.GenerateLoanContractUseCase
import com.sanibonani.save.domain.usecase.ValidateLoanEligibilityUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AdminLoanViewModel @Inject constructor(
    private val loanRepo: LoanRepository,
    private val validateLoanEligibilityUseCase: ValidateLoanEligibilityUseCase,
    private val generateLoanContractUseCase: GenerateLoanContractUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(AdminLoanUiState())
    val state: StateFlow<AdminLoanUiState> = _state.asStateFlow()

    private val selectedGroupId = MutableStateFlow<String?>(null)
    private var observationJob: Job? = null

    fun setGroupId(groupId: String?) {
        if (selectedGroupId.value == groupId) return
        selectedGroupId.value = groupId
        if (groupId != null) {
            startObserving(groupId)
        } else {
            observationJob?.cancel()
            _state.update { AdminLoanUiState() }
        }
    }

    private fun startObserving(groupId: String) {
        observationJob?.cancel()
        observationJob = viewModelScope.launch {
            loanRepo.getGroupLoans(groupId).collect { result ->
                _state.update { it.copy(loans = result.getOrDefault(emptyList())) }
            }
        }
    }

    fun approveLoan(loanId: String, group: Group, member: Member) {
        viewModelScope.launch {
            _state.update { it.copy(isProcessing = true) }
            loanRepo.approveLoan(loanId)
                .onSuccess {
                    val loan = loanRepo.getLoanById(loanId).getOrNull()
                    if (loan != null) {
                        generateLoanContractUseCase(loan, member, group)
                            .onSuccess { file ->
                                loanRepo.uploadLoanContract(loanId, file.readBytes(), "Loan_Agreement_${member.fullName.replace(" ", "_")}.pdf")
                            }
                    }
                    _state.update { it.copy(isProcessing = false, successMessage = "Loan approved and contract generated") }
                }
                .onFailure { e ->
                    _state.update { it.copy(isProcessing = false, error = e.toUserMessage()) }
                }
        }
    }

    fun disburseLoan(loanId: String, paymentMethod: PaymentMethod) {
        viewModelScope.launch {
            _state.update { it.copy(isProcessing = true) }
            loanRepo.disburseLoan(loanId, paymentMethod)
                .onSuccess {
                    _state.update { it.copy(isProcessing = false, successMessage = "Loan disbursed") }
                }
                .onFailure { e ->
                    _state.update { it.copy(isProcessing = false, error = e.toUserMessage()) }
                }
        }
    }

    fun rejectLoan(loanId: String, reason: String) {
        viewModelScope.launch {
            _state.update { it.copy(isProcessing = true) }
            loanRepo.rejectLoan(loanId, reason)
                .onSuccess {
                    _state.update { it.copy(isProcessing = false, successMessage = "Loan rejected") }
                }
                .onFailure { e ->
                    _state.update { it.copy(isProcessing = false, error = e.toUserMessage()) }
                }
        }
    }

    fun clearMessages() {
        _state.update { it.copy(successMessage = null, error = null) }
    }
}

data class AdminLoanUiState(
    val loans: List<Loan> = emptyList(),
    val isProcessing: Boolean = false,
    val successMessage: String? = null,
    val error: String? = null
)
