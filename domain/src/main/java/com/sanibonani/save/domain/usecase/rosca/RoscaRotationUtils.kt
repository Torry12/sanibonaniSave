package com.sanibonani.save.domain.usecase.rosca

import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.Member
import com.sanibonani.save.domain.model.RoscaRotationMethod

/**
 * Shared utility for determining the ordered payout queue of ROSCA participants.
 *
 * Single source of truth used by both [CalculateRoscaRotationUseCase] (schedule view)
 * and [com.sanibonani.save.domain.usecase.actuarial.GroupTypeActuarialEngine] (risk metrics).
 *
 * Rotation logic:
 *  - [RoscaRotationMethod.RANDOM_DRAW]: deterministic shuffle seeded from group id/name hash
 *    so the same order is reproduced every time for the same group.
 *  - All other methods ([FIXED], [NEED_BASED], [AUCTION]): join-date ascending order.
 */
fun sortRoscaParticipants(group: Group, participants: List<Member>): List<Member> {
    val baseOrder = participants.sortedBy { it.joinedAt ?: it.id }
    return when (group.rotationMethod) {
        RoscaRotationMethod.RANDOM_DRAW ->
            baseOrder.shuffled(kotlin.random.Random((group.id ?: group.name).hashCode()))
        RoscaRotationMethod.FIXED,
        RoscaRotationMethod.NEED_BASED,
        RoscaRotationMethod.AUCTION -> baseOrder
    }
}

