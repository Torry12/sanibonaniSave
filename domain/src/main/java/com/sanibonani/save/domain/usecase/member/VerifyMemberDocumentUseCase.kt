package com.sanibonani.save.domain.usecase.member
import com.sanibonani.save.domain.model.DocumentStatus
import com.sanibonani.save.domain.model.MemberStatus
import com.sanibonani.save.domain.repository.member.MemberRepository
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
            .mapCatching {
                if (!approve) {
                    // Automatically suspend member if a required document is rejected.
                    // Refusing to report a suspension that did not apply told the admin the rejection
                    // had taken effect, over a member who stayed active with a rejected ID on file.
                    updateMemberStatusUseCase(memberId, MemberStatus.SUSPENDED).getOrThrow()
                }
            }
    }
}
