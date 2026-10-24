package com.sanibonani.save.domain.event

import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.system.NotificationRepository
import javax.inject.Inject

/**
 * Handles group-wide broadcast notifications for all fund disbursements.
 * Ensures every member is notified when funds leave the group account.
 */
class DisbursementBroadcastHandler @Inject constructor(
    private val notificationRepository: NotificationRepository
) : EventHandler<DomainEvent> {

    override suspend fun handle(event: DomainEvent) {
        val notification = when (event) {
            is PayoutProcessedEvent -> {
                when (event.status) {
                    PayoutStatus.COMPLETED -> AppNotification(
                        id = "broadcast_payout_${event.payout.id}",
                        groupId = event.payout.groupId,
                        message = "Group Disbursement: A payout of ${event.payout.amount.format()} has been processed successfully.",
                        triggerEvent = NotifEvent.PAYOUT_PROCESSED,
                        channel = NotifChannel.BOTH,
                        memberId = null
                    )
                    PayoutStatus.REJECTED -> AppNotification(
                        id = "payout_rejected_${event.payout.id}",
                        groupId = event.payout.groupId,
                        message = "Payout Request Rejected: The payout of ${event.payout.amount.format()} has been rejected.",
                        triggerEvent = NotifEvent.PAYOUT_PROCESSED,
                        channel = NotifChannel.PUSH,
                        memberId = event.payout.memberId // Direct to requester
                    )
                    else -> null
                }
            }

            is ClaimProcessedEvent -> {
                if (event.status == BeneficiaryClaimStatus.PAID) {
                    AppNotification(
                        id = "broadcast_claim_${event.claim.id}",
                        groupId = event.claim.groupId,
                        message = "Group Disbursement: A burial claim for ${event.claim.beneficiaryName} (${event.claim.claimAmount.format()}) has been paid.",
                        triggerEvent = NotifEvent.PAYOUT_PROCESSED,
                        channel = NotifChannel.BOTH,
                        memberId = null // Broadcast to all
                    )
                } else null
            }

            is LoanDisbursedEvent -> {
                AppNotification(
                    id = "broadcast_loan_${event.loan.id}",
                    groupId = event.loan.groupId,
                    message = "Group Disbursement: A loan of ${event.loan.amount.format()} has been disbursed to a member.",
                    triggerEvent = NotifEvent.PAYOUT_PROCESSED,
                    channel = NotifChannel.BOTH,
                    memberId = null // Broadcast to all
                )
            }

            else -> null
        }

        notification?.let {
            notificationRepository.sendNotification(it)
        }
    }
}
