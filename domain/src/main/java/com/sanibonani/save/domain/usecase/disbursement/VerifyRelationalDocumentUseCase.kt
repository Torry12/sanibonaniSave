package com.sanibonani.save.domain.usecase.disbursement

import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.MemberDocumentRepository
import com.sanibonani.save.domain.usecase.dynamics.UpdateMemberStatusUseCase
import javax.inject.Inject

class VerifyRelationalDocumentUseCase @Inject constructor(
    private val memberDocumentRepo: MemberDocumentRepository,
    private val updateMemberStatusUseCase: UpdateMemberStatusUseCase
) {
    suspend operator fun invoke(document: MemberDocument, approve: Boolean): Result<Unit> {
        val status = if (approve) DocumentStatus.VERIFIED else DocumentStatus.REJECTED
        return memberDocumentRepo.updateMemberDocument(document.copy(status = status)).map { }.onSuccess {
            if (!approve) updateMemberStatusUseCase(document.memberId, MemberStatus.SUSPENDED)
        }
    }
}
