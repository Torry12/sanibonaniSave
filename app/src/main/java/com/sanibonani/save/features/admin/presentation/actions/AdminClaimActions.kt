package com.sanibonani.save.features.admin.presentation.actions

import com.sanibonani.save.domain.model.BeneficiaryClaimStatus
import com.sanibonani.save.domain.model.DocumentStatus
import com.sanibonani.save.domain.repository.auth.SupabaseRepository
import com.sanibonani.save.domain.repository.member.BeneficiaryClaimRepository
import com.sanibonani.save.domain.usecase.member.ProcessBurialClaimUseCase
import com.sanibonani.save.service.AdminGroupContextCacheService
import javax.inject.Inject

/**
 * Burial claim review lifecycle: status decisions and supporting document verification.
 */
class AdminClaimActions @Inject constructor(
    private val processBurialClaimUseCase: ProcessBurialClaimUseCase,
    private val claimRepo: BeneficiaryClaimRepository,
    private val supabaseRepo: SupabaseRepository,
    private val adminContextCacheService: AdminGroupContextCacheService,
) {

    fun processClaim(host: AdminFinancialActionHost, claimId: String, approve: Boolean) {
        val groupId = adminContextCacheService.selectedGroupId.value ?: return
        val adminId = supabaseRepo.currentUserId ?: return
        host.launchAction("process_claim") {
            host.updateActionState { it.copy(isLoading = true, error = null, processingIds = it.processingIds + claimId) }
            val status = if (approve) BeneficiaryClaimStatus.PAID else BeneficiaryClaimStatus.REJECTED
            processBurialClaimUseCase(
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
        val status = if (approve) DocumentStatus.VERIFIED else DocumentStatus.REJECTED
        host.launchAction("verify_claim_document") {
            host.updateActionState { it.copy(isLoading = true, error = null) }
            claimRepo.verifyClaimDocument(claimId, docKey, status)
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
