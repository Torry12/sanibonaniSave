package com.sanibonani.save.domain.usecase.system
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.system.NotificationRepository
import javax.inject.Inject
/**
 * Orchestrates the sending of multi-channel notifications (SMS, Email, Push).
 */
class SendNotificationUseCase @Inject constructor(
    private val notificationRepository: NotificationRepository
) {
    suspend operator fun invoke(
        groupId: String?,
        message: String,
        memberId: String? = null,
        channel: NotifChannel = NotifChannel.BOTH,
        triggerEvent: NotifEvent = NotifEvent.CUSTOM
    ): Result<Unit> {
        val notification = AppNotification(
            groupId = groupId,
            memberId = memberId,
            message = message,
            channel = channel,
            triggerEvent = triggerEvent
        )
        return notificationRepository.sendNotification(notification)
    }
}
