package com.sanibonani.save.domain.usecase.member

import com.sanibonani.save.domain.model.MemberDocument
import com.sanibonani.save.domain.repository.member.MemberDocumentRepository
import javax.inject.Inject

/**
 * Uploads a labelled supporting document and attaches it to the member in one step.
 */
class UploadRelationalDocumentUseCase @Inject constructor(
    private val memberDocumentRepository: MemberDocumentRepository
) {
    suspend operator fun invoke(
        memberId: String,
        groupId: String,
        label: String,
        byteArray: ByteArray,
        fileName: String,
        documentType: String? = null
    ): Result<MemberDocument> = memberDocumentRepository.uploadAndAddMemberDocument(
        memberId = memberId,
        groupId = groupId,
        label = label,
        byteArray = byteArray,
        fileName = fileName,
        documentType = documentType
    )
}
