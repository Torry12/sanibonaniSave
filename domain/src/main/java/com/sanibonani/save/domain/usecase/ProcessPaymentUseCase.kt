package com.sanibonani.save.domain.usecase

import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.*
import com.sanibonani.save.data.utils.PaymentCalculator
import com.sanibonani.save.domain.utils.OperationKeys
import com.sanibonani.save.domain.utils.formatZAR
import javax.inject.Inject
import kotlin.math.max

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
    suspend operator fun invoke(
        type: PaymentType,
        amount: Double,
        groupId: String,
        member: Member? = null,
        group: Group? = null,
        calculation: com.sanibonani.save.data.utils.PaymentCalculation? = null
    ): Result<String> = runCatching {
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
            else -> throw Exception("Unsupported payment type: $type")
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
    ): Unit {
        if (groupId == "new_group") {
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
    ): Unit {
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
    ): Unit {
        val targetMember = member ?: throw Exception("Member context required for contribution")
        val targetGroup = group ?: throw Exception("Group context required for contribution")
        val calc = calculation ?: throw Exception("Calculation context required for contribution")
        
        val memberId = targetMember.id ?: throw IllegalStateException("Member ID missing")
        val memberMonthlyContribution = PaymentCalculator.calculateMonthlyContribution(targetGroup, targetMember)
        val monthlyMemberFee = PlatformFees.MONTHLY_MEMBER_FEE.coerceAtLeast(0.0)
        val feeComponent = if (amount + 0.01 >= monthlyMemberFee) monthlyMemberFee else 0.0
        val contributionComponent = (amount - feeComponent).coerceAtLeast(0.0)
        val contributionDueExcludingFee = max(0.0, memberMonthlyContribution - monthlyMemberFee)

        recordPayment(txId, amount, groupId, PaymentType.CONTRIBUTION, timestampStr, memberId)
            .onFailure { throw it }
            .getOrThrow()
        
        // Record main contribution
        memberRepository.recordContribution(
            Contribution(
                memberId = memberId,
                groupId = groupId,
                amount = contributionComponent,
                type = "contribution",
                status = if (contributionComponent >= contributionDueExcludingFee - 0.01) ContributionStatus.PAID else ContributionStatus.PARTIAL,
                dueDate = calc.nextDueDate,
                paidAt = timestampStr,
                yocoTransactionId = txId
            )
        ).onFailure { throw it }
            .getOrThrow()

        // Record member fee ledger if applicable
        if (feeComponent > 0.0) {
            memberRepository.recordContribution(
                Contribution(
                    memberId = memberId,
                    groupId = groupId,
                    amount = feeComponent,
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
