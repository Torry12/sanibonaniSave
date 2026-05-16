package com.sanibonani.save.domain.usecase

import com.sanibonani.save.domain.model.AppNotification
import com.sanibonani.save.domain.model.BeneficiaryClaimStatus
import com.sanibonani.save.domain.model.NotifChannel
import com.sanibonani.save.domain.model.NotifEvent
import com.sanibonani.save.domain.repository.BeneficiaryClaimRepository
import com.sanibonani.save.domain.repository.NotificationRepository
import com.sanibonani.save.domain.utils.OperationKeys
import javax.inject.Inject

/**
 * Handles the logic for a Platform Admin processing a burial claim (disbursement).
 * Ensures group balance is updated atomically when the claim is marked as PAID.
 */
class ProcessBurialClaimUseCase @Inject constructor(
    private val claimRepository: BeneficiaryClaimRepository,
    private val notificationRepository: NotificationRepository
) {
    suspend operator fun invoke(
        claimId: String,
        status: BeneficiaryClaimStatus,
        reviewedBy: String,
        adminNotes: String? = null,
        rejectionReason: String? = null
    ): Result<Unit> {
        // 1. Get claim details to know the amount and group
        val claimResult = claimRepository.getClaimById(claimId)
        val claim = claimResult.getOrNull()
        
        if (status == BeneficiaryClaimStatus.PAID && (claimResult.isFailure || claim == null)) {
            return Result.failure(claimResult.exceptionOrNull() ?: Exception("Failed to retrieve claim details for payment"))
        }

        // 2. Update status and balance atomically if paying
        val updateResult = if (status == BeneficiaryClaimStatus.PAID) {
            claimRepository.payClaimAtomic(claimId, reviewedBy, adminNotes)
        } else {
            claimRepository.updateClaimStatus(
                claimId = claimId,
                status = status,
                reviewedBy = reviewedBy,
                adminNotes = adminNotes,
                rejectionReason = rejectionReason
            )
        }

        if (updateResult.isSuccess) {
            // 3. Send notification
            val message = when (status) {
                BeneficiaryClaimStatus.APPROVED -> "Your burial claim for ${claim?.beneficiaryName} has been approved and is awaiting payment."
                BeneficiaryClaimStatus.PAID -> "The burial claim for ${claim?.beneficiaryName} has been paid successfully."
                BeneficiaryClaimStatus.REJECTED -> "Your burial claim has been rejected: $rejectionReason"
                else -> null
            }

            if (message != null && claim != null) {
                notificationRepository.sendNotification(
                    AppNotification(
                        id = OperationKeys.stableUuid("claim_transition_notification", claimId, status.name),
                        groupId = claim.groupId,
                        message = message,
                        triggerEvent = NotifEvent.CUSTOM,
                        channel = NotifChannel.BOTH
                    )
                )
            }
        }

        return updateResult
    }
}
