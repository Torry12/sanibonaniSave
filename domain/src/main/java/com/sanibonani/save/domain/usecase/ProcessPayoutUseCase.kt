package com.sanibonani.save.domain.usecase

import com.sanibonani.save.domain.model.AppNotification
import com.sanibonani.save.domain.model.NotifChannel
import com.sanibonani.save.domain.model.NotifEvent
import com.sanibonani.save.domain.model.PayoutStatus
import com.sanibonani.save.domain.repository.NotificationRepository
import com.sanibonani.save.domain.repository.PayoutRepository
import com.sanibonani.save.domain.utils.OperationKeys
import javax.inject.Inject

/**
 * Handles the logic for a Platform Admin processing a disbursement request.
 */
class ProcessPayoutUseCase @Inject constructor(
    private val payoutRepository: PayoutRepository,
    private val notificationRepository: NotificationRepository
) {
    suspend operator fun invoke(
        payoutId: String,
        groupId: String,
        status: PayoutStatus,
        adminId: String,
        payoutReference: String? = null
    ): Result<Unit> {
        // 1. Get current payout data to know the amount if completing
        val payoutResult = payoutRepository.getPayoutById(payoutId)
        val payout = payoutResult.getOrNull()

        if (payoutResult.isFailure || payout == null) {
            return Result.failure(payoutResult.exceptionOrNull() ?: Exception("Payout not found: $payoutId"))
        }

        // 2. Update status and balance atomically if completing
        val updateResult = if (status == PayoutStatus.COMPLETED) {
            payoutRepository.completePayoutAtomic(payoutId, adminId, payoutReference)
        } else {
            payoutRepository.updatePayoutStatus(payoutId, status, payoutReference)
        }
        
        if (updateResult.isSuccess) {
            val message = when (status) {
                PayoutStatus.PROCESSING -> "Your payout request of R${"%.2f".format(payout.amount)} is now being processed."
                PayoutStatus.COMPLETED -> "Your disbursement request of R${"%.2f".format(payout.amount)} has been processed successfully. Funds are on their way."
                PayoutStatus.FAILED -> "Your disbursement request of R${"%.2f".format(payout.amount)} could not be processed. Please contact support."
                PayoutStatus.CANCELLED -> "Your payout request of R${"%.2f".format(payout.amount)} has been cancelled."
                else -> null
            }
            
            message?.let {
                notificationRepository.sendNotification(
                    AppNotification(
                        id = OperationKeys.stableUuid("payout_transition_notification", payoutId, status.name),
                        groupId = groupId,
                        message = it,
                        triggerEvent = NotifEvent.CUSTOM,
                        channel = NotifChannel.BOTH
                    )
                )
            }
        }
        
        return updateResult
    }
}
