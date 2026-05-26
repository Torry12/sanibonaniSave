package com.sanibonani.save.domain.event

import com.sanibonani.save.domain.model.AppNotification
import com.sanibonani.save.domain.model.NotifChannel
import com.sanibonani.save.domain.model.NotifEvent
import com.sanibonani.save.domain.repository.NotificationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Sends notifications for key domain events.
 */
class NotificationEventHandler(
    private val notificationRepository: NotificationRepository
) : EventHandler<DomainEvent> {
    override suspend fun handle(event: DomainEvent) {
        when (event) {
            is LedgerEntryCreatedEvent -> {
                // Example: send notification to group admin
                val notification = AppNotification(
                    id = "audit_${event.entry.id}",
                    groupId = event.entry.groupId,
                    memberId = null,
                    message = "Ledger entry created: ${event.entry.description}",
                    triggerEvent = NotifEvent.LEDGER_ENTRY,
                    channel = NotifChannel.ADMIN
                )
                withContext(Dispatchers.IO) { notificationRepository.sendNotification(notification) }
            }
            is PaymentProcessedEvent -> {
                val notification = AppNotification(
                    id = "audit_${event.entry.transactionId}",
                    groupId = event.entry.groupId,
                    memberId = event.entry.memberId,
                    message = "Payment processed: ${event.entry.amount}",
                    triggerEvent = NotifEvent.PAYMENT_CONFIRMED,
                    channel = NotifChannel.BOTH
                )
                withContext(Dispatchers.IO) { notificationRepository.sendNotification(notification) }
            }
            else -> {}
        }
    }
}

