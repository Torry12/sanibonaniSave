package com.sanibonani.save.domain.usecase

import com.sanibonani.save.domain.model.AppNotification
import com.sanibonani.save.domain.model.NotifChannel
import com.sanibonani.save.domain.model.NotifEvent
import com.sanibonani.save.domain.repository.NotificationRepository
import javax.inject.Inject

/**
 * Orchestrates sending notifications via multiple channels (App, WhatsApp).
 * Centralizes messaging logic for both Admins and Members.
 */
class SendNotificationUseCase @Inject constructor(
    private val notificationRepository: NotificationRepository
) {
    suspend operator fun invoke(
        groupId: String,
        message: String,
        memberId: String? = null, // If null, it's a broadcast to the group
        triggerEvent: NotifEvent = NotifEvent.CUSTOM,
        channel: NotifChannel = NotifChannel.BOTH
    ): Result<Unit> {
        if (message.isBlank()) return Result.failure(Exception("Message cannot be empty"))

        val notification = AppNotification(
            groupId = groupId,
            memberId = memberId,
            message = message,
            triggerEvent = triggerEvent,
            channel = channel
        )

        return notificationRepository.sendNotification(notification)
    }

    suspend fun notifyPlatformAdmin(message: String): Result<Unit> {
        return notificationRepository.notifyPlatformAdmin(message)
    }
}
