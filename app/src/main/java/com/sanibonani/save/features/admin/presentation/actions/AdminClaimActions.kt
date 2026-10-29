package com.sanibonani.save.features.admin.presentation.actions

import com.sanibonani.save.domain.model.BeneficiaryClaimStatus
import com.sanibonani.save.domain.usecase.auth.GetAttributableActorIdUseCase
import com.sanibonani.save.domain.usecase.member.ProcessBurialClaimUseCase
import com.sanibonani.save.domain.usecase.member.VerifyBurialClaimDocumentUseCase
import com.sanibonani.save.service.AdminGroupContextCacheService
import javax.inject.Inject

/**
 * Burial claim review lifecycle: status decisions and supporting document verification.
 */
class AdminClaimActions @Inject constructor(
    private val processBurialClaim: ProcessBurialClaimUseCase,
    private val verifyClaimDocumentUseCase: VerifyBurialClaimDocumentUseCase,
    private val getAttributableActorId: GetAttributableActorIdUseCase,
    private val adminContextCacheService: AdminGroupContextCacheService,
) {

    fun processClaim(host: AdminFinancialActionHost, claimId: String, approve: Boolean) {
        val groupId = adminContextCacheService.selectedGroupId.value ?: return
        // The domain refuses an unattributable decision, so report a blank session
        // instead of launching a call that must fail or silently dropping the tap.
        val adminId = getAttributableActorId()
        if (adminId == null) {
            host.reportError(IllegalStateException("Sign in again to change a claim status."))
            return
        }
        host.launchAction("process_claim") {
            host.updateActionState { it.copy(isLoading = true, error = null, processingIds = it.processingIds + claimId) }
            val status = if (approve) BeneficiaryClaimStatus.PAID else BeneficiaryClaimStatus.REJECTED
            processBurialClaim(
                claimId = claimId,
                status = status,
                reviewedBy = adminId
            ).onSuccess { updated ->
                host.updateActionState { s ->
                    s.copy(
                        burialClaims = s.burialClaims.map { if (it.id == updated.id) updated else it },
                        isLoading = false,
                        successMessage = "Claim ${if (approve) "processed as PAID" else "rejected"}"
                    )
                }
                if (approve) adminContextCacheService.warmUpGroup(groupId)
            }.onFailure { e ->
                host.updateActionState { it.copy(isLoading = false) }
                host.reportActionError(e)
            }
            host.updateActionState { it.copy(processingIds = it.processingIds - claimId) }
        }
    }

    fun verifyClaimDocument(host: AdminFinancialActionHost, claimId: String, docKey: String, approve: Boolean) {
        host.launchAction("verify_claim_document") {
            host.updateActionState { it.copy(isLoading = true, error = null) }
            verifyClaimDocumentUseCase(claimId, docKey, approve)
                .onSuccess {
                    host.updateActionState { it.copy(isLoading = false, successMessage = "Claim document ${if (approve) "verified" else "rejected"}") }
                }
                .onFailure { e ->
                    host.updateActionState { it.copy(isLoading = false) }
                    host.reportError(e)
                }
        }
    }
}
