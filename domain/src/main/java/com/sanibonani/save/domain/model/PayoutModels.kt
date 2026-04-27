package com.sanibonani.save.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents a disbursement from the main platform YoCo account to a specific group's bank account.
 * This is triggered manually by an Admin after validating group balances.
 */
@Serializable
@Parcelize
data class PayoutRequest(
    val id: String? = null,
    @SerialName("group_id")    val groupId: String,
    val amount: Double,
    @SerialName("bank_name")   val bankName: String,
    @SerialName("account_no")  val accountNo: String,
    @SerialName("branch_code") val branchCode: String,
    val status: PayoutStatus = PayoutStatus.PENDING,
    @SerialName("processed_by") val processedBy: String? = null,
    @SerialName("processed_at") val processedAt: String? = null,
    @SerialName("yoco_payout_id") val yocoPayoutId: String? = null,
    @SerialName("created_at")   val createdAt: String? = null
) : Parcelable

@Serializable
enum class PayoutStatus {
    @SerialName("pending")   PENDING,
    @SerialName("processing") PROCESSING,
    @SerialName("completed") COMPLETED,
    @SerialName("failed")    FAILED,
    @SerialName("cancelled") CANCELLED
}
