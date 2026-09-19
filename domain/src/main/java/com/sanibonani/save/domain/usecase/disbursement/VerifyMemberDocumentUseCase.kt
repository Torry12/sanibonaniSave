package com.sanibonani.save.domain.usecase.disbursement

import com.sanibonani.save.domain.model.DocumentStatus
import com.sanibonani.save.domain.model.MemberStatus
import com.sanibonani.save.domain.repository.MemberRepository
import com.sanibonani.save.domain.usecase.dynamics.UpdateMemberStatusUseCase
import javax.inject.Inject

class VerifyMemberDocumentUseCase @Inject constructor(
    private val memberRepo: MemberRepository,
    private val updateMemberStatusUseCase: UpdateMemberStatusUseCase
) {
    suspend operator fun invoke(memberId: String, docIndex: Int, approve: Boolean): Result<Unit> {
        val status = if (approve) DocumentStatus.VERIFIED else DocumentStatus.REJECTED
        return memberRepo.updateMemberDocumentStatus(memberId, docIndex, status).onSuccess {
            if (!approve) updateMemberStatusUseCase(memberId, MemberStatus.SUSPENDED)
        }
    }
}
