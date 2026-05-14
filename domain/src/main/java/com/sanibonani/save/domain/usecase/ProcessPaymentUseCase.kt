package com.sanibonani.save.domain.usecase

import com.sanibonani.save.data.utils.PaymentCalculator
import com.sanibonani.save.domain.model.AdminFeeState
import com.sanibonani.save.domain.model.AppNotification
import com.sanibonani.save.domain.model.Contribution
import com.sanibonani.save.domain.model.ContributionStatus
import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.Member
import com.sanibonani.save.domain.model.MemberStatus
import com.sanibonani.save.domain.model.NotifChannel
import com.sanibonani.save.domain.model.NotifEvent
import com.sanibonani.save.domain.model.Payment
import com.sanibonani.save.domain.model.PaymentStatus
import com.sanibonani.save.domain.model.PaymentType
import com.sanibonani.save.domain.model.PlatformFees
import com.sanibonani.save.domain.repository.GroupRepository
import com.sanibonani.save.domain.repository.MemberRepository
import com.sanibonani.save.domain.repository.NotificationRepository
import com.sanibonani.save.domain.repository.PaymentRepository
import com.sanibonani.save.domain.repository.PlatformRepository
import com.sanibonani.save.domain.utils.isPositiveMoneyAmount
import com.sanibonani.save.domain.utils.toMoneyBigDecimal
import com.sanibonani.save.domain.utils.OperationKeys
import com.sanibonani.save.domain.utils.formatZAR
import javax.inject.Inject
import java.math.BigDecimal

/**
 * Orchestrates payment processing across different payment types.
 * Handles side effects like group activation, member registration, and notifications.
 * 
 * Follows MVVM + Clean Architecture principles:
 * - Returns Result<T> for all operations (no throwing exceptions)
 * - Validates inputs and returns user-friendly error messages
 * - Delegates to repositories via dependency injection
 * - Single Responsibility: payment orchestration only
 */
class ProcessPaymentUseCase @Inject constructor(
    private val paymentRepository: PaymentRepository,
    private val groupRepository: GroupRepository,
    private val memberRepository: MemberRepository,
    private val notificationRepository: NotificationRepository,
    private val platformRepository: PlatformRepository
) {
    companion object {
        /**
         * Sentinel group ID used during ad-hoc registration flow
         * before a real group ID has been assigned.
         */
        const val NEW_GROUP_SENTINEL = "new_group"
    }
    suspend operator fun invoke(
        type: PaymentType,
        amount: Double,
        groupId: String,
        member: Member? = null,
        group: Group? = null,
        calculation: com.sanibonani.save.data.utils.PaymentCalculation? = null
    ): Result<String> = runCatching {
        require(amount.isPositiveMoneyAmount()) { "Payment amount must be greater than zero." }

        // Generate deterministic transaction ID
        val dueKey = when (type) {
            PaymentType.CONTRIBUTION -> calculation?.nextDueDate.orEmpty()
            PaymentType.JOINING_FEE -> member?.id.orEmpty()
            PaymentType.PLATFORM_FEE -> if (group?.registrationPaid == false) "registration_activation" else "platform_fee"
            else -> "generic"
        }
        val txId = "yoco_tx_${OperationKeys.stableSuffix(
            namespace = "payment_tx",
            type.name,
            groupId,
            member?.id,
            amount,
            dueKey
        )}"
        val timestampStr = kotlinx.datetime.Clock.System.now().toString()

        when (type) {
            PaymentType.PLATFORM_FEE -> processPlatformFeePayment(txId, amount, groupId, group, member, timestampStr)
            PaymentType.JOINING_FEE -> processJoiningFeePayment(txId, amount, groupId, member, timestampStr)
            PaymentType.CONTRIBUTION -> processContributionPayment(txId, amount, groupId, member, group, calculation, timestampStr)
            else -> throw IllegalArgumentException("Payment type '${type.name}' is not supported in this flow.")
        }
        
        txId
    }

    private suspend fun processPlatformFeePayment(
        txId: String,
        amount: Double,
        groupId: String,
        group: Group?,
        member: Member?,
        timestampStr: String
    ) {
        if (groupId == NEW_GROUP_SENTINEL) {
            // Handled by registration flow in GroupViewModel
            return
        }
        
        // Monthly Platform Fee or Registration Finalization
        if (group?.registrationPaid == false) {
            groupRepository.activateGroup(groupId, txId)
                .onFailure { throw it }
                .getOrThrow()
        } else {
            groupRepository.updateFeeStatus(groupId, AdminFeeState.PAID)
                .onFailure { throw it }
                .getOrThrow()
            platformRepository.unsuspendGroup(groupId)
                .onFailure { throw it }
                .getOrThrow()
        }
        recordPayment(txId, amount, groupId, PaymentType.PLATFORM_FEE, timestampStr, member?.id)
            .onFailure { throw it }
            .getOrThrow()
    }

    private suspend fun processJoiningFeePayment(
        txId: String,
        amount: Double,
        groupId: String,
        member: Member?,
        timestampStr: String
    ) {
        val targetMember = member ?: throw Exception("Member context required for joining fee")
        
        recordPayment(txId, amount, groupId, PaymentType.JOINING_FEE, timestampStr, targetMember.id)
            .onFailure { throw it }
            .getOrThrow()
        
        val activatedMember = memberRepository.registerMember(targetMember, txId)
            .onFailure { throw it }
            .getOrThrow()
            
        val welcomeMsg = if (activatedMember.status == MemberStatus.PROBATION) {
            "Joining fee of ${formatZAR(amount)} received! You are now on probation until ${activatedMember.probationEndAt ?: "the end of your period"}."
        } else {
            "Joining fee of ${formatZAR(amount)} received! Welcome as an active member."
        }

        notificationRepository.sendNotification(
            AppNotification(
                id = OperationKeys.stableUuid("payment_notification", txId, NotifEvent.PAYMENT_CONFIRMED.name),
                groupId = groupId,
                memberId = activatedMember.id,
                message = welcomeMsg,
                triggerEvent = NotifEvent.PAYMENT_CONFIRMED,
                channel = NotifChannel.BOTH
            )
        ).onFailure { throw it }
            .getOrThrow()
    }

    private suspend fun processContributionPayment(
        txId: String,
        amount: Double,
        groupId: String,
        member: Member?,
        group: Group?,
        calculation: com.sanibonani.save.data.utils.PaymentCalculation?,
        timestampStr: String
    ) {
        val targetMember = member ?: throw Exception("Member context required for contribution")
        val targetGroup = group ?: throw Exception("Group context required for contribution")
        val calc = calculation ?: throw Exception("Calculation context required for contribution")
        
        val memberId = targetMember.id ?: throw IllegalStateException("Member ID missing")
        val memberMonthlyContribution = PaymentCalculator.calculateMonthlyContribution(targetGroup, targetMember)
        val amountMoney = amount.toMoneyBigDecimal()
        val monthlyMemberFee = PlatformFees.MONTHLY_MEMBER_FEE.coerceAtLeast(0.0).toMoneyBigDecimal()
        val feeComponentMoney = if (amountMoney >= monthlyMemberFee) monthlyMemberFee else BigDecimal.ZERO
        val contributionComponentMoney = amountMoney.subtract(feeComponentMoney)
        val contributionDueExcludingFeeMoney = memberMonthlyContribution.toMoneyBigDecimal().subtract(monthlyMemberFee).max(BigDecimal.ZERO)

        recordPayment(txId, amount, groupId, PaymentType.CONTRIBUTION, timestampStr, memberId)
            .onFailure { throw it }
            .getOrThrow()
        
        // Record main contribution
        memberRepository.recordContribution(
            Contribution(
                memberId = memberId,
                groupId = groupId,
                amount = contributionComponentMoney.toDouble(),
                type = "contribution",
                status = if (contributionComponentMoney >= contributionDueExcludingFeeMoney) ContributionStatus.PAID else ContributionStatus.PARTIAL,
                dueDate = calc.nextDueDate,
                paidAt = timestampStr,
                yocoTransactionId = txId
            )
        ).onFailure { throw it }
            .getOrThrow()

        // Record member fee ledger if applicable
        if (feeComponentMoney > BigDecimal.ZERO) {
            memberRepository.recordContribution(
                Contribution(
                    memberId = memberId,
                    groupId = groupId,
                    amount = feeComponentMoney.toDouble(),
                    type = "member_fee_ledger",
                    status = ContributionStatus.PAID,
                    dueDate = calc.nextDueDate,
                    paidAt = timestampStr,
                    yocoTransactionId = "${txId}_member_fee"
                )
            ).onFailure { throw it }
                .getOrThrow()
        }

        // Send confirmation notification
        notificationRepository.sendNotification(
            AppNotification(
                id = OperationKeys.stableUuid("payment_notification", txId, NotifEvent.PAYMENT_CONFIRMED.name),
                groupId = groupId,
                memberId = targetMember.id,
                message = if (amount >= memberMonthlyContribution - 0.01) {
                    "Contribution of ${formatZAR(amount)} received. Thank you!"
                } else {
                    "Partial contribution of ${formatZAR(amount)} received. Thank you!"
                },
                triggerEvent = NotifEvent.PAYMENT_CONFIRMED,
                channel = NotifChannel.BOTH
            )
        ).onFailure { throw it }
            .getOrThrow()
    }

    private suspend fun recordPayment(
        txId: String,
        amount: Double,
        groupId: String,
        type: PaymentType,
        timestamp: String,
        memberId: String? = null
    ): Result<String> {
        val payment = Payment(
            memberId = memberId ?: "",
            groupId = groupId,
            amount = amount,
            paymentType = type,
            transactionId = txId,
            status = PaymentStatus.COMPLETED,
            processedAt = timestamp
        )
        return paymentRepository.recordPayment(payment)
    }
}
