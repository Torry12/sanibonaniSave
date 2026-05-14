package com.sanibonani.save.domain.usecase.rosca

import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.GroupType
import com.sanibonani.save.domain.model.Member
import com.sanibonani.save.domain.model.MemberStatus
import java.time.LocalDate
import java.time.temporal.ChronoUnit
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

        val participatingMembers = members.filter { it.status != MemberStatus.SUSPENDED }
        if (participatingMembers.isEmpty()) {
            return Result.failure(IllegalArgumentException("ROSCA has no participating members"))
        }

        val cycleMonths = participatingMembers.size
        val totalPot = group.monthlyContribution * cycleMonths

        val sortedMembers = sortRoscaParticipants(group, participatingMembers)

        // Start from group creation or current month
        val startDate = try {
            LocalDate.parse(group.createdAt?.substringBefore("T"))
        } catch (_: Exception) {
            LocalDate.now().withDayOfMonth(group.paymentDueDay)
        }

        val now = LocalDate.now().withDayOfMonth(1)
        val scheduleStart = startDate.withDayOfMonth(1)
        val elapsedMonths = ChronoUnit.MONTHS.between(scheduleStart, now).coerceAtLeast(0)
        val currentIndex = (elapsedMonths % cycleMonths).toInt()
        val cycleStartOffset = elapsedMonths - currentIndex
        val currentCycleStart = scheduleStart.plusMonths(cycleStartOffset)

        val items = sortedMembers.mapIndexed { index, member ->
            val payoutDate = currentCycleStart.plusMonths(index.toLong())
            val isCurrent = index == currentIndex
            val isCompleted = index < currentIndex

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
