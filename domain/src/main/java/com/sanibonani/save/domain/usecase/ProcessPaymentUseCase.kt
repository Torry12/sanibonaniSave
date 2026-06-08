package com.sanibonani.save.domain.usecase

import com.sanibonani.save.data.utils.PaymentCalculator
import com.sanibonani.save.domain.model.AdminFeeState
import com.sanibonani.save.domain.model.Contribution
import com.sanibonani.save.domain.model.ContributionStatus
import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.Member
import com.sanibonani.save.domain.model.Payment
import com.sanibonani.save.domain.model.PaymentMethod
import com.sanibonani.save.domain.model.PaymentStatus
import com.sanibonani.save.domain.model.PaymentType
import com.sanibonani.save.domain.model.PlatformFees
import com.sanibonani.save.domain.repository.GroupRepository
import com.sanibonani.save.domain.repository.MemberRepository
import com.sanibonani.save.domain.repository.NotificationRepository
import com.sanibonani.save.domain.repository.PaymentGatewayRepository
import com.sanibonani.save.domain.repository.PaymentRepository
import com.sanibonani.save.domain.repository.PlatformRepository
import com.sanibonani.save.domain.utils.OperationKeys
import com.sanibonani.save.domain.utils.isPositiveMoneyAmount
import com.sanibonani.save.domain.utils.toMoneyBigDecimal
import java.math.BigDecimal
import java.time.Instant
import javax.inject.Inject

/**
 * Orchestrates payment processing across different payment types and gateways.
 * Handles side effects like group activation, member registration, and notifications.
 */
class ProcessPaymentUseCase @Inject constructor(
    private val paymentRepository: PaymentRepository,
    private val groupRepository: GroupRepository,
    private val memberRepository: MemberRepository,
    private val notificationRepository: NotificationRepository,
    private val platformRepository: PlatformRepository,
    private val gatewayRepository: PaymentGatewayRepository
) {
    companion object {
        const val NEW_GROUP_SENTINEL = "new_group"
    }

    /**
     * Initiates a payment via a gateway. 
     * Returns the initiation result including the checkout URL.
     */
    suspend fun initiate(
        method: PaymentMethod,
        type: PaymentType,
        amount: Double,
        groupId: String,
        memberId: String? = null,
        description: String? = null
    ): Result<com.sanibonani.save.domain.repository.PaymentInitiationResult> {
        return gatewayRepository.initiatePayment(method, type, amount, groupId, memberId, description)
    }

    /**
     * Confirms and records a successful payment in the system.
     */
    suspend fun confirm(
        txId: String,
        method: PaymentMethod,
        type: PaymentType,
        amount: Double,
        groupId: String,
        member: Member? = null,
        group: Group? = null,
        calculation: com.sanibonani.save.data.utils.PaymentCalculation? = null
    ): Result<Unit> = runCatching {
        require(amount.isPositiveMoneyAmount()) { "Payment amount must be greater than zero." }

        val timestampStr = Instant.now().toString()

        when (type) {
            PaymentType.PLATFORM_FEE -> processPlatformFeePayment(txId, amount, groupId, group, member, timestampStr, method)
            PaymentType.JOINING_FEE -> processJoiningFeePayment(txId, amount, groupId, member, timestampStr, method)
            PaymentType.CONTRIBUTION -> processContributionPayment(txId, amount, groupId, member, group, calculation, timestampStr, method)
            else -> throw IllegalArgumentException("Payment type '${type.name}' is not supported in this flow.")
        }
    }

    private suspend fun processPlatformFeePayment(
        txId: String,
        amount: Double,
        groupId: String,
        group: Group?,
        member: Member?,
        timestampStr: String,
        method: PaymentMethod
    ) {
        if (groupId == NEW_GROUP_SENTINEL) return
        
        val feeType = if (group?.registrationPaid == false) "registration" else "monthly"

        if (group?.registrationPaid == false) {
            groupRepository.activateGroup(groupId, txId)
                .onFailure { throw it }
                .getOrThrow()
        } else {
            groupRepository.payPlatformFee(groupId, amount, feeType, txId)
                .onFailure { throw it }
                .getOrThrow()
            
            groupRepository.updateFeeStatus(groupId, AdminFeeState.PAID)
                .onFailure { throw it }
                .getOrThrow()
            platformRepository.unsuspendGroup(groupId)
                .onFailure { throw it }
                .getOrThrow()
        }

        recordPayment(txId, amount, groupId, PaymentType.PLATFORM_FEE, timestampStr, member?.id, method)
            .onFailure { throw it }
            .getOrThrow()
    }

    private suspend fun processJoiningFeePayment(
        txId: String,
        amount: Double,
        groupId: String,
        member: Member?,
        timestampStr: String,
        method: PaymentMethod
    ) {
        val targetMember = member ?: throw Exception("Member context required for joining fee")

        val registeredMember = memberRepository.registerMember(targetMember, txId)
            .onFailure { throw it }
            .getOrThrow()
        val registeredMemberId = registeredMember.id ?: throw IllegalStateException("Registered member has no ID")

        recordPayment(txId, amount, groupId, PaymentType.JOINING_FEE, timestampStr, registeredMemberId, method)
            .onFailure { throw it }
            .getOrThrow()
            
        // Side effects (Audit/Notification) are now handled by DomainEventDispatcher via recordPayment()
    }

    private suspend fun processContributionPayment(
        txId: String,
        amount: Double,
        groupId: String,
        member: Member?,
        group: Group?,
        calculation: com.sanibonani.save.data.utils.PaymentCalculation?,
        timestampStr: String,
        method: PaymentMethod
    ) {
        val targetMember = member ?: throw Exception("Member context required for contribution")
        val targetGroup = group ?: throw Exception("Group context required for contribution")
        val calc = calculation ?: throw Exception("Calculation context required for contribution")

        require(targetMember.groupId == groupId) { "Member does not belong to this group" }
        
        val memberId = targetMember.id ?: throw IllegalStateException("Member ID missing")
        val memberMonthlyContribution = PaymentCalculator.calculateMonthlyContribution(targetGroup, targetMember)
        val amountMoney = amount.toMoneyBigDecimal()
        val monthlyMemberFee = PlatformFees.MONTHLY_MEMBER_FEE.coerceAtLeast(0.0).toMoneyBigDecimal()
        val feeComponentMoney = if (amountMoney >= monthlyMemberFee) monthlyMemberFee else BigDecimal.ZERO
        val contributionComponentMoney = amountMoney.subtract(feeComponentMoney)
        val contributionDueExcludingFeeMoney = memberMonthlyContribution.toMoneyBigDecimal().subtract(monthlyMemberFee).max(BigDecimal.ZERO)

        recordPayment(txId, amount, groupId, PaymentType.CONTRIBUTION, timestampStr, memberId, method)
            .onFailure { throw it }
            .getOrThrow()
        
        memberRepository.recordContribution(
            Contribution(
                memberId = memberId,
                groupId = groupId,
                amount = contributionComponentMoney.toDouble(),
                type = "contribution",
                status = if (contributionComponentMoney >= contributionDueExcludingFeeMoney) ContributionStatus.PAID else ContributionStatus.PARTIAL,
                dueDate = calc.nextDueDate,
                paidAt = timestampStr,
                transactionId = txId
            )
        ).onFailure { throw it }.getOrThrow()

        if (feeComponentMoney > BigDecimal.ZERO) {
            memberRepository.recordContribution(
                Contribution(
                    memberId = memberId,
                    groupId = groupId,
                    amount = feeComponentMoney.toDouble(),
                    type = "member_fee",
                    status = ContributionStatus.PAID,
                    dueDate = calc.nextDueDate,
                    paidAt = timestampStr,
                    transactionId = "${txId}_member_fee"
                )
            ).onFailure { throw it }.getOrThrow()
        }

        // Side effects (Audit/Notification) are now handled by DomainEventDispatcher via recordPayment()
    }

    private suspend fun recordPayment(
        txId: String,
        amount: Double,
        groupId: String,
        type: PaymentType,
        timestamp: String,
        memberId: String? = null,
        method: PaymentMethod
    ): Result<String> {
        val payment = Payment(
            memberId = memberId ?: "",
            groupId = groupId,
            amount = amount,
            paymentType = type,
            paymentMethod = method,
            transactionId = txId,
            status = PaymentStatus.COMPLETED,
            processedAt = timestamp
        )
        return paymentRepository.recordPayment(payment)
    }

    suspend operator fun invoke(
        type: PaymentType,
        amount: Double,
        groupId: String,
        member: Member? = null,
        group: Group? = null,
        calculation: com.sanibonani.save.data.utils.PaymentCalculation? = null,
        method: PaymentMethod = PaymentMethod.BANK
    ): Result<String> = runCatching {
        // Generate a deterministic but provider-neutral transaction ID for internal tracking
        val txId = "tx_${OperationKeys.stableSuffix("payment_tx", type.name, groupId, member?.id, amount)}"
        confirm(txId, method, type, amount, groupId, member, group, calculation).getOrThrow()
        txId
    }
}
