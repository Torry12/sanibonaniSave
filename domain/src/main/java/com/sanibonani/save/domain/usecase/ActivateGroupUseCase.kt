package com.sanibonani.save.domain.usecase

import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.*
import com.sanibonani.save.domain.utils.Money
import com.sanibonani.save.domain.utils.PlatformFees
import java.time.Instant
import javax.inject.Inject

/**
 * Orchestrates the activation of a newly created group.
 * Handles platform fee payment, admin member initialization, and financial seeding.
 */
class ActivateGroupUseCase @Inject constructor(
    private val groupRepository: GroupRepository,
    private val platformRepository: PlatformRepository,
    private val memberRepository: MemberRepository,
    private val paymentRepository: PaymentRepository
) {
    suspend operator fun invoke(groupId: String, txId: String? = null): Result<Unit> = runCatching {
        // 1. Fetch group to ensure it exists and get type
        val group = groupRepository.getGroupById(groupId).getOrThrow()

        // 2. Mark group as active and registration paid
        groupRepository.updateGroupSettings(groupId, mapOf(
            "registration_paid" to true,
            "fee_status" to "paid",
            "is_platform_suspended" to false
        )).getOrThrow()

        // 3. Process Platform Registration Fee
        val registrationFee = platformRepository.getFeeConfigForGroup(
            group.type,
            if (group.type == GroupType.ROSCA) group.periodMonths else 0
        ).map { it.registrationFee }.getOrDefault(PlatformFees.REGISTRATION)

        groupRepository.payPlatformFee(groupId, registrationFee, "registration", txId).getOrThrow()

        // 4. Finalize Admin Member
        group.adminUserId?.let { adminId ->
            val members = memberRepository.getMembers(groupId).getOrThrow()
            val adminMember = members.find { it.userId == adminId } ?: return@let

            val nowStr = Instant.now().toString()
            val effectiveTxId = txId ?: "auto_activate_${groupId}_${System.currentTimeMillis()}"

            // 4a. Credit first monthly contribution
            if (group.monthlyContribution.isPositive) {
                paymentRepository.recordPayment(
                    Payment(
                        memberId = adminMember.id ?: "",
                        groupId = groupId,
                        amount = group.monthlyContribution,
                        paymentType = PaymentType.CONTRIBUTION,
                        paymentMethod = PaymentMethod.BANK,
                        transactionId = "first_contrib_$effectiveTxId",
                        status = PaymentStatus.COMPLETED,
                        processedAt = nowStr
                    )
                ).getOrThrow()
            }

            // 4b. Record platform registration fee contribution (audit)
            paymentRepository.recordPayment(
                Payment(
                    memberId = adminMember.id ?: "",
                    groupId = groupId,
                    amount = registrationFee,
                    paymentType = PaymentType.PLATFORM_FEE,
                    paymentMethod = PaymentMethod.BANK,
                    transactionId = "reg_fee_$effectiveTxId",
                    status = PaymentStatus.COMPLETED,
                    processedAt = nowStr
                )
            ).getOrThrow()

            // 4c. Credit joining fee if applicable
            if (group.joiningFee.isPositive) {
                paymentRepository.recordPayment(
                    Payment(
                        memberId = adminMember.id ?: "",
                        groupId = groupId,
                        amount = group.joiningFee,
                        paymentType = PaymentType.JOINING_FEE,
                        paymentMethod = PaymentMethod.BANK,
                        transactionId = "joining_fee_$effectiveTxId",
                        status = PaymentStatus.COMPLETED,
                        processedAt = nowStr
                    )
                ).getOrThrow()
            }

            // 4d. Update admin status to ACTIVE/PROBATION
            val nextStatus = if (group.probationMonths > 0) MemberStatus.PROBATION else MemberStatus.ACTIVE
            memberRepository.updateMemberStatus(adminMember.id ?: "", nextStatus).getOrThrow()
        }
    }
}
