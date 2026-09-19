package com.sanibonani.save.domain.repository

interface GroupDocumentRepository {
    suspend fun uploadConstitution(groupId: String, fileBytes: ByteArray, fileName: String): Result<String>
}
