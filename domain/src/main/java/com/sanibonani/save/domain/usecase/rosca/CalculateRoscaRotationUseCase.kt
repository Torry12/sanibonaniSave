package com.sanibonani.save.domain.usecase.rosca

import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.GroupType
import com.sanibonani.save.domain.model.Member
import java.time.LocalDate
import javax.inject.Inject

/**
 * ROSCA (Rotating Savings and Credit Association) specific logic.
 * Calculates the rotation schedule and payout pots.
 */
class CalculateRoscaRotationUseCase @Inject constructor() {

    data class RoscaSchedule(
        val groupId: String,
        val totalPot: Double,
        val cycleMonths: Int,
        val items: List<RoscaRotationItem>
    )

    data class RoscaRotationItem(
        val memberId: String,
        val memberName: String,
        val payoutDate: String,
        val isCurrent: Boolean,
        val isCompleted: Boolean
    )

    operator fun invoke(group: Group, members: List<Member>): Result<RoscaSchedule> {
        if (group.type != GroupType.ROSCA) {
            return Result.failure(IllegalArgumentException("Group is not a ROSCA group"))
        }

        if (members.isEmpty()) {
            return Result.failure(IllegalArgumentException("Group has no members"))
        }

        val totalPot = group.monthlyContribution * members.size
        val cycleMonths = members.size
        
        // Sort members by joined date or some assigned index
        // For simplicity, we'll use the ID or joinedAt
        val sortedMembers = members.sortedBy { it.joinedAt ?: it.id }

        // Start from group creation or current month
        val startDate = try {
            LocalDate.parse(group.createdAt?.substringBefore("T"))
        } catch (_: Exception) {
            LocalDate.now().withDayOfMonth(group.paymentDueDay)
        }

        val now = LocalDate.now()

        val items = sortedMembers.mapIndexed { index, member ->
            val payoutDate = startDate.plusMonths(index.toLong())
            val isCurrent = payoutDate.month == now.month && payoutDate.year == now.year
            val isCompleted = payoutDate.isBefore(now) && !isCurrent

            RoscaRotationItem(
                memberId = member.id ?: "",
                memberName = member.fullName,
                payoutDate = payoutDate.toString(),
                isCurrent = isCurrent,
                isCompleted = isCompleted
            )
        }

        return Result.success(
            RoscaSchedule(
                groupId = group.id ?: "",
                totalPot = totalPot,
                cycleMonths = cycleMonths,
                items = items
            )
        )
    }
}
