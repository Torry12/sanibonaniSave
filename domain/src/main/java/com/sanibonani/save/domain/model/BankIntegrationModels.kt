package com.sanibonani.save.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.*

/**
 * Represents a physical bank transaction recorded in the group's bank account.
 */
@Serializable
@Parcelize
data class BankTransaction(
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val id: String? = null,
    @SerialName("group_id") val groupId: String,
    @SerialName("bank_reference") val bankReference: String,
    val amount: Money,
    @SerialName("transaction_date") val transactionDate: String,
    val description: String? = null,
    val status: BankTransactionStatus = BankTransactionStatus.UNRECONCILED,
    @SerialName("reconciled_with") val reconciledWith: String? = null,
    @SerialName("created_at") val createdAt: String? = null
) : Parcelable

@Serializable
@Parcelize
enum class BankTransactionStatus : Parcelable {
    @SerialName("unreconciled") UNRECONCILED,
    @SerialName("reconciled") RECONCILED,
    @SerialName("ignored") IGNORED
}
