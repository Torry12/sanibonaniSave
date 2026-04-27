package com.sanibonani.save.domain.repository

import com.sanibonani.save.domain.model.MemberDocument
import kotlinx.coroutines.flow.Flow

interface MemberDocumentRepository {
    fun observeMemberDocuments(memberId: String): Flow<Result<List<MemberDocument>>>
    suspend fun syncMemberDocuments(memberId: String): Result<List<MemberDocument>>
    suspend fun addMemberDocument(document: MemberDocument): Result<MemberDocument>
    suspend fun updateMemberDocument(document: MemberDocument): Result<MemberDocument>
    suspend fun uploadAndAddMemberDocument(
        memberId: String,
        groupId: String,
        label: String,
        byteArray: ByteArray,
        fileName: String,
        documentType: String? = null
    ): Result<MemberDocument>
}
