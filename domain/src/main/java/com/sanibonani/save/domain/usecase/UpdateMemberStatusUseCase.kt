package com.sanibonani.save.domain.usecase

import com.sanibonani.save.domain.model.MemberStatus
import com.sanibonani.save.domain.repository.MemberRepository
import javax.inject.Inject

/**
 * Encapsulates the logic for updating a member's status.
 * Handles transitions like Pending -> Probation -> Active.
 */
class UpdateMemberStatusUseCase @Inject constructor(
    private val memberRepository: MemberRepository
) {
    suspend operator fun invoke(memberId: String, newStatus: MemberStatus): Result<Unit> {
        return try {
            // Here we could add validation logic for status transitions if needed
            memberRepository.updateMemberStatus(memberId, newStatus)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
