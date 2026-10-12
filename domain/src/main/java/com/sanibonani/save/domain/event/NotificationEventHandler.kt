package com.sanibonani.save.domain.event

import com.sanibonani.save.domain.model.AppNotification
import com.sanibonani.save.domain.model.NotifChannel
import com.sanibonani.save.domain.model.NotifEvent
import com.sanibonani.save.domain.model.PaymentType
import com.sanibonani.save.domain.model.PayoutStatus
import com.sanibonani.save.domain.model.BeneficiaryClaimStatus
import com.sanibonani.save.domain.repository.system.NotificationRepository
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
            is PayoutProcessedEvent -> {
                val message = when (event.status) {
                    PayoutStatus.PROCESSING -> "Your payout request of ${event.payout.amount.format()} is now being processed."
                    PayoutStatus.COMPLETED -> "Your disbursement request of ${event.payout.amount.format()} has been processed successfully. Funds are on their way."
                    PayoutStatus.FAILED -> "Your disbursement request of ${event.payout.amount.format()} could not be processed. Please contact support."
                    PayoutStatus.CANCELLED -> "Your payout request of ${event.payout.amount.format()} has been cancelled."
                    PayoutStatus.REJECTED -> "Your payout request of ${event.payout.amount.format()} has been rejected."
                    else -> null
                }
                if (message != null) {
                    val notification = AppNotification(
                        id = "payout_${event.payout.id}_${event.status.name}",
                        groupId = event.payout.groupId,
                        memberId = event.payout.memberId,
                        message = message,
                        triggerEvent = NotifEvent.CUSTOM,
                        channel = NotifChannel.BOTH
                    )
                    withContext(Dispatchers.IO) { notificationRepository.sendNotification(notification) }
                }
            }
            is ClaimProcessedEvent -> {
                val message = when (event.status) {
                    BeneficiaryClaimStatus.APPROVED -> "Your burial claim for ${event.claim.beneficiaryName} has been approved."
                    BeneficiaryClaimStatus.PAID -> "Your burial claim of ${event.claim.claimAmount.format()} has been paid."
                    BeneficiaryClaimStatus.REJECTED -> "Your burial claim for ${event.claim.beneficiaryName} was rejected."
                    else -> null
                }
                if (message != null) {
                    val notification = AppNotification(
                        id = "claim_${event.claim.id}_${event.status.name}",
                        groupId = event.claim.groupId,
                        memberId = event.claim.memberId,
                        message = message,
                        triggerEvent = NotifEvent.CUSTOM,
                        channel = NotifChannel.BOTH
                    )
                    withContext(Dispatchers.IO) { notificationRepository.sendNotification(notification) }
                }
            }
            else -> {}
        }
    }
}
