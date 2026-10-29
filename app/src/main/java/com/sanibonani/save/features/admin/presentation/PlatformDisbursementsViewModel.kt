package com.sanibonani.save.features.admin.presentation

import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.usecase.auth.GetAttributableActorIdUseCase
import com.sanibonani.save.domain.usecase.credit.GetPendingLoanRequestsUseCase
import com.sanibonani.save.domain.usecase.disbursement.GetPendingPayoutsUseCase
import com.sanibonani.save.domain.usecase.disbursement.ProcessDisbursementUseCase
import com.sanibonani.save.domain.usecase.credit.ProcessLoanDecisionUseCase
import com.sanibonani.save.domain.usecase.credit.ProcessLoanDisbursementUseCase
import com.sanibonani.save.domain.usecase.member.GetEscalatedClaimsUseCase
import com.sanibonani.save.domain.usecase.member.GetMemberByIdUseCase
import com.sanibonani.save.domain.usecase.member.ProcessBurialClaimUseCase
import com.sanibonani.save.domain.utils.requiresMfaChallenge
import com.sanibonani.save.features.admin.presentation.state.PlatformDisbursementsUiState
import com.sanibonani.save.features.common.presentation.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class PlatformDisbursementsViewModel @Inject constructor(
    private val getEscalatedClaims: GetEscalatedClaimsUseCase,
    private val getPendingPayouts: GetPendingPayoutsUseCase,
    private val getMemberById: GetMemberByIdUseCase,
    private val getPendingLoanRequests: GetPendingLoanRequestsUseCase,
    private val processLoanDecision: ProcessLoanDecisionUseCase,
    private val processLoanDisbursement: ProcessLoanDisbursementUseCase,
    private val processDisbursement: ProcessDisbursementUseCase,
    private val processBurialClaim: ProcessBurialClaimUseCase,
    private val getAttributableActorId: GetAttributableActorIdUseCase
) : BaseViewModel<PlatformDisbursementsUiState>(PlatformDisbursementsUiState()) {

    val state: StateFlow<PlatformDisbursementsUiState> = uiState

    private val _mfaRequired = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val mfaRequired: Flow<Unit> = _mfaRequired.asSharedFlow()

    fun load() {
        launchJob("load_disbursements") {
            coroutineScope {
                val claimsDeferred = async { getEscalatedClaims() }
                val payoutsDeferred = async { getPendingPayouts() }
                val loansDeferred = async { getPendingLoanRequests() }

                val claimsResult = claimsDeferred.await()
                val payoutsResult = payoutsDeferred.await()
                val loansResult = loansDeferred.await()

                // A failed fetch is not a verified empty response. Only sections that
                // answered are replaced; a failed section keeps what is on screen.
                updateState { it.copy(
                    escalatedClaims = claimsResult.getOrNull() ?: it.escalatedClaims,
                    payouts = payoutsResult.getOrNull() ?: it.payouts,
                    loanRequestsByGroup = loansResult.getOrNull()?.groupBy { loan -> loan.groupId }
                        ?: it.loanRequestsByGroup
                ) }

                // Name the failed sections: an admin on a device with silenced logcat
                // cannot otherwise tell claims drift from an offline payout fetch.
                val failedSections = listOfNotNull(
                    claimsResult.exceptionOrNull()?.let { "escalated claims" },
                    payoutsResult.exceptionOrNull()?.let { "payout requests" },
                    loansResult.exceptionOrNull()?.let { "loan requests" }
                )
                if (failedSections.isNotEmpty()) {
                    val firstError = claimsResult.exceptionOrNull()
                        ?: payoutsResult.exceptionOrNull()
                        ?: loansResult.exceptionOrNull()!!
                    handleError(firstError, "Could not refresh ${failedSections.joinToString(", ")}.")
                }
            }
        }
    }

    fun approvePayout(payoutId: String) {
        val payout = uiState.value.payouts.find { it.id == payoutId } ?: return
        val nextStatus = when (payout.status) {
            PayoutStatus.PENDING -> PayoutStatus.GROUP_APPROVED
            PayoutStatus.GROUP_APPROVED -> PayoutStatus.PROCESSING
            PayoutStatus.FAILED -> PayoutStatus.PROCESSING
            else -> return
        }
        processPayoutInternal(payoutId, nextStatus)
    }

    fun rejectPayout(payoutId: String) {
        val payout = uiState.value.payouts.find { it.id == payoutId } ?: return
        val nextStatus = when (payout.status) {
            PayoutStatus.PROCESSING -> PayoutStatus.FAILED
            else -> PayoutStatus.REJECTED
        }
        processPayoutInternal(payoutId, nextStatus)
    }

    fun completePayout(payoutId: String) {
        processPayoutInternal(payoutId, PayoutStatus.COMPLETED)
    }

    private fun adminIdOrReport(action: String): String? {
        val adminId = getAttributableActorId()
        if (adminId == null) {
            handleError(
                IllegalStateException("A signed-in reviewer is required to change a $action status."),
                "Sign in again to change a $action status."
            )
        }
        return adminId
    }

    private fun processPayoutInternal(payoutId: String, status: PayoutStatus) {
        val adminId = adminIdOrReport("payout") ?: return
        launchJob("process_payout_$payoutId") {
            updateState { it.copy(isProcessingPayout = true, processingIds = it.processingIds + payoutId) }
            processDisbursement(
                payoutId = payoutId,
                status = status,
                adminId = adminId
            ).onSuccess { updated ->
                updateState { s -> s.copy(
                    payouts = s.payouts.map { if (it.id == updated.id) updated else it }
                ) }
                showMessage("Payout status updated to ${status.name}")
                load()
            }.onFailure { handleActionFailure(it) }
            updateState { it.copy(isProcessingPayout = false, processingIds = it.processingIds - payoutId) }
        }
    }

    fun approveLoanRequest(loan: Loan) {
        val loanId = loan.id ?: return
        val adminId = adminIdOrReport("loan") ?: return
        launchJob("approve_loan_$loanId") {
            updateState { it.copy(isProcessingLoanRequest = true, processingIds = it.processingIds + loanId) }
            processLoanDecision(
                loanId = loanId,
                decision = ProcessLoanDecisionUseCase.LoanDecision.Approve,
                adminId = adminId
            ).onSuccess { updated ->
                updateState { s -> s.copy(
                    loanRequestsByGroup = s.loanRequestsByGroup.mapValues { (_, list) ->
                        list.map { if (it.id == updated.id) updated else it }
                    }
                ) }
                showMessage("Loan approved")
                load()
            }.onFailure { handleActionFailure(it) }
            updateState { it.copy(isProcessingLoanRequest = false, processingIds = it.processingIds - loanId) }
        }
    }

    fun rejectLoanRequest(loan: Loan, reason: String) {
        val loanId = loan.id ?: return
        val adminId = adminIdOrReport("loan") ?: return
        launchJob("reject_loan_$loanId") {
            updateState { it.copy(isProcessingLoanRequest = true, processingIds = it.processingIds + loanId) }
            processLoanDecision(
                loanId = loanId,
                decision = ProcessLoanDecisionUseCase.LoanDecision.Reject(reason),
                adminId = adminId
            ).onSuccess { updated ->
                updateState { s -> s.copy(
                    loanRequestsByGroup = s.loanRequestsByGroup.mapValues { (_, list) ->
                        list.map { if (it.id == updated.id) updated else it }
                    }
                ) }
                showMessage("Loan rejected")
                load()
            }.onFailure { handleActionFailure(it) }
            updateState { it.copy(isProcessingLoanRequest = false, processingIds = it.processingIds - loanId) }
        }
    }

    fun disburseLoan(loan: Loan) {
        val loanId = loan.id ?: return
        val adminId = adminIdOrReport("loan") ?: return
        launchJob("disburse_loan_$loanId") {
            updateState { it.copy(isProcessingLoanRequest = true, processingIds = it.processingIds + loanId) }
            processLoanDisbursement(loanId, adminId, PaymentMethod.BANK).onSuccess { updated ->
                updateState { s -> s.copy(
                    loanRequestsByGroup = s.loanRequestsByGroup.mapValues { (_, list) ->
                        list.map { if (it.id == updated.id) updated else it }
                    }
                ) }
                showMessage("Loan disbursed")
                load()
            }.onFailure { handleActionFailure(it) }
            updateState { it.copy(isProcessingLoanRequest = false, processingIds = it.processingIds - loanId) }
        }
    }

    fun approveBurialClaim(claimId: String, reason: String) {
        processBurialClaimInternal(claimId, BeneficiaryClaimStatus.APPROVED, reason)
    }

    fun rejectBurialClaim(claimId: String, reason: String) {
        processBurialClaimInternal(claimId, BeneficiaryClaimStatus.REJECTED, reason)
    }

    fun payBurialClaim(claimId: String, reason: String) {
        processBurialClaimInternal(claimId, BeneficiaryClaimStatus.PAID, reason)
    }

    private fun processBurialClaimInternal(claimId: String, status: BeneficiaryClaimStatus, reason: String) {
        val adminId = adminIdOrReport("claim") ?: return
        launchJob("process_claim_$claimId") {
            updateState { it.copy(isProcessingClaim = true, processingIds = it.processingIds + claimId) }
            processBurialClaim(
                claimId = claimId,
                status = status,
                reviewedBy = adminId,
                adminNotes = if (status != BeneficiaryClaimStatus.REJECTED) reason else null,
                rejectionReason = if (status == BeneficiaryClaimStatus.REJECTED) reason else null
            ).onSuccess { updated ->
                updateState { s -> s.copy(
                    escalatedClaims = s.escalatedClaims.map { if (it.id == updated.id) updated else it }
                ) }
                showMessage("Claim status updated to ${status.name}")
                load()
            }.onFailure { handleActionFailure(it) }
            updateState { it.copy(isProcessingClaim = false, processingIds = it.processingIds - claimId) }
        }
    }

    fun fetchMemberSummary(memberId: String, groupId: String) {
        launchJob("member_summary") {
            updateState { it.copy(isLoadingMemberSummary = true) }
            getMemberById(memberId).onSuccess { member ->
                updateState { it.copy(selectedMemberSummary = MemberSummary(member = member, behavior = null), isLoadingMemberSummary = false) }
            }.onFailure {
                updateState { it.copy(isLoadingMemberSummary = false) }
                handleError(it)
            }
        }
    }

    fun dismissMemberSummary() {
        updateState { it.copy(selectedMemberSummary = null) }
    }

    private fun handleActionFailure(e: Throwable) {
        if (e.requiresMfaChallenge()) {
            _mfaRequired.tryEmit(Unit)
        } else {
            handleError(e)
        }
    }
}
