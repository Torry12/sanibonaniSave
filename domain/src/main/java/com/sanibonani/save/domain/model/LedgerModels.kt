@file:OptIn(ExperimentalSerializationApi::class)
package com.sanibonani.save.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.*
import java.time.Instant

@Serializable
@Parcelize
data class Transaction(
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val id: String? = null,
    @SerialName("group_id") val groupId: String = "",
    @SerialName("member_id") val memberId: String? = null,
    @SerialName("amount") val amount: Long,
    @SerialName("currency") val currency: String = "ZAR",
    @SerialName("type") val type: TransactionType,
    @SerialName("status") val status: TransactionStatus = TransactionStatus.PENDING,
    @SerialName("created_at") val createdAt: Long = Instant.now().toEpochMilli(),
    @SerialName("description") val description: String? = null
) : Parcelable

@Serializable
@Parcelize
enum class TransactionType : Parcelable {
    @SerialName("contribution") CONTRIBUTION,
    @SerialName("payout") PAYOUT,
    @SerialName("fee") FEE,
    @SerialName("reversal") REVERSAL
}

@Serializable
@Parcelize
enum class TransactionStatus : Parcelable {
    @SerialName("pending") PENDING,
    @SerialName("completed") COMPLETED,
    @SerialName("failed") FAILED,
    @SerialName("reversed") REVERSED
}

@Serializable
@Parcelize
data class SettlementRecord(
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val id: String? = null,
    @SerialName("transaction_id") val transactionId: String,
    @SerialName("provider_reference") val providerReference: String? = null,
    @SerialName("status") val status: String = "pending",
    @SerialName("settled_at") val settledAt: Long? = null
) : Parcelable

@Serializable
@Parcelize
data class ReconciliationRecord(
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val id: String? = null,
    @SerialName("group_id") val groupId: String,
    @SerialName("period_start") val periodStart: Long,
    @SerialName("period_end") val periodEnd: Long,
    @SerialName("status") val status: String = "pending",
    @SerialName("discrepancy") val discrepancy: Long? = null
) : Parcelable
