package com.sanibonani.save.domain.usecase

import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.*
import com.sanibonani.save.data.utils.PaymentCalculator
import com.sanibonani.save.domain.utils.formatZAR
import kotlinx.datetime.Clock
import javax.inject.Inject

/**
 * Orchestrates payment processing across different payment types.
 * Handles side effects like group activation, member registration, and notifications.
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
        val now = Clock.System.now()
        val txId = "yoco_tx_" + now.toEpochMilliseconds()
        val timestampStr = now.toString()

        when (type) {
            PaymentType.PLATFORM_FEE -> {
                if (groupId == "new_group") {
                    // Handled by registration flow in GroupViewModel
                    return@runCatching txId
                }
                
                // Monthly Platform Fee or Registration Finalization
                if (group?.registrationPaid == false) {
                   groupRepository.activateGroup(groupId, txId).getOrThrow()
                } else {
                   groupRepository.updateFeeStatus(groupId, AdminFeeState.PAID).getOrThrow()
                   platformRepository.unsuspendGroup(groupId).getOrThrow()
                }
                recordPayment(txId, amount, groupId, type, timestampStr, member?.id)
            }

            PaymentType.JOINING_FEE -> {
                val targetMember = member ?: throw Exception("Member context required for joining fee")
                recordPayment(txId, amount, groupId, type, timestampStr, targetMember.id).getOrThrow()
                
                val activatedMember = memberRepository.registerMember(targetMember, txId).getOrThrow()
                val welcomeMsg = if (activatedMember.status == MemberStatus.PROBATION) {
                    "Joining fee of ${formatZAR(amount)} received! You are now on probation until ${activatedMember.probationEndAt ?: "the end of your period"}."
                } else {
                    "Joining fee of ${formatZAR(amount)} received! Welcome as an active member."
                }

                notificationRepository.sendNotification(
                    AppNotification(
                        groupId = groupId,
                        memberId = activatedMember.id,
                        message = welcomeMsg,
                        triggerEvent = NotifEvent.PAYMENT_CONFIRMED,
                        channel = NotifChannel.BOTH
                    )
                ).getOrThrow()
            }

            PaymentType.CONTRIBUTION -> {
                val targetMember = member ?: throw Exception("Member context required for contribution")
                val targetGroup = group ?: throw Exception("Group context required for contribution")
                val calc = calculation ?: throw Exception("Calculation context required for contribution")
                
                val memberId = targetMember.id ?: throw IllegalStateException("Member ID missing")
                val memberMonthlyContribution = PaymentCalculator.calculateMonthlyContribution(targetGroup, targetMember)
                
                recordPayment(txId, amount, groupId, type, timestampStr, memberId).getOrThrow()
                
                memberRepository.recordContribution(
                    Contribution(
                        memberId = memberId,
                        groupId = groupId,
                        amount = amount,
                        type = "contribution",
                        status = if (amount >= memberMonthlyContribution - 0.01) ContributionStatus.PAID else ContributionStatus.PARTIAL,
                        dueDate = calc.nextDueDate,
                        paidAt = timestampStr,
                        yocoTransactionId = txId
                    )
                ).getOrThrow()

                notificationRepository.sendNotification(
                    AppNotification(
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
                ).getOrThrow()
            }
            else -> throw Exception("Unsupported payment type: $type")
        }
        
        txId
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
