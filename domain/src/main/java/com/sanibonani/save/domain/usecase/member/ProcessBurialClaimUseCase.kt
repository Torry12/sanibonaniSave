package com.sanibonani.save.domain.usecase.member
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.admin.BehaviorTrackingRepository
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
    private val validateBurialClaimEligibilityUseCase: ValidateBurialClaimEligibilityUseCase,
    private val behaviorTrackingRepository: BehaviorTrackingRepository
) {
    suspend operator fun invoke(
        claimId: String,
        status: BeneficiaryClaimStatus,
        reviewedBy: String,
        adminNotes: String? = null,
        rejectionReason: String? = null
    ): Result<BeneficiaryPayoutClaim> = safeRunCatching {
        // A dropped session hands over a blank id. Stamping a decision with it would leave the
        // claim unattributable and could let the cheque and the approval be made by nobody.
        if (reviewedBy.isBlank()) {
            throw IllegalStateException("A signed-in reviewer is required to change a claim status.")
        }
        val claim = claimRepository.getClaimById(claimId).getOrThrow()
        validateTransition(claim.status, status).getOrThrow()
        if (status == BeneficiaryClaimStatus.APPROVED || status == BeneficiaryClaimStatus.PAID) {
            val group = groupRepository.getGroupById(claim.groupId).getOrThrow()
            val member = memberRepository.getMemberById(claim.memberId).getOrThrow()

            securityService.validateLiquidity(claim.groupId, claim.claimAmount).getOrThrow()

            if (status == BeneficiaryClaimStatus.PAID) {
                securityService.assertMfaRequirement(claim.claimAmount).getOrThrow()
            }

            // The eligibility rule blocks a member in arrears, but only if it is told about them.
            // A failed read is not an absence of arrears, so it blocks the decision too.
            val behaviorTrack = behaviorTrackingRepository.getMemberBehavior(claim.memberId).getOrThrow()
            val eligibility = validateBurialClaimEligibilityUseCase(
                member = member,
                group = group,
                causeOfDeath = claim.causeOfDeath,
                dateOfDeath = claim.dateOfDeath,
                overdueAmount = behaviorTrack?.totalOutstandingAmount ?: Money.ZERO
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

    /**
     * Claims move forward only. Paying an already-paid or rejected claim would draw the funeral
     * cover twice, so terminal states accept no further decision.
     */
    private fun validateTransition(
        current: BeneficiaryClaimStatus,
        next: BeneficiaryClaimStatus
    ): Result<Unit> {
        if (current == next) {
            return Result.failure(IllegalStateException("Claim is already ${current.displayName}."))
        }
        val allowed = when (current) {
            // The group admin may approve and pay in one step; the platform reviews escalations
            // before any money moves.
            BeneficiaryClaimStatus.SUBMITTED -> listOf(
                BeneficiaryClaimStatus.UNDER_REVIEW,
                BeneficiaryClaimStatus.APPROVED,
                BeneficiaryClaimStatus.REJECTED,
                BeneficiaryClaimStatus.PAID,
                BeneficiaryClaimStatus.CANCELLED
            )
            BeneficiaryClaimStatus.UNDER_REVIEW -> listOf(
                BeneficiaryClaimStatus.APPROVED,
                BeneficiaryClaimStatus.REJECTED,
                BeneficiaryClaimStatus.ESCALATED,
                BeneficiaryClaimStatus.PAID,
                BeneficiaryClaimStatus.CANCELLED
            )
            BeneficiaryClaimStatus.ESCALATED -> listOf(
                BeneficiaryClaimStatus.APPROVED,
                BeneficiaryClaimStatus.REJECTED,
                BeneficiaryClaimStatus.CANCELLED
            )
            BeneficiaryClaimStatus.APPROVED -> listOf(
                BeneficiaryClaimStatus.PAID,
                BeneficiaryClaimStatus.REJECTED,
                BeneficiaryClaimStatus.CANCELLED
            )
            BeneficiaryClaimStatus.PAID,
            BeneficiaryClaimStatus.REJECTED,
            BeneficiaryClaimStatus.CANCELLED -> emptyList()
        }
        if (next !in allowed) {
            return Result.failure(
                IllegalStateException("Cannot move a claim from ${current.displayName} to ${next.displayName}.")
            )
        }
        return Result.success(Unit)
    }
}
