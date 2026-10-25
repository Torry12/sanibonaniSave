package com.sanibonani.save.features.member.presentation

import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.usecase.credit.RequestLoanUseCase
import com.sanibonani.save.domain.usecase.credit.ValidateLoanEligibilityUseCase
import com.sanibonani.save.domain.usecase.disbursement.RequestWithdrawalUseCase
import com.sanibonani.save.features.member.presentation.state.MemberUiState
import com.sanibonani.save.features.common.presentation.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class MemberFinanceViewModel @Inject constructor(
    private val requestLoanUseCase: RequestLoanUseCase,
    private val requestWithdrawalUseCase: RequestWithdrawalUseCase,
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
        group.id.serverIdOrReport("group") ?: return
        member.id.serverIdOrReport("member") ?: return
        launchJob("request_loan") {
            updateState { it.copy(isLoading = true) }
            requestLoanUseCase(
                RequestLoanUseCase.Params(
                    group = group,
                    member = member,
                    amount = amount,
                    purpose = purpose,
                    termMonths = period
                )
            ).onSuccess {
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
        group.id.serverIdOrReport("group") ?: return
        member.id.serverIdOrReport("member") ?: return
        launchJob("request_withdrawal") {
            updateState { it.copy(isLoading = true) }
            requestWithdrawalUseCase(
                RequestWithdrawalUseCase.Params(
                    group = group,
                    member = member,
                    amount = amount,
                    type = type
                )
            ).onSuccess {
                updateState { it.copy(isLoading = false) }
                showMessage("Withdrawal request submitted successfully")
            }.onFailure {
                updateState { it.copy(isLoading = false) }
                handleError(it)
            }
        }
    }
}
