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
        yocoPayoutId: String? = null
    ): Result<Unit> {
        val updateResult = payoutRepository.updatePayoutStatus(payoutId, status, yocoPayoutId)
        
        if (updateResult.isSuccess) {
            val message = when (status) {
                PayoutStatus.PROCESSING -> "Your payout request is now being processed."
                PayoutStatus.COMPLETED -> "Your disbursement request has been processed successfully. Funds are on their way."
                PayoutStatus.FAILED -> "Your disbursement request could not be processed. Please contact support."
                PayoutStatus.CANCELLED -> "Your payout request has been cancelled."
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
