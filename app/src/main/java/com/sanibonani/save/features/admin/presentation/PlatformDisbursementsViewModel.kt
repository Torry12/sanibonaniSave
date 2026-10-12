package com.sanibonani.save.features.admin.presentation

import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.auth.SupabaseRepository
import com.sanibonani.save.domain.repository.credit.LoanRepository
import com.sanibonani.save.domain.repository.disbursement.PayoutRepository
import com.sanibonani.save.domain.repository.member.BeneficiaryClaimRepository
import com.sanibonani.save.domain.repository.member.MemberRepository
import com.sanibonani.save.domain.usecase.disbursement.ProcessDisbursementUseCase
import com.sanibonani.save.domain.usecase.credit.ProcessLoanDecisionUseCase
import com.sanibonani.save.domain.usecase.credit.ProcessLoanDisbursementUseCase
import com.sanibonani.save.domain.usecase.member.ProcessBurialClaimUseCase
import com.sanibonani.save.features.admin.presentation.state.PlatformDisbursementsUiState
import com.sanibonani.save.features.common.presentation.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class PlatformDisbursementsViewModel @Inject constructor(
    private val claimRepo: BeneficiaryClaimRepository,
    private val payoutRepo: PayoutRepository,
    private val memberRepo: MemberRepository,
    private val loanRepo: LoanRepository,
    private val processLoanDecisionUseCase: ProcessLoanDecisionUseCase,
    private val processLoanDisbursementUseCase: ProcessLoanDisbursementUseCase,
    private val processDisbursementUseCase: ProcessDisbursementUseCase,
    private val processBurialClaimUseCase: ProcessBurialClaimUseCase,
    private val supabaseRepo: SupabaseRepository
) : BaseViewModel<PlatformDisbursementsUiState>(PlatformDisbursementsUiState()) {

    val state: StateFlow<PlatformDisbursementsUiState> = uiState

    private val _mfaRequired = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val mfaRequired: Flow<Unit> = _mfaRequired.asSharedFlow()

    fun load() {
        launchJob("load_disbursements") {
            coroutineScope {
                val claimsDeferred = async { claimRepo.getEscalatedClaimsSync().getOrNull() ?: emptyList() }
                val payoutsDeferred = async { payoutRepo.getPendingPayouts().getOrNull() ?: emptyList() }
                val loansDeferred = async { loanRepo.getAllPendingLoansSync().getOrNull() ?: emptyList() }

                val claims = claimsDeferred.await()
                val payouts = payoutsDeferred.await()
                val loans = loansDeferred.await()

                updateState { it.copy(
                    escalatedClaims = claims,
                    payouts = payouts,
                    loanRequestsByGroup = loans.groupBy { it.groupId }
                ) }
            }
        }
    }

    fun approvePayout(payoutId: String, groupId: String) {
        val payout = uiState.value.payouts.find { it.id == payoutId } ?: return
        val nextStatus = when (payout.status) {
            PayoutStatus.PENDING -> PayoutStatus.GROUP_APPROVED
            PayoutStatus.GROUP_APPROVED -> PayoutStatus.PROCESSING
            PayoutStatus.FAILED -> PayoutStatus.PROCESSING
            else -> return
        }
        processPayoutInternal(payoutId, groupId, nextStatus)
    }

    fun rejectPayout(payoutId: String, groupId: String) {
        val payout = uiState.value.payouts.find { it.id == payoutId } ?: return
        val nextStatus = when (payout.status) {
            PayoutStatus.PROCESSING -> PayoutStatus.FAILED
            else -> PayoutStatus.REJECTED
        }
        processPayoutInternal(payoutId, groupId, nextStatus)
    }

    fun completePayout(payoutId: String, groupId: String) {
        processPayoutInternal(payoutId, groupId, PayoutStatus.COMPLETED)
    }

    private fun processPayoutInternal(payoutId: String, groupId: String, status: PayoutStatus) {
        launchJob("process_payout_$payoutId") {
            updateState { it.copy(isProcessingPayout = true, processingIds = it.processingIds + payoutId) }
            processDisbursementUseCase(
                payoutId = payoutId,
                groupId = groupId,
                status = status,
                adminId = supabaseRepo.currentUserId ?: ""
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
        val adminId = supabaseRepo.currentUserId ?: return
        launchJob("approve_loan_$loanId") {
            updateState { it.copy(isProcessingLoanRequest = true, processingIds = it.processingIds + loanId) }
            processLoanDecisionUseCase(
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
            }.onFailure { handleError(it) }
            updateState { it.copy(isProcessingLoanRequest = false, processingIds = it.processingIds - loanId) }
        }
    }

    fun rejectLoanRequest(loan: Loan, reason: String) {
        val loanId = loan.id ?: return
        val adminId = supabaseRepo.currentUserId ?: return
        launchJob("reject_loan_$loanId") {
            updateState { it.copy(isProcessingLoanRequest = true, processingIds = it.processingIds + loanId) }
            processLoanDecisionUseCase(
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
            }.onFailure { handleError(it) }
            updateState { it.copy(isProcessingLoanRequest = false, processingIds = it.processingIds - loanId) }
        }
    }

    fun disburseLoan(loan: Loan) {
        val loanId = loan.id ?: return
        val adminId = supabaseRepo.currentUserId ?: return
        launchJob("disburse_loan_$loanId") {
            updateState { it.copy(isProcessingLoanRequest = true, processingIds = it.processingIds + loanId) }
            processLoanDisbursementUseCase(loanId, adminId, PaymentMethod.BANK).onSuccess { updated ->
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
        launchJob("process_claim_$claimId") {
            updateState { it.copy(isProcessingClaim = true, processingIds = it.processingIds + claimId) }
            processBurialClaimUseCase(
                claimId = claimId,
                status = status,
                reviewedBy = supabaseRepo.currentUserId ?: "",
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
            memberRepo.getMemberById(memberId).onSuccess { member ->
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
        val msg = e.message ?: ""
        if (msg.contains("MFA_REQUIRED")) {
            _mfaRequired.tryEmit(Unit)
        } else {
            handleError(e)
        }
    }
}
