package com.sanibonani.save.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Result of AI-based ID document extraction.
 */
@Serializable
@Parcelize
data class IdDocumentExtraction(
    @SerialName("full_name") val fullName: String? = null,
    @SerialName("id_number") val idNumber: String? = null,
    @SerialName("date_of_birth") val dateOfBirth: String? = null,
    @SerialName("document_type") val documentType: String? = null,
    @SerialName("confidence_score") val confidenceScore: Double = 0.0,
    @SerialName("is_authentic") val isAuthentic: Boolean = false,
    @SerialName("extraction_raw") val extractionRaw: String? = null
) : Parcelable

/**
 * Result of facial recognition match between ID and Selfie.
 */
@Serializable
@Parcelize
data class FaceMatchResult(
    @SerialName("is_match") val isMatch: Boolean = false,
    @SerialName("confidence_score") val confidenceScore: Double = 0.0,
    @SerialName("match_status") val matchStatus: String = "PENDING"
) : Parcelable
