package com.sanibonani.save.domain.usecase.group

import com.sanibonani.save.domain.repository.group.GroupDocumentRepository
import javax.inject.Inject

/**
 * Stores a group's constitution file and returns its storage URL.
 */
class UploadGroupConstitutionUseCase @Inject constructor(
    private val groupDocumentRepository: GroupDocumentRepository
) {
    suspend operator fun invoke(groupId: String, fileBytes: ByteArray, fileName: String): Result<String> =
        groupDocumentRepository.uploadConstitution(groupId, fileBytes, fileName)
}
