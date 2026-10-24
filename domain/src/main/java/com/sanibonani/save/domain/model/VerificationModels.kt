package com.sanibonani.save.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class VerificationResult(
    val isValid: Boolean,
    val issues: List<VerificationIssue>,
    val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        val Valid = VerificationResult(true, emptyList())
    }
}

@Serializable
data class VerificationIssue(
    val category: String,
    val message: String,
    val severity: IssueSeverity,
    val suggestedAction: String? = null
)

enum class IssueSeverity {
    INFO, WARNING, CRITICAL
}
