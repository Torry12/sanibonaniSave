package com.sanibonani.save.domain.event

import com.sanibonani.save.domain.logging.AppLogger
import com.sanibonani.save.domain.repository.dynamics.BehaviorTrackingRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
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

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun registerListeners() {
        EventBus.subscribe<PaymentProcessedEvent>(this)
        EventBus.subscribe<LoanApprovedEvent>(this)
        EventBus.subscribe<LoanDisbursedEvent>(this)
        EventBus.subscribe<MemberStatusChangedEvent>(this)
        EventBus.subscribe<ClaimProcessedEvent>(this)
        AppLogger.i("BehaviorTrackingEventHandler", "Registered for financial and status events")
    }

    override fun onEvent(event: DomainEvent) {
        scope.launch {
            when (event) {
                is PaymentProcessedEvent -> handlePayment(event)
                is LoanApprovedEvent -> handleLoanUpdate(event.loan.memberId, event.loan.groupId)
                is LoanDisbursedEvent -> handleLoanUpdate(event.loan.memberId, event.loan.groupId)
                is MemberStatusChangedEvent -> handleStatusChange(event)
                is ClaimProcessedEvent -> handleClaimUpdate(event.claim.memberId, event.claim.groupId)
            }
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
