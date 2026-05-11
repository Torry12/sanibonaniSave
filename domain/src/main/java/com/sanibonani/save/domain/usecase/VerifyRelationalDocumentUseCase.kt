package com.sanibonani.save.domain.usecase

import com.sanibonani.save.domain.model.DocumentStatus
import com.sanibonani.save.domain.model.MemberDocument
import com.sanibonani.save.domain.model.MemberStatus
import com.sanibonani.save.domain.repository.MemberDocumentRepository
import javax.inject.Inject

/**
 * Orchestrates verification of relational documents (e.g., certificates).
 * Handles document status update and member suspension on rejection.
 */
class VerifyRelationalDocumentUseCase @Inject constructor(
    private val memberDocumentRepo: MemberDocumentRepository,
    private val updateMemberStatusUseCase: UpdateMemberStatusUseCase
) {
    suspend operator fun invoke(document: MemberDocument, approve: Boolean): Result<Unit> {
        val status = if (approve) DocumentStatus.VERIFIED else DocumentStatus.REJECTED
        val updatedDoc = document.copy(status = status)
        
        return memberDocumentRepo.updateMemberDocument(updatedDoc)
            .map { } // Convert Result<MemberDocument> to Result<Unit>
            .onSuccess {
                if (!approve) {
                    updateMemberStatusUseCase(document.memberId, MemberStatus.SUSPENDED)
                }
            }
    }
}
