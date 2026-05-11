package com.sanibonani.save.domain.usecase

import com.sanibonani.save.domain.model.DocumentStatus
import com.sanibonani.save.domain.model.MemberStatus
import com.sanibonani.save.domain.repository.MemberRepository
import javax.inject.Inject

/**
 * Orchestrates document verification/rejection.
 * Rejection automatically triggers member suspension.
 */
class VerifyMemberDocumentUseCase @Inject constructor(
    private val memberRepo: MemberRepository,
    private val updateMemberStatusUseCase: UpdateMemberStatusUseCase
) {
    suspend operator fun invoke(memberId: String, docIndex: Int, approve: Boolean): Result<Unit> {
        val status = if (approve) DocumentStatus.VERIFIED else DocumentStatus.REJECTED
        
        return memberRepo.updateMemberDocumentStatus(memberId, docIndex, status)
            .onSuccess {
                if (!approve) {
                    // Automatically suspend member if a required document is rejected
                    updateMemberStatusUseCase(memberId, MemberStatus.SUSPENDED)
                }
            }
    }
}
