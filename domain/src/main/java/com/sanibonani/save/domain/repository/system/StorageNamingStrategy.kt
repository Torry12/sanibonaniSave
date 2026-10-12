package com.sanibonani.save.domain.repository.system

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Strategy interface for generating storage paths and determining buckets.
 */
interface StorageNamingStrategy {
    fun getBucketForMemberDoc(index: Int): String
    fun getMemberDocPath(memberId: String, index: Int, extension: String, timestamp: Long): String

    fun getBucketForGroupDoc(): String
    fun getGroupDocPath(groupId: String, extension: String, timestamp: Long): String

    fun getBucketForLoanContract(): String
    fun getLoanContractPath(loanId: String, extension: String, timestamp: Long): String

    fun getBucketForBeneficiaryDoc(): String
    fun getBeneficiaryDocPath(memberId: String, beneficiaryId: String, extension: String, timestamp: Long): String

    fun getBucketForClaimDoc(): String
    fun getClaimDocPath(claimId: String, extension: String, timestamp: Long): String
}

@Singleton
class DefaultStorageNamingStrategy @Inject constructor() : StorageNamingStrategy {

    companion object {
        const val PROFILE_PHOTO_INDEX = 0
        const val AVATARS_BUCKET = "avatars"
        const val DOCUMENTS_BUCKET = "documents"
        const val CONSTITUTIONS_BUCKET = "constitutions"
        const val LOAN_CONTRACTS_BUCKET = "loan_contracts"
        const val BENEFICIARIES_BUCKET = "beneficiaries"
    }

    override fun getBucketForMemberDoc(index: Int): String {
        return if (index == PROFILE_PHOTO_INDEX) AVATARS_BUCKET else DOCUMENTS_BUCKET
    }

    override fun getMemberDocPath(memberId: String, index: Int, extension: String, timestamp: Long): String {
        return if (index == PROFILE_PHOTO_INDEX) {
            "members/$memberId/profile_$timestamp.$extension"
        } else {
            "members/$memberId/doc_${index}_$timestamp.$extension"
        }
    }

    override fun getBucketForGroupDoc(): String = CONSTITUTIONS_BUCKET

    override fun getGroupDocPath(groupId: String, extension: String, timestamp: Long): String {
        return "groups/$groupId/constitution_$timestamp.$extension"
    }

    override fun getBucketForLoanContract(): String = LOAN_CONTRACTS_BUCKET

    override fun getLoanContractPath(loanId: String, extension: String, timestamp: Long): String {
        return "loans/$loanId/contract_$timestamp.$extension"
    }

    override fun getBucketForBeneficiaryDoc(): String = BENEFICIARIES_BUCKET

    override fun getBeneficiaryDocPath(memberId: String, beneficiaryId: String, extension: String, timestamp: Long): String {
        return "beneficiaries/$memberId/${beneficiaryId}_$timestamp.$extension"
    }

    override fun getBucketForClaimDoc(): String = DOCUMENTS_BUCKET

    override fun getClaimDocPath(claimId: String, extension: String, timestamp: Long): String {
        return "claims/${claimId}_$timestamp.$extension"
    }

    fun getRelationalPath(memberId: String, label: String, extension: String, timestamp: Long): String {
        val sanitizedLabel = label.filter { it.isLetterOrDigit() }
        return "members/$memberId/relational_${sanitizedLabel}_$timestamp.$extension"
    }
}
