package com.sanibonani.save.domain.usecase.treasury

import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.*
import com.sanibonani.save.domain.repository.equity.ContributionRepository
import com.sanibonani.save.domain.repository.treasury.GroupFinancialRepository
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * Orchestrates the activation of a newly created group.
 * Handles platform fee payment, admin member initialization, and financial seeding.
 */
class ActivateGroupUseCase @Inject constructor(
    private val groupRepository: GroupRepository,
    private val platformRepository: PlatformRepository,
    private val memberRepository: MemberRepository,
    private val contributionRepository: ContributionRepository
) {
    suspend operator fun invoke(groupId: String, txId: String? = null): Result<Unit> = runCatching {
        // 1. Fetch group
        val group = groupRepository.getGroupById(groupId).getOrThrow()

        // 2. Onboard Admin as Member #1
        group.adminUserId?.let { adminUserId ->
            val members = memberRepository.syncGroupMembers(groupId).getOrThrow()
            val adminMember = members.find { it.userId == adminUserId } ?: return@let
            val adminMemberId = adminMember.id ?: return@let

            val regFee = platformRepository.getFeeConfigForGroup(
                group.type,
                if (group.type == GroupType.ROSCA) group.periodMonths else 0
            ).map { it.registrationFee }.getOrDefault(PlatformFees.REGISTRATION)

            val effectiveTxId = txId ?: "activate_${groupId}_${System.currentTimeMillis()}"

            // 3. Build atomic onboarding entries
            val allocations = buildJsonArray {
                // Leg 1: Registration Credit (Member Equity)
                addJsonObject {
                    put("type", "registration_contribution")
                    put("amount", regFee.amount)
                    put("due_date", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE))
                    put("description", "Admin Registration Credit")
                }
                // Leg 2: Joining Fee Waiver
                addJsonObject {
                    put("type", "joining_fee")
                    put("amount", 0.0)
                    put("due_date", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE))
                    put("description", "Admin Joining Fee Waiver (Registration Applied)")
                }
                // Leg 3: Month 1 Coverage
                addJsonObject {
                    put("type", "contribution")
                    put("amount", 0.0)
                    put("due_date", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE))
                    put("description", "Admin Month 1 Coverage (Registration Applied)")
                }
            }

            // Execute ATOMIC activation payment
            contributionRepository.processComplexPayment(
                groupId = groupId,
                memberId = adminMemberId,
                totalAmount = regFee,
                txId = effectiveTxId,
                allocations = allocations,
                method = PaymentMethod.BANK,
                paymentType = PaymentType.REGISTRATION
            ).getOrThrow()

            // Finalize statuses
            groupRepository.updateFeeStatus(groupId, AdminFeeState.PAID).getOrThrow()
            groupRepository.updateGroupSettings(groupId, mapOf("registration_paid" to true)).getOrThrow()

            val nextStatus = if (group.probationMonths > 0) MemberStatus.PROBATION else MemberStatus.ACTIVE
            memberRepository.updateMemberStatus(adminMemberId, nextStatus).getOrThrow()
        }
    }
}
