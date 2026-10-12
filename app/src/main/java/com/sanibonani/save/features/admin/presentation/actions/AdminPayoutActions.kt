package com.sanibonani.save.features.admin.presentation.actions

import com.sanibonani.save.domain.utils.safeRunCatching
import com.sanibonani.save.domain.model.DocumentStatus
import com.sanibonani.save.domain.model.Money
import com.sanibonani.save.domain.model.MemberStatus
import com.sanibonani.save.domain.model.PayoutStatus
import com.sanibonani.save.domain.repository.disbursement.PayoutRepository
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
    private val adminContextCacheService: AdminGroupContextCacheService,
) {

    fun submitPayoutRequest(host: AdminFinancialActionHost) {
        val s = host.actionState
        val groupId = adminContextCacheService.selectedGroupId.value ?: return
        val moneyAmount = if (s.payoutAmount.isBlank()) null else Money(s.payoutAmount)

        host.launchAction("submit_payout_request") {
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
            for (m in members) {
                m.id?.let { mId ->
                    safeRunCatching {
                        requestPayoutUseCase(groupId = groupId, memberId = mId).getOrThrow()
                        successCount++
                    }
                }
            }
            host.updateActionState {
                it.copy(
                    isRequestingPayout = false,
                    payoutRequestSuccess = true,
                    successMessage = "Annual Stokvel savings payouts requested for $successCount member(s)."
                )
            }
        }
    }

    fun approveAndEscalatePayoutRequest(host: AdminFinancialActionHost, payoutId: String) {
        host.launchAction("approve_escalate_payout") {
            host.updateActionState { it.copy(isLoading = true, error = null, processingIds = it.processingIds + payoutId) }
            payoutRepo.updatePayoutStatus(payoutId, PayoutStatus.GROUP_APPROVED).onSuccess {
                host.updateActionState { s ->
                    s.copy(
                        payouts = s.payouts.map {
                            if (it.id == payoutId) it.copy(status = PayoutStatus.GROUP_APPROVED) else it
                        },
                        isLoading = false,
                        successMessage = "Request escalated for platform approval"
                    )
                }
            }.onFailure { e ->
                host.updateActionState { it.copy(isLoading = false) }
                host.reportError(e)
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
