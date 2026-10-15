package com.sanibonani.save.features.member.presentation

import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.admin.PlatformRepository
import com.sanibonani.save.domain.repository.credit.LoanRepository
import com.sanibonani.save.domain.repository.disbursement.PayoutRepository
import com.sanibonani.save.domain.usecase.credit.CalculateLoanRepaymentScheduleUseCase
import com.sanibonani.save.domain.usecase.credit.ValidateLoanEligibilityUseCase
import com.sanibonani.save.features.member.presentation.state.MemberUiState
import com.sanibonani.save.features.common.presentation.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class MemberFinanceViewModel @Inject constructor(
    private val loanRepo: LoanRepository,
    private val payoutRepo: PayoutRepository,
    private val calculateSchedule: CalculateLoanRepaymentScheduleUseCase,
    private val platformRepo: PlatformRepository,
    private val validateLoanEligibility: ValidateLoanEligibilityUseCase
) : BaseViewModel<MemberUiState>(MemberUiState()) {

    val state: StateFlow<MemberUiState> = uiState

    fun setImpersonationContext(member: Member?, group: Group?) {
        updateState { it.copy(member = member, group = group, isImpersonating = member != null) }
    }

    suspend fun checkLoanEligibility(): ValidateLoanEligibilityUseCase.EligibilityResult {
        val group = uiState.value.group ?: return ValidateLoanEligibilityUseCase.EligibilityResult.Ineligible("No group selected.")
        val member = uiState.value.member ?: return ValidateLoanEligibilityUseCase.EligibilityResult.Ineligible("No member context.")
        return validateLoanEligibility(member, group)
    }

    fun requestLoan(amount: Money, purpose: String, period: Int) {
        val group = uiState.value.group ?: return
        val member = uiState.value.member ?: return
        launchJob("request_loan") {
            updateState { it.copy(isLoading = true) }

            val platformSettings = platformRepo.getPlatformSettings().getOrNull()
            val globalInterestRate = platformSettings?.loanInterestRate?.toDouble() ?: 20.0
            val globalProcessingFee = platformSettings?.loanProcessingFee ?: Money(15.0)
            val groupInterestRate = group.loanInterestRate
            val effectiveInterestRate = if (groupInterestRate != null && groupInterestRate > 0.0) groupInterestRate else globalInterestRate

            val scheduleRes = calculateSchedule(
                CalculateLoanRepaymentScheduleUseCase.Params(
                    principal = amount,
                    annualInterestRate = effectiveInterestRate,
                    termMonths = period,
                    processingFee = globalProcessingFee,
                    startDate = LocalDate.now()
                )
            )

            val loan = Loan(
                groupId = group.id!!,
                memberId = member.id!!,
                amount = amount,
                interestRate = effectiveInterestRate,
                totalToRepay = scheduleRes.totalToRepay,
                monthlyRepayment = scheduleRes.monthlyInstallment,
                startDate = LocalDate.now().toString(),
                endDate = LocalDate.now().plusMonths(period.toLong()).toString(),
                status = LoanStatus.PENDING,
                purpose = purpose,
                amortizationType = AmortizationType.FLAT
            )
            loanRepo.requestLoan(loan).onSuccess {
                updateState { it.copy(isLoading = false) }
                showMessage("Loan request submitted successfully")
            }.onFailure {
                updateState { it.copy(isLoading = false) }
                handleError(it)
            }
        }
    }

    fun requestWithdrawal(amount: Money, type: PayoutType) {
        val group = uiState.value.group ?: return
        val member = uiState.value.member ?: return
        launchJob("request_withdrawal") {
            updateState { it.copy(isLoading = true) }
            val request = PayoutRequest(
                groupId = group.id!!,
                memberId = member.id!!,
                amount = amount,
                type = type,
                status = PayoutStatus.PENDING,
                bankName = member.bankName ?: "",
                accountNo = member.accountNo ?: "",
                branchCode = member.branchCode ?: ""
            )
            payoutRepo.requestPayout(request).onSuccess {
                updateState { it.copy(isLoading = false) }
                showMessage("Withdrawal request submitted successfully")
            }.onFailure {
                updateState { it.copy(isLoading = false) }
                handleError(it)
            }
        }
    }
}
