package com.sanibonani.save.domain.usecase.member

import com.sanibonani.save.domain.model.MemberDocument
import com.sanibonani.save.domain.repository.member.MemberDocumentRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * The document rows of one member, re-emitted whenever the cache or the server changes them.
 */
class ObserveMemberDocumentsUseCase @Inject constructor(
    private val memberDocumentRepository: MemberDocumentRepository
) {
    operator fun invoke(memberId: String): Flow<Result<List<MemberDocument>>> =
        memberDocumentRepository.observeMemberDocuments(memberId)
}
