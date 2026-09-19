package com.sanibonani.save.domain.repository.system

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Strategy interface for generating storage paths and determining buckets.
 */
interface StorageNamingStrategy {
    fun getBucket(index: Int): String
    fun getPath(id: String, index: Int, extension: String, timestamp: Long): String
}

@Singleton
class MemberStorageNamingStrategy @Inject constructor() : StorageNamingStrategy {

    companion object {
        const val PROFILE_PHOTO_INDEX = 0
        const val AVATARS_BUCKET = "avatars"
        const val DOCUMENTS_BUCKET = "documents"
    }

    override fun getBucket(index: Int): String {
        return if (index == PROFILE_PHOTO_INDEX) AVATARS_BUCKET else DOCUMENTS_BUCKET
    }

    override fun getPath(id: String, index: Int, extension: String, timestamp: Long): String {
        return if (index == PROFILE_PHOTO_INDEX) {
            "members/$id/profile_$timestamp.$extension"
        } else {
            "members/$id/doc_${index}_$timestamp.$extension"
        }
    }

    fun getRelationalPath(memberId: String, label: String, extension: String, timestamp: Long): String {
        val sanitizedLabel = label.filter { it.isLetterOrDigit() }
        return "members/$memberId/relational_${sanitizedLabel}_$timestamp.$extension"
    }

    fun getBeneficiaryPath(memberId: String, beneficiaryId: String, extension: String, timestamp: Long): String {
        return "beneficiaries/$memberId/${beneficiaryId}_$timestamp.$extension"
    }
}
