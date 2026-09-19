package com.sanibonani.save.viewmodel.credit

import androidx.lifecycle.viewModelScope
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.*
import com.sanibonani.save.domain.repository.credit.LoanRepository
import com.sanibonani.save.domain.repository.disbursement.PayoutRepository
import com.sanibonani.save.domain.usecase.credit.CalculateLoanRepaymentScheduleUseCase
import com.sanibonani.save.domain.usecase.credit.ValidateLoanEligibilityUseCase
import com.sanibonani.save.service.MemberGroupContextCacheService
import com.sanibonani.save.viewmodel.BaseViewModel
import com.sanibonani.save.viewmodel.state.MemberUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class MemberFinanceViewModel @Inject constructor(
    private val memberContextCacheService: MemberGroupContextCacheService,
    private val loanRepo: LoanRepository,
    private val payoutRepo: PayoutRepository,
    private val validateLoanEligibilityUseCase: ValidateLoanEligibilityUseCase,
    private val calculateLoanScheduleUseCase: CalculateLoanRepaymentScheduleUseCase,
    private val exportRepo: ExportRepository
) : BaseViewModel<MemberUiState>(MemberUiState()) {

    val state: StateFlow<MemberUiState> = uiState

    private var contextObservationJob: Job? = null

    init {
        observeContext()
    }

    private fun observeContext() {
        contextObservationJob?.cancel()
        contextObservationJob = observeGroupContext(
            groupIdFlow = uiState.map { it.selectedGroupId },
            contextSource = memberContextCacheService.contexts
        ) { context ->
            if (context == null || uiState.value.isImpersonating) return@observeGroupContext

            updateState { it.copy(
                member = context.member,
                group = context.group,
                contributions = context.contributions,
                loans = context.loans,
                loanRepayments = context.loanRepayments,
                paymentCalculation = context.calculation
            ) }

            checkLoanEligibility(context.member, context.group)
        }
    }

    fun setImpersonationContext(member: Member?, group: Group?) {
        updateState { it.copy(member = member, group = group, isImpersonating = member != null) }
        checkLoanEligibility(member, group)
    }

    private fun checkLoanEligibility(member: Member?, group: Group?) {
        if (member == null || group == null) return
        viewModelScope.launch {
            val result = validateLoanEligibilityUseCase(member, group)
            when (result) {
                is ValidateLoanEligibilityUseCase.EligibilityResult.Eligible -> {
                    updateState { it.copy(isEligibleForLoan = true, loanIneligibilityReason = null) }
                }
                is ValidateLoanEligibilityUseCase.EligibilityResult.Ineligible -> {
                    updateState { it.copy(isEligibleForLoan = false, loanIneligibilityReason = result.reason) }
                }
            }
        }
    }

    fun requestLoan(amount: Money, months: Int, purpose: String) {
        val member = uiState.value.member ?: return
        val group = uiState.value.group ?: return

        viewModelScope.launch {
            updateState { it.copy(isLoading = true) }
            withLoading {
                val params = CalculateLoanRepaymentScheduleUseCase.Params(
                    principal = amount,
                    annualInterestRate = group.loanInterestRate ?: 0.0,
                    termMonths = months,
                    amortizationType = AmortizationType.FLAT,
                    startDate = LocalDate.now()
                )
                val schedule = calculateLoanScheduleUseCase(params)

                val loan = Loan(
                    memberId = member.id!!,
                    groupId = group.id!!,
                    amount = amount,
                    interestRate = group.loanInterestRate ?: 0.0,
                    totalToRepay = schedule.totalToRepay,
                    monthlyRepayment = schedule.monthlyInstallment,
                    status = LoanStatus.PENDING,
                    purpose = purpose
                )

                loanRepo.requestLoan(loan).onSuccess {
                    updateState { it.copy(isLoading = false) }
                }.onFailure { e ->
                    updateState { it.copy(isLoading = false) }
                    handleError(e)
                }
            }
        }
    }

    fun selectGroup(groupId: String) {
        updateState { it.copy(selectedGroupId = groupId) }
    }

    fun requestWithdrawal(amount: Money, type: PayoutType) {
        val member = uiState.value.member ?: return
        val group = uiState.value.group ?: return

        viewModelScope.launch {
            updateState { it.copy(isLoading = true) }
            val payout = PayoutRequest(
                groupId = group.id!!,
                memberId = member.id!!,
                amount = amount,
                bankName = member.bankName ?: group.bankName ?: "N/A",
                accountNo = member.accountNo ?: group.accountNumber ?: "000000000",
                branchCode = member.branchCode ?: group.branchCode ?: "000000",
                status = PayoutStatus.PENDING,
                type = type
            )

            payoutRepo.requestPayout(payout).onSuccess {
                updateState { it.copy(isLoading = false) }
            }.onFailure { e ->
                updateState { it.copy(isLoading = false) }
                handleError(e)
            }
        }
    }
}
