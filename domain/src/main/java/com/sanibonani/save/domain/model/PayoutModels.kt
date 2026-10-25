package com.sanibonani.save.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents a disbursement from the platform account to a specific group's bank account.
 * This is triggered manually by an Admin after validating group balances.
 */
@Serializable
data class PayoutRequest(
    val id: String? = null,
    @SerialName("group_id")    val groupId: String,
    @SerialName("member_id")   val memberId: String? = null,
    val amount: Money = Money.ZERO,
    @SerialName("bank_name")   val bankName: String,
    @SerialName("account_no")  val accountNo: String,
    @SerialName("branch_code") val branchCode: String,
    val status: PayoutStatus = PayoutStatus.PENDING,
    @SerialName("payout_type") val type: PayoutType = PayoutType.STANDARD,
    val metadata: kotlinx.serialization.json.JsonObject? = null,
    @SerialName("processed_by") val processedBy: String? = null,
    @SerialName("processed_at") val processedAt: String? = null,
    @SerialName("payout_reference") val payoutReference: String? = null,
    @SerialName("supporting_document_url") val supportingDocumentUrl: String? = null,
    @SerialName("supporting_document_status") val supportingDocumentStatus: DocumentStatus = DocumentStatus.PENDING,
    @SerialName("created_at")   val createdAt: String? = null
)

@Serializable
enum class PayoutStatus {
    @SerialName("pending")   PENDING,
    @SerialName("group_approved") GROUP_APPROVED,
    @SerialName("processing") PROCESSING,
    @SerialName("completed") COMPLETED,
    @SerialName("rejected")  REJECTED,
    @SerialName("failed")    FAILED,
    @SerialName("cancelled") CANCELLED
}

/**
 * The single state machine for payout status movement, shared by the online admin path and the
 * offline replay lane. Terminal states accept nothing, and a status is never a transition from
 * itself: callers that want idempotency handle equality before consulting this.
 */
fun PayoutStatus.canTransitionTo(next: PayoutStatus): Boolean {
    if (this == next) return false
    val allowed = when (this) {
        PayoutStatus.PENDING -> listOf(
            PayoutStatus.GROUP_APPROVED,
            PayoutStatus.CANCELLED,
            PayoutStatus.REJECTED
        )
        PayoutStatus.GROUP_APPROVED -> listOf(
            PayoutStatus.PROCESSING,
            PayoutStatus.CANCELLED,
            PayoutStatus.REJECTED
        )
        PayoutStatus.PROCESSING -> listOf(
            PayoutStatus.COMPLETED,
            PayoutStatus.FAILED,
            PayoutStatus.CANCELLED
        )
        // A failed transfer is abandoned through either terminal state: the platform screen
        // offers Reject on a failed payout, so the state machine must accept it.
        PayoutStatus.FAILED -> listOf(
            PayoutStatus.PROCESSING,
            PayoutStatus.CANCELLED,
            PayoutStatus.REJECTED
        )
        PayoutStatus.COMPLETED,
        PayoutStatus.CANCELLED,
        PayoutStatus.REJECTED -> emptyList()
    }
    return next in allowed
}

@Serializable
enum class PayoutType {
    @SerialName("standard") STANDARD,
    @SerialName("annual") ANNUAL,
    @SerialName("emergency") EMERGENCY,
    @SerialName("disinvestment") DISINVESTMENT,
    @SerialName("claim") CLAIM,
    @SerialName("rotation") ROTATION,

    // Both literals are live data and cannot be collapsed: 'rosca' is what
    // process_rosca_payout_v2/v3 insert and 'custom' is the payouts table's own DEFAULT,
    // so any row created outside the app arrives as one of these two.
    @SerialName("rosca") ROSCA,
    @SerialName("custom") CUSTOM
}
