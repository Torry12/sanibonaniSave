package com.sanibonani.save.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@Parcelize
data class PlatformFee(
    val id: String? = null,
    @SerialName("group_id") val groupId: String,
    @SerialName("fee_type") val feeType: String, // 'registration', 'monthly'
    @SerialName("amount_due") val amountDue: Money,
    @SerialName("amount_paid") val amountPaid: Money = Money.ZERO,
    val status: String = "due", // 'paid', 'due', 'warning', 'suspended'
    @SerialName("due_date") val dueDate: String? = null,
    @SerialName("paid_at") val paidAt: String? = null,
    @SerialName("month_reference") val monthReference: String? = null,
    @SerialName("transaction_id") val transactionId: String? = null,
    @SerialName("created_at") val createdAt: String? = null
) : Parcelable
