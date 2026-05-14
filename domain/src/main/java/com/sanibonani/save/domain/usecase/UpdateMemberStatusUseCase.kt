package com.sanibonani.save.domain.usecase

import com.sanibonani.save.domain.model.MemberStatus
import com.sanibonani.save.domain.repository.MemberRepository
import javax.inject.Inject

/**
 * Encapsulates the logic for updating a member's status.
 *
 * Enforces allowed status transitions:
 *  - PENDING_PAYMENT → PROBATION | ACTIVE | SUSPENDED
 *  - PROBATION       → ACTIVE | SUSPENDED | PENDING_PAYMENT
 *  - ACTIVE          → SUSPENDED | PENDING_PAYMENT
 *  - SUSPENDED       → ACTIVE | PENDING_PAYMENT
 */
class UpdateMemberStatusUseCase @Inject constructor(
    private val memberRepository: MemberRepository
) {
    suspend operator fun invoke(memberId: String, newStatus: MemberStatus): Result<Unit> =
        runCatching {
            memberRepository.updateMemberStatus(memberId, newStatus).getOrThrow()
        }

    /**
     * Validates whether transitioning from [current] to [next] is a legally allowed change.
     * Returns a user-facing error message when the transition is forbidden, or `null` when allowed.
     */
    fun validateTransition(current: MemberStatus, next: MemberStatus): String? {
        val allowed = ALLOWED_TRANSITIONS[current] ?: emptySet()
        return if (next in allowed) null
        else "Status change from ${current.displayName} to ${next.displayName} is not permitted."
    }

    companion object {
        val ALLOWED_TRANSITIONS: Map<MemberStatus, Set<MemberStatus>> = mapOf(
            MemberStatus.PENDING_PAYMENT to setOf(MemberStatus.PROBATION, MemberStatus.ACTIVE, MemberStatus.SUSPENDED),
            MemberStatus.PROBATION       to setOf(MemberStatus.ACTIVE, MemberStatus.SUSPENDED, MemberStatus.PENDING_PAYMENT),
            MemberStatus.ACTIVE          to setOf(MemberStatus.SUSPENDED, MemberStatus.PENDING_PAYMENT),
            MemberStatus.SUSPENDED       to setOf(MemberStatus.ACTIVE, MemberStatus.PENDING_PAYMENT)
        )
    }
}
