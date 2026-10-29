package com.sanibonani.save.domain.usecase.member

import com.sanibonani.save.domain.repository.member.MemberRepository
import javax.inject.Inject

/**
 * Stores one of a member's numbered identity slots and returns the uploaded file's URL.
 */
class UploadMemberDocumentUseCase @Inject constructor(
    private val memberRepository: MemberRepository
) {
    suspend operator fun invoke(
        memberId: String,
        documentIndex: Int,
        byteArray: ByteArray,
        fileName: String,
        documentType: String? = null
    ): Result<String> = memberRepository.uploadMemberDocument(
        memberId = memberId,
        documentIndex = documentIndex,
        byteArray = byteArray,
        fileName = fileName,
        documentType = documentType
    )
}
