package com.sanibonani.save.domain.event

import com.sanibonani.save.domain.model.AppNotification
import com.sanibonani.save.domain.model.NotifChannel
import com.sanibonani.save.domain.model.NotifEvent
import com.sanibonani.save.domain.model.PaymentType
import com.sanibonani.save.domain.repository.NotificationRepository
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Sends notifications for key domain events.
 */
class NotificationEventHandler @Inject constructor(
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
                    triggerEvent = NotifEvent.DEPOSIT_RECEIVED, // More accurate for ledger entry
                    channel = NotifChannel.BOTH
                )
                withContext(Dispatchers.IO) { notificationRepository.sendNotification(notification) }
            }
            is PaymentProcessedEvent -> {
                val triggerEvent = when (event.payment.paymentType) {
                    PaymentType.LOAN_DISBURSEMENT -> NotifEvent.LOAN_APPROVED
                    PaymentType.CLAIM -> NotifEvent.PAYOUT_PROCESSED
                    else -> NotifEvent.PAYMENT_CONFIRMED
                }

                val notification = AppNotification(
                    id = "audit_${event.payment.transactionId}",
                    groupId = event.payment.groupId,
                    memberId = event.payment.memberId,
                    message = "Payment processed: ${event.payment.amount}",
                    triggerEvent = triggerEvent,
                    channel = NotifChannel.BOTH
                )
                withContext(Dispatchers.IO) { notificationRepository.sendNotification(notification) }
            }
            else -> {}
        }
    }
}
