package com.sanibonani.save.domain.service

import com.sanibonani.save.domain.repository.system.StorageNamingStrategy
import com.sanibonani.save.domain.repository.system.StorageRepository
import javax.inject.Inject
import javax.inject.Singleton
import com.sanibonani.save.domain.utils.safeRunCatching

@Singleton
class FileStorageService @Inject constructor(
    private val storageRepo: StorageRepository,
    private val namingStrategy: StorageNamingStrategy
) {

    companion object {
        const val MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024 // 10MB
    }

    private fun sanitizeExtension(extensionOrFileName: String): String {
        val ext = extensionOrFileName.trim().substringAfterLast(".", extensionOrFileName).lowercase()
        val cleaned = ext.filter { it.isLetterOrDigit() }
        return if (cleaned.length in 2..5) cleaned else "pdf"
    }

    private fun validateFileBytes(byteArray: ByteArray) {
        require(byteArray.isNotEmpty()) { "Cannot upload an empty file." }
        require(byteArray.size <= MAX_FILE_SIZE_BYTES) { "File size exceeds 10MB limit." }
    }

    suspend fun uploadMemberDocument(
        memberId: String,
        index: Int,
        byteArray: ByteArray,
        extension: String
    ): Result<String> = safeRunCatching {
        validateFileBytes(byteArray)
        val cleanExt = sanitizeExtension(extension)
        val bucket = namingStrategy.getBucketForMemberDoc(index)
        val path = namingStrategy.getMemberDocPath(memberId, index, cleanExt, System.currentTimeMillis())
        storageRepo.uploadFile(bucket, path, byteArray).getOrThrow()
        storageRepo.getPublicUrl(bucket, path)
    }

    suspend fun uploadGroupConstitution(
        groupId: String,
        byteArray: ByteArray,
        extension: String
    ): Result<String> = safeRunCatching {
        validateFileBytes(byteArray)
        val cleanExt = sanitizeExtension(extension)
        val bucket = namingStrategy.getBucketForGroupDoc()
        val path = namingStrategy.getGroupDocPath(groupId, cleanExt, System.currentTimeMillis())
        storageRepo.uploadFile(bucket, path, byteArray).getOrThrow()
        storageRepo.getPublicUrl(bucket, path)
    }

    suspend fun uploadLoanContract(
        loanId: String,
        byteArray: ByteArray,
        extension: String
    ): Result<String> = safeRunCatching {
        validateFileBytes(byteArray)
        val cleanExt = sanitizeExtension(extension)
        val bucket = namingStrategy.getBucketForLoanContract()
        val path = namingStrategy.getLoanContractPath(loanId, cleanExt, System.currentTimeMillis())
        storageRepo.uploadFile(bucket, path, byteArray).getOrThrow()
        storageRepo.getPublicUrl(bucket, path)
    }

    suspend fun uploadBeneficiaryDocument(
        memberId: String,
        beneficiaryId: String,
        byteArray: ByteArray,
        extension: String
    ): Result<String> = safeRunCatching {
        validateFileBytes(byteArray)
        val cleanExt = sanitizeExtension(extension)
        val bucket = namingStrategy.getBucketForBeneficiaryDoc()
        val path = namingStrategy.getBeneficiaryDocPath(memberId, beneficiaryId, cleanExt, System.currentTimeMillis())
        storageRepo.uploadFile(bucket, path, byteArray).getOrThrow()
        storageRepo.getPublicUrl(bucket, path)
    }

    suspend fun uploadClaimDocument(
        claimId: String,
        byteArray: ByteArray,
        extension: String
    ): Result<String> = safeRunCatching {
        validateFileBytes(byteArray)
        val cleanExt = sanitizeExtension(extension)
        val bucket = namingStrategy.getBucketForClaimDoc()
        val path = namingStrategy.getClaimDocPath(claimId, cleanExt, System.currentTimeMillis())
        storageRepo.uploadFile(bucket, path, byteArray).getOrThrow()
        storageRepo.getPublicUrl(bucket, path)
    }
}
