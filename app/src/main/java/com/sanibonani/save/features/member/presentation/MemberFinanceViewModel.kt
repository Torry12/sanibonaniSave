package com.sanibonani.save.features.member.presentation

import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.credit.LoanRepository
import com.sanibonani.save.features.member.presentation.state.MemberUiState
import com.sanibonani.save.viewmodel.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MemberFinanceViewModel @Inject constructor(
    private val loanRepo: LoanRepository
) : BaseViewModel<MemberUiState>(MemberUiState()) {

    val state: StateFlow<MemberUiState> = uiState

    fun setImpersonationContext(member: Member?, group: Group?) {
        updateState { it.copy(member = member, group = group, isImpersonating = member != null) }
    }

    fun requestLoan(amount: Money, memberId: String, period: Int) {
        val group = uiState.value.group ?: return
        val member = uiState.value.member ?: return
        launchJob("request_loan") {
            updateState { it.copy(isLoading = true) }
            val loan = Loan(
                groupId = group.id!!,
                memberId = member.id!!,
                amount = amount,
                status = LoanStatus.PENDING
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
}
