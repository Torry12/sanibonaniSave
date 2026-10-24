package com.sanibonani.save.features.member.presentation

import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.admin.PlatformRepository
import com.sanibonani.save.domain.repository.credit.LoanRepository
import com.sanibonani.save.domain.repository.disbursement.PayoutRepository
import com.sanibonani.save.domain.service.PayoutGuard
import com.sanibonani.save.domain.usecase.credit.CalculateLoanRepaymentScheduleUseCase
import com.sanibonani.save.domain.usecase.credit.ValidateLoanEligibilityUseCase
import com.sanibonani.save.domain.validation.InputValidator
import com.sanibonani.save.domain.validation.ValidationResult
import com.sanibonani.save.features.member.presentation.state.MemberUiState
import com.sanibonani.save.features.common.presentation.BaseViewModel
import com.sanibonani.save.features.common.presentation.UIEvent
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
    private val validateLoanEligibility: ValidateLoanEligibilityUseCase,
    private val payoutGuard: PayoutGuard
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
        val groupId = group.id.serverIdOrReport("group") ?: return
        val memberId = member.id.serverIdOrReport("member") ?: return
        launchJob("request_loan") {
            updateState { it.copy(isLoading = true) }

            // The configured rate and fee are the price of this loan, so a failed settings read
            // must stop the request instead of billing an invented default. PlatformConfig itself
            // carries the documented defaults, which keeps it the single source for them.
            val platformSettings = platformRepo.getPlatformSettings().getOrElse { error ->
                updateState { it.copy(isLoading = false) }
                handleError(error)
                return@launchJob
            }
            val globalInterestRate = platformSettings.loanInterestRate.toDouble()
            val globalProcessingFee = platformSettings.loanProcessingFee
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
                groupId = groupId,
                memberId = memberId,
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
        val groupId = group.id.serverIdOrReport("group") ?: return
        val memberId = member.id.serverIdOrReport("member") ?: return

        val bankName = member.bankName.orEmpty().trim()
        val accountNo = member.accountNo.orEmpty().trim()
        val branchCode = member.branchCode.orEmpty().trim()

        // Checked before the request is queued: the processor rejects blank or malformed details
        // after the treasury has already reserved the amount.
        val detailsError = when {
            bankName.isEmpty() -> "Add your bank name before requesting a withdrawal."
            InputValidator.validateAccountNumber(accountNo, bankName) !is ValidationResult.Valid ->
                "Add a valid account number before requesting a withdrawal."
            branchCode.isEmpty() || InputValidator.validateBranchCode(branchCode) !is ValidationResult.Valid ->
                "Add a valid 6-digit branch code before requesting a withdrawal."
            else -> null
        }
        if (detailsError != null) {
            updateState { it.copy(isLoading = false) }
            sendEvent(UIEvent.ShowError(detailsError))
            return
        }

        launchJob("request_withdrawal") {
            updateState { it.copy(isLoading = true) }
            // Same solvency, suspension and probation guards the admin payout flow runs through.
            payoutGuard.validateRequest(group, member, amount, type).onFailure { e ->
                updateState { it.copy(isLoading = false) }
                handleError(e)
                return@launchJob
            }
            val request = PayoutRequest(
                groupId = groupId,
                memberId = memberId,
                amount = amount,
                type = type,
                status = PayoutStatus.PENDING,
                bankName = bankName,
                accountNo = accountNo,
                branchCode = branchCode
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
