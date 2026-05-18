package com.sanibonani.save.domain.usecase.rosca

import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.Member
import com.sanibonani.save.domain.model.RoscaRotationMethod
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Shared utility for determining the ordered payout queue of ROSCA participants.
 *
 * Single source of truth used by both [CalculateRoscaRotationUseCase] (schedule view)
 * and [com.sanibonani.save.domain.usecase.actuarial.GroupTypeActuarialEngine] (risk metrics).
 *
 * Rotation logic:
 *  - [RoscaRotationMethod.RANDOM_DRAW]: Deterministic shuffle seeded from (group id + cycle number)
 *    so the order remains stable for the duration of a cycle but can rotate between cycles.
 *  - All other methods ([FIXED], [NEED_BASED], [AUCTION]): join-date ascending order.
 */
fun sortRoscaParticipants(group: Group, participants: List<Member>): List<Member> {
    return when (group.rotationMethod) {
        RoscaRotationMethod.RANDOM_DRAW -> {
            // Seed based on group ID and the current cycle number to maintain stability within a cycle
            val startDate = try {
                LocalDate.parse(group.createdAt?.substringBefore("T"))
            } catch (_: Exception) {
                LocalDate.now().withDayOfMonth(group.paymentDueDay)
            }
            val now = LocalDate.now().withDayOfMonth(1)
            val cycleMonths = participants.size.coerceAtLeast(1)
            val elapsedMonths = ChronoUnit.MONTHS.between(startDate.withDayOfMonth(1), now).coerceAtLeast(0)
            val cycleNumber = elapsedMonths / cycleMonths

            val seed = (group.id.hashCode().toLong() + cycleNumber)
            participants.shuffled(java.util.Random(seed))
        }
        else -> {
            // FIXED, NEED_BASED and AUCTION currently fall back to join order
            participants.sortedBy { it.joinedAt ?: it.id ?: "" }
        }
    }
}
