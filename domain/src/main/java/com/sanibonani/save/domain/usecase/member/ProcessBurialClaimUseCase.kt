package com.sanibonani.save.domain.usecase.member
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.member.BeneficiaryClaimRepository
import com.sanibonani.save.domain.repository.group.*
import com.sanibonani.save.domain.repository.member.*
import com.sanibonani.save.domain.event.EventBus
import com.sanibonani.save.domain.event.ClaimProcessedEvent
import javax.inject.Inject
import com.sanibonani.save.domain.utils.safeRunCatching
class ProcessBurialClaimUseCase @Inject constructor(
    private val claimRepository: BeneficiaryClaimRepository,
    private val groupRepository: GroupRepository,
    private val memberRepository: MemberRepository,
    private val securityService: com.sanibonani.save.domain.service.FinancialSecurityService,
    private val validateBurialClaimEligibilityUseCase: ValidateBurialClaimEligibilityUseCase
) {
    suspend operator fun invoke(
        claimId: String,
        status: BeneficiaryClaimStatus,
        reviewedBy: String,
        adminNotes: String? = null,
        rejectionReason: String? = null
    ): Result<BeneficiaryPayoutClaim> = safeRunCatching {
        val claim = claimRepository.getClaimById(claimId).getOrThrow()
        if (status == BeneficiaryClaimStatus.APPROVED || status == BeneficiaryClaimStatus.PAID) {
            val group = groupRepository.getGroupById(claim.groupId).getOrThrow()
            val member = memberRepository.getMemberById(claim.memberId).getOrThrow()

            securityService.validateLiquidity(claim.groupId, claim.claimAmount).getOrThrow()

            if (status == BeneficiaryClaimStatus.PAID) {
                securityService.assertMfaRequirement(claim.claimAmount).getOrThrow()
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

        val updated = claimRepository.getClaimById(claimId).getOrThrow()

        if (updateResult.isSuccess) {
            EventBus.emit(ClaimProcessedEvent(updated, status))
        }
        updateResult.getOrThrow()
        return Result.success(updated)
    }
}
