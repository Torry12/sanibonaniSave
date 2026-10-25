package com.sanibonani.save.domain.repository.group

interface GroupDocumentRepository {
    suspend fun uploadConstitution(groupId: String, fileBytes: ByteArray, fileName: String): Result<String>
}
