package com.sanibonani.save.domain.usecase.disbursement

import com.sanibonani.save.domain.model.BeneficiaryClaimStatus
import com.sanibonani.save.domain.model.Money
import com.sanibonani.save.domain.repository.disbursement.BeneficiaryClaimRepository
import com.sanibonani.save.domain.repository.*
import com.sanibonani.save.domain.event.EventBus
import com.sanibonani.save.domain.event.ClaimProcessedEvent
import javax.inject.Inject

class ProcessBurialClaimUseCase @Inject constructor(
    private val claimRepository: BeneficiaryClaimRepository,
    private val groupRepository: GroupRepository,
    private val memberRepository: MemberRepository,
    private val supabaseRepository: SupabaseRepository,
    private val validateBurialClaimEligibilityUseCase: ValidateBurialClaimEligibilityUseCase
) {
    companion object {
        private val MFA_THRESHOLD = Money(10000.0)
    }

    suspend operator fun invoke(
        claimId: String,
        status: BeneficiaryClaimStatus,
        reviewedBy: String,
        adminNotes: String? = null,
        rejectionReason: String? = null
    ): Result<Unit> = runCatching {
        val claim = claimRepository.getClaimById(claimId).getOrThrow()

        if (status == BeneficiaryClaimStatus.APPROVED || status == BeneficiaryClaimStatus.PAID) {
            val group = groupRepository.getGroupById(claim.groupId).getOrThrow()
            val member = memberRepository.getMemberById(claim.memberId).getOrThrow()

            if (status == BeneficiaryClaimStatus.PAID && claim.claimAmount >= MFA_THRESHOLD) {
                if (!supabaseRepository.isMfaVerified) {
                    throw Exception("MFA_REQUIRED: AAL2 verification needed.")
                }
            }

            val eligibility = validateBurialClaimEligibilityUseCase(
                member = member,
                group = group,
                causeOfDeath = claim.causeOfDeath,
                dateOfDeath = claim.dateOfDeath
            )

            if (eligibility is ValidateBurialClaimEligibilityUseCase.EligibilityResult.Ineligible) {
                throw Exception("COMPLIANCE_ERROR: ${eligibility.reason}")
            }
        }

        val updateResult = if (status == BeneficiaryClaimStatus.PAID) {
            claimRepository.payClaimAtomic(claimId, reviewedBy, adminNotes)
        } else {
            claimRepository.updateClaimStatus(claimId, status, reviewedBy, adminNotes, rejectionReason)
        }

        if (updateResult.isSuccess) {
            EventBus.emit(ClaimProcessedEvent(claim, status))
        }

        return updateResult
    }
}
