package com.sanibonani.save.features.admin.presentation.actions

import com.sanibonani.save.domain.utils.safeRunCatching
import com.sanibonani.save.domain.model.DocumentStatus
import com.sanibonani.save.domain.model.Money
import com.sanibonani.save.domain.model.MemberStatus
import com.sanibonani.save.domain.model.PayoutStatus
import com.sanibonani.save.domain.repository.auth.SupabaseRepository
import com.sanibonani.save.domain.repository.disbursement.PayoutRepository
import com.sanibonani.save.domain.usecase.disbursement.ProcessDisbursementUseCase
import com.sanibonani.save.domain.usecase.disbursement.RequestPayoutUseCase
import com.sanibonani.save.service.AdminGroupContextCacheService
import javax.inject.Inject

/**
 * Payout request flows: member payouts, ROSCA rotation, Stokvel batches,
 * platform escalation and payout document verification.
 */
class AdminPayoutActions @Inject constructor(
    private val requestPayoutUseCase: RequestPayoutUseCase,
    private val payoutRepo: PayoutRepository,
    private val processDisbursementUseCase: ProcessDisbursementUseCase,
    private val supabaseRepo: SupabaseRepository,
    private val adminContextCacheService: AdminGroupContextCacheService,
) {

    fun submitPayoutRequest(host: AdminFinancialActionHost) {
        val s = host.actionState
        val groupId = adminContextCacheService.selectedGroupId.value ?: return

        host.launchAction("submit_payout_request") {
            // Parsing on the UI thread before the coroutine turned a typo into a crash. The
            // amount is validated next to the call that spends it and reported like any other
            // action failure.
            val moneyAmount = try {
                if (s.payoutAmount.isBlank()) null else Money(s.payoutAmount.trim())
            } catch (e: NumberFormatException) {
                host.reportError(IllegalArgumentException("Enter a valid payout amount."))
                return@launchAction
            }
            host.updateActionState { it.copy(isRequestingPayout = true, error = null) }
            requestPayoutUseCase(
                groupId = groupId,
                memberId = s.selectedMemberId,
                requestedAmount = moneyAmount
            ).onSuccess {
                host.updateActionState {
                    it.copy(
                        isRequestingPayout = false,
                        payoutRequestSuccess = true,
                        payoutAmount = "",
                        selectedMemberId = null,
                        successMessage = "Payout request submitted successfully"
                    )
                }
            }.onFailure { e ->
                host.updateActionState { it.copy(isRequestingPayout = false) }
                host.reportActionError(e)
            }
        }
    }

    fun executeRoscaPayout(host: AdminFinancialActionHost) {
        val groupId = adminContextCacheService.selectedGroupId.value ?: return
        host.launchAction("execute_rosca_payout") {
            host.updateActionState { it.copy(isRequestingPayout = true, error = null) }
            requestPayoutUseCase(groupId = groupId).onSuccess { result ->
                host.updateActionState { it.copy(isRequestingPayout = false, successMessage = result) }
            }.onFailure { e ->
                host.updateActionState { it.copy(isRequestingPayout = false) }
                host.reportActionError(e)
            }
        }
    }

    fun executeStokvelBatchPayout(host: AdminFinancialActionHost) {
        val groupId = adminContextCacheService.selectedGroupId.value ?: return
        host.launchAction("execute_stokvel_batch_payout") {
            host.updateActionState { it.copy(isRequestingPayout = true, error = null) }
            val members = host.actionState.members.filter {
                it.status == MemberStatus.ACTIVE || it.status == MemberStatus.PROBATION
            }
            var successCount = 0
            var failedCount = 0
            for (m in members) {
                val memberId = m.id
                if (memberId == null) {
                    failedCount++
                    continue
                }
                val outcome = safeRunCatching {
                    requestPayoutUseCase(groupId = groupId, memberId = memberId).getOrThrow()
                }
                if (outcome.isSuccess) successCount++ else failedCount++
            }
            // A batch that silently drops members reads as a completed payout round, so the
            // failure count is reported alongside the successes.
            host.updateActionState {
                it.copy(
                    isRequestingPayout = false,
                    payoutRequestSuccess = successCount > 0,
                    successMessage = if (successCount > 0) {
                        "Annual Stokvel savings payouts requested for $successCount member(s)."
                    } else null,
                    error = if (failedCount > 0) {
                        "$failedCount member(s) could not be requested. Please review and retry."
                    } else null
                )
            }
        }
    }

    fun approveAndEscalatePayoutRequest(host: AdminFinancialActionHost, payoutId: String) {
        host.launchAction("approve_escalate_payout") {
            val adminId = supabaseRepo.currentUserId?.takeIf { it.isNotBlank() }
            if (adminId == null) {
                host.reportError(IllegalStateException("Sign in again to escalate a payout request."))
                return@launchAction
            }
            host.updateActionState { it.copy(isLoading = true, error = null, processingIds = it.processingIds + payoutId) }
            // Through the disbursement use case so the transition, the liquidity check and the
            // platform audit trail all see the escalation, exactly as on the platform screen.
            processDisbursementUseCase(
                payoutId = payoutId,
                status = PayoutStatus.GROUP_APPROVED,
                adminId = adminId
            ).onSuccess { updated ->
                host.updateActionState { s ->
                    s.copy(
                        payouts = s.payouts.map { if (it.id == updated.id) updated else it },
                        isLoading = false,
                        successMessage = "Request escalated for platform approval"
                    )
                }
            }.onFailure { e ->
                host.updateActionState { it.copy(isLoading = false) }
                host.reportActionError(e)
            }
            host.updateActionState { it.copy(processingIds = it.processingIds - payoutId) }
        }
    }

    fun verifyPayoutDocument(host: AdminFinancialActionHost, payoutId: String, approve: Boolean) {
        val status = if (approve) DocumentStatus.VERIFIED else DocumentStatus.REJECTED
        host.launchAction("verify_payout_document") {
            host.updateActionState { it.copy(isLoading = true, error = null) }
            payoutRepo.verifyPayoutDocument(payoutId, status)
                .onSuccess {
                    host.updateActionState { it.copy(isLoading = false, successMessage = "Payout document ${if (approve) "verified" else "rejected"}") }
                }
                .onFailure { e ->
                    host.updateActionState { it.copy(isLoading = false) }
                    host.reportError(e)
                }
        }
    }
}
