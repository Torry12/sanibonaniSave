package com.sanibonani.save.data.utils

import com.sanibonani.save.domain.model.AppNotification
import com.sanibonani.save.domain.model.DocumentStatus
import com.sanibonani.save.domain.model.Member
import com.sanibonani.save.domain.model.NotifEvent

private const val PROFILE_PHOTO_DOCUMENT_INDEX = 0

fun filterNotificationsForMember(
    notifications: List<AppNotification>,
    memberId: String?
): List<AppNotification> {
    return notifications
        .filter { it.memberId == null || it.memberId == memberId }
        .sortedByDescending { it.id }
}

fun partitionMemberNotifications(
    notifications: List<AppNotification>
): Pair<List<AppNotification>, List<AppNotification>> {
    return notifications.partition { notif ->
        notif.triggerEvent == NotifEvent.CUSTOM ||
            notif.triggerEvent == NotifEvent.MEMBER_MESSAGE
    }
}

fun memberDocumentLabel(documentIndex: Int): String {
    return when (documentIndex) {
        PROFILE_PHOTO_DOCUMENT_INDEX -> "Profile Photo"
        1 -> "ID Document"
        2 -> "Proof of Residence"
        3 -> "Beneficiary Form"
        4 -> "Marriage Certificate"
        5 -> "Constitution"
        else -> "Document $documentIndex"
    }
}

fun shouldRefreshProfileImageVersion(documentIndex: Int): Boolean {
    return documentIndex == PROFILE_PHOTO_DOCUMENT_INDEX
}

fun requiresSupabaseAuthHeaders(url: String): Boolean {
    // Only authenticated storage endpoints require auth headers.
    return url.contains("/storage/v1/object/authenticated/")
}

private val MEMBER_INFRA_STORAGE_ERROR_MARKERS = listOf(
    "File storage is not configured on the server",
    "Uploads are blocked by storage security rules",
    "Database permissions are not configured"
)

fun sanitizeMemberDocumentUploadError(message: String): String {
    return if (MEMBER_INFRA_STORAGE_ERROR_MARKERS.any { marker -> message.contains(marker, ignoreCase = true) }) {
        "Document upload is temporarily unavailable. Please try again later."
    } else {
        message
    }
}

fun resolveFreshProfilePhotoUrl(
    existingUrl: String?,
    uploadedUrl: String?
): String? {
    val uploaded = uploadedUrl?.trim()
    val existing = existingUrl?.trim()
    return when {
        !uploaded.isNullOrEmpty() -> uploaded
        !existing.isNullOrEmpty() -> existing
        else -> null
    }
}

fun Member.mergeUploadedMemberDocument(
    documentIndex: Int,
    uploadedUrl: String,
    documentType: String?
): Member {
    return when (documentIndex) {
        PROFILE_PHOTO_DOCUMENT_INDEX -> copy(
            profilePhotoUrl = resolveFreshProfilePhotoUrl(
                existingUrl = profilePhotoUrl,
                uploadedUrl = uploadedUrl
            )
        )
        1 -> copy(
            document1Url = document1Url.orUploadedFallback(uploadedUrl),
            document1Type = document1Type ?: documentType,
            document1Status = document1Status
        )
        2 -> copy(
            document2Url = document2Url.orUploadedFallback(uploadedUrl),
            document2Type = document2Type ?: documentType,
            document2Status = document2Status
        )
        3 -> copy(
            document3Url = document3Url.orUploadedFallback(uploadedUrl),
            document3Type = document3Type ?: documentType,
            document3Status = document3Status
        )
        4 -> copy(
            document4Url = document4Url.orUploadedFallback(uploadedUrl),
            document4Type = document4Type ?: documentType,
            document4Status = document4Status
        )
        5 -> copy(
            document5Url = document5Url.orUploadedFallback(uploadedUrl),
            document5Type = document5Type ?: documentType,
            document5Status = document5Status
        )
        else -> this
    }
}

fun Member.applyUploadedMemberDocument(
    documentIndex: Int,
    uploadedUrl: String,
    documentType: String?
): Member {
    return when (documentIndex) {
        PROFILE_PHOTO_DOCUMENT_INDEX -> copy(
            profilePhotoUrl = resolveFreshProfilePhotoUrl(
                existingUrl = profilePhotoUrl,
                uploadedUrl = uploadedUrl
            )
        )
        1 -> copy(
            document1Url = uploadedUrl,
            document1Type = documentType ?: document1Type,
            document1Status = DocumentStatus.PENDING
        )
        2 -> copy(
            document2Url = uploadedUrl,
            document2Type = documentType ?: document2Type,
            document2Status = DocumentStatus.PENDING
        )
        3 -> copy(
            document3Url = uploadedUrl,
            document3Type = documentType ?: document3Type,
            document3Status = DocumentStatus.PENDING
        )
        4 -> copy(
            document4Url = uploadedUrl,
            document4Type = documentType ?: document4Type,
            document4Status = DocumentStatus.PENDING
        )
        5 -> copy(
            document5Url = uploadedUrl,
            document5Type = documentType ?: document5Type,
            document5Status = DocumentStatus.PENDING
        )
        else -> this
    }
}

private fun String?.orUploadedFallback(uploadedUrl: String): String {
    return this?.takeIf { it.isNotBlank() } ?: uploadedUrl
}

// ──────────────────────────────────────────────────────────────────────────────
// SHARED SUPABASE AUTH HEADER BUILDER
// Used by both MemberViewModel and AdminViewModel to construct authenticated
// storage download headers without duplicating BuildConfig / token logic.
// ──────────────────────────────────────────────────────────────────────────────

/**
 * Builds the Supabase Storage authorization headers needed for authenticated
 * document downloads.  Both `apikey` and `Authorization` are required so that
 * private-bucket requests succeed regardless of the Supabase project config.
 *
 * @param anonKey  `BuildConfig.SUPABASE_ANON_KEY`
 * @param accessToken  Optional bearer token from the active session.
 */
fun buildSupabaseAuthHeaders(anonKey: String, accessToken: String?): Map<String, String> {
    return buildMap {
        put("apikey", anonKey)
        accessToken?.let { put("Authorization", "Bearer $it") }
    }
}

