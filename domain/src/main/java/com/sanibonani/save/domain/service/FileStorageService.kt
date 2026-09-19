package com.sanibonani.save.domain.service

import com.sanibonani.save.domain.repository.system.StorageNamingStrategy
import com.sanibonani.save.domain.repository.system.StorageRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileStorageService @Inject constructor(
    private val storageRepo: StorageRepository,
    private val namingStrategy: StorageNamingStrategy
) {

    suspend fun uploadMemberDocument(
        memberId: String,
        index: Int,
        byteArray: ByteArray,
        extension: String
    ): Result<String> = runCatching {
        val bucket = namingStrategy.getBucketForMemberDoc(index)
        val path = namingStrategy.getMemberDocPath(memberId, index, extension, System.currentTimeMillis())
        storageRepo.uploadFile(bucket, path, byteArray).getOrThrow()
        storageRepo.getPublicUrl(bucket, path)
    }

    suspend fun uploadGroupConstitution(
        groupId: String,
        byteArray: ByteArray,
        extension: String
    ): Result<String> = runCatching {
        val bucket = namingStrategy.getBucketForGroupDoc()
        val path = namingStrategy.getGroupDocPath(groupId, extension, System.currentTimeMillis())
        storageRepo.uploadFile(bucket, path, byteArray).getOrThrow()
        storageRepo.getPublicUrl(bucket, path)
    }

    suspend fun uploadLoanContract(
        loanId: String,
        byteArray: ByteArray,
        extension: String
    ): Result<String> = runCatching {
        val bucket = namingStrategy.getBucketForLoanContract()
        val path = namingStrategy.getLoanContractPath(loanId, extension, System.currentTimeMillis())
        storageRepo.uploadFile(bucket, path, byteArray).getOrThrow()
        storageRepo.getPublicUrl(bucket, path)
    }

    suspend fun uploadBeneficiaryDocument(
        memberId: String,
        beneficiaryId: String,
        byteArray: ByteArray,
        extension: String
    ): Result<String> = runCatching {
        val bucket = namingStrategy.getBucketForBeneficiaryDoc()
        val path = namingStrategy.getBeneficiaryDocPath(memberId, beneficiaryId, extension, System.currentTimeMillis())
        storageRepo.uploadFile(bucket, path, byteArray).getOrThrow()
        storageRepo.getPublicUrl(bucket, path)
    }
}
