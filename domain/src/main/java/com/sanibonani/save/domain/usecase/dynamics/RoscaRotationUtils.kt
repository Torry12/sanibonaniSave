package com.sanibonani.save.domain.usecase.dynamics

import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.Member
import com.sanibonani.save.domain.model.RoscaRotationMethod
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Shared utility for determining the ordered payout queue of ROSCA participants.
 */
fun sortRoscaParticipants(group: Group, participants: List<Member>): List<Member> {
    return when (group.rotationMethod) {
        RoscaRotationMethod.RANDOM_DRAW -> {
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
        RoscaRotationMethod.NEED_BASED,
        RoscaRotationMethod.AUCTION -> {
            participants.sortedWith(
                compareBy<Member> { it.roscaPosition ?: Int.MAX_VALUE }
                    .thenBy { it.joinedAt ?: it.id ?: "" }
            )
        }
        else -> {
            participants.sortedBy { it.joinedAt ?: it.id ?: "" }
        }
    }
}
