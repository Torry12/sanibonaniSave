package com.sanibonani.save.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanibonani.save.data.utils.toUserMessage
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.*
import com.sanibonani.save.domain.usecase.SendNotificationUseCase
import com.sanibonani.save.domain.usecase.ValidateLoanEligibilityUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MemberLoanUiState(
    val loans: List<Loan> = emptyList(),
    val loanRepayments: List<LoanRepayment> = emptyList(),
    val isEligibleForLoan: Boolean = false,
    val loanIneligibilityReason: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null
)

@HiltViewModel
class MemberLoanViewModel @Inject constructor(
    private val loanRepo: LoanRepository,
    private val validateLoanEligibilityUseCase: ValidateLoanEligibilityUseCase,
    private val sendNotificationUseCase: SendNotificationUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(MemberLoanUiState())
    val state: StateFlow<MemberLoanUiState> = _state.asStateFlow()

    private var observationJob: Job? = null
    private var repaymentsJob: Job? = null
    private var currentGroupId: String? = null
    private var currentMemberId: String? = null

    fun setContext(groupId: String, memberId: String, member: Member?, group: Group?) {
        if (currentGroupId == groupId && currentMemberId == memberId) return
        currentGroupId = groupId
        currentMemberId = memberId
        
        startObserving(groupId, memberId)
        
        if (member != null && group != null) {
            checkLoanEligibility(member, group)
        }
    }

    private fun startObserving(groupId: String, memberId: String) {
        observationJob?.cancel()
        observationJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            loanRepo.getMemberLoans(memberId).collect { res ->
                res.onSuccess { loans ->
                    _state.update { it.copy(loans = loans, isLoading = false) }
                    val activeLoan = loans.find { it.status == LoanStatus.APPROVED || it.status == LoanStatus.ACTIVE || it.status == LoanStatus.PARTIALLY_PAID }
                    if (activeLoan != null) {
                        observeRepayments(activeLoan.id ?: "")
                    } else {
                        repaymentsJob?.cancel()
                        _state.update { it.copy(loanRepayments = emptyList()) }
                    }
                }.onFailure { e ->
                    _state.update { it.copy(isLoading = false, error = e.toUserMessage()) }
                }
            }
        }
    }

    private fun observeRepayments(loanId: String) {
        repaymentsJob?.cancel()
        repaymentsJob = viewModelScope.launch {
            loanRepo.getRepayments(loanId).collect { res ->
                res.onSuccess { list -> _state.update { it.copy(loanRepayments = list) } }
            }
        }
    }

    fun checkLoanEligibility(member: Member, group: Group) {
        viewModelScope.launch {
            val res = validateLoanEligibilityUseCase(member, group)
            _state.update { it.copy(
                isEligibleForLoan = res is ValidateLoanEligibilityUseCase.EligibilityResult.Eligible,
                loanIneligibilityReason = (res as? ValidateLoanEligibilityUseCase.EligibilityResult.Ineligible)?.reason
            ) }
        }
    }

    fun requestLoan(amount: Double, months: Int, purpose: String, member: Member, group: Group) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            
            // Re-check eligibility before submitting
            val eligibility = validateLoanEligibilityUseCase(member, group)
            if (eligibility is ValidateLoanEligibilityUseCase.EligibilityResult.Ineligible) {
                _state.update { it.copy(isLoading = false, error = eligibility.reason) }
                return@launch
            }

            val interestRate = group.loanInterestRate ?: 0.0
            val totalInterest = loanRepo.calculateInterest(amount, interestRate, months)
            
            val loan = Loan(
                groupId = group.id ?: "",
                memberId = member.id ?: "",
                amount = amount,
                interestRate = interestRate,
                totalToRepay = amount + totalInterest,
                totalRepaid = 0.0,
                monthlyRepayment = (amount + totalInterest) / months,
                startDate = com.sanibonani.save.data.utils.DateProvider.getCurrentDate().toString(),
                endDate = com.sanibonani.save.data.utils.DateProvider.getCurrentDate().plusMonths(months.toLong()).toString(),
                status = LoanStatus.PENDING,
                purpose = purpose
            )

            loanRepo.requestLoan(loan).onSuccess {
                _state.update { it.copy(isLoading = false, successMessage = "Loan request submitted successfully") }
                sendNotificationUseCase(
                    groupId = group.id ?: "",
                    message = "NEW LOAN REQUEST: Member ${member.fullName} requested R$amount",
                    triggerEvent = NotifEvent.LOAN_REQUESTED,
                    channel = NotifChannel.BOTH
                )
            }.onFailure { e ->
                _state.update { it.copy(isLoading = false, error = e.toUserMessage()) }
            }
        }
    }

    fun acceptLoanAgreement(loanId: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            loanRepo.acceptLoanAgreement(loanId).onSuccess {
                _state.update { it.copy(isLoading = false, successMessage = "Loan agreement accepted") }
            }.onFailure { e ->
                _state.update { it.copy(isLoading = false, error = e.toUserMessage()) }
            }
        }
    }

    fun clearMessages() {
        _state.update { it.copy(successMessage = null, error = null) }
    }

    override fun onCleared() {
        super.onCleared()
        observationJob?.cancel()
        repaymentsJob?.cancel()
    }
}
