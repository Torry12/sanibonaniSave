package com.sanibonani.save.domain.event

import com.sanibonani.save.domain.logging.AppLogger
import com.sanibonani.save.domain.repository.admin.BehaviorTrackingRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates member behavior updates based on domain events.
 * Listens for financial and status events to trigger scoring and metrics recalculation.
 */
@Singleton
class BehaviorTrackingEventHandler @Inject constructor(
    private val behaviorRepository: BehaviorTrackingRepository
) : EventHandler<DomainEvent> {

    override suspend fun handle(event: DomainEvent) {
        when (event) {
            is PaymentProcessedEvent -> handlePayment(event)
            is LoanApprovedEvent -> handleLoanUpdate(event.loan.memberId, event.loan.groupId)
            is LoanDisbursedEvent -> handleLoanUpdate(event.loan.memberId, event.loan.groupId)
            is MemberStatusChangedEvent -> handleStatusChange(event)
            is ClaimProcessedEvent -> handleClaimUpdate(event.claim.memberId, event.claim.groupId)
            else -> {}
        }
    }

    private suspend fun handlePayment(event: PaymentProcessedEvent) {
        val payment = event.payment
        AppLogger.d("BehaviorTrackingEventHandler", "Payment processed for member ${payment.memberId}, updating behavior metrics")
        behaviorRepository.calculateAndUpdateMemberBehavior(payment.memberId, payment.groupId)
    }

    private suspend fun handleLoanUpdate(memberId: String, groupId: String) {
        AppLogger.d("BehaviorTrackingEventHandler", "Loan update for member $memberId, updating behavior metrics")
        behaviorRepository.calculateAndUpdateMemberBehavior(memberId, groupId)
    }

    private suspend fun handleStatusChange(event: MemberStatusChangedEvent) {
        val member = event.member
        val memberId = member.id ?: return
        AppLogger.d("BehaviorTrackingEventHandler", "Status changed for member $memberId, syncing behavior track")
        behaviorRepository.calculateAndUpdateMemberBehavior(memberId, member.groupId)
    }

    private suspend fun handleClaimUpdate(memberId: String, groupId: String) {
        AppLogger.d("BehaviorTrackingEventHandler", "Claim processed for member $memberId, updating behavior metrics")
        behaviorRepository.calculateAndUpdateMemberBehavior(memberId, groupId)
    }
}
