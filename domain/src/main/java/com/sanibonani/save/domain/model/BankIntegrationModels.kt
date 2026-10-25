package com.sanibonani.save.domain.model

import kotlinx.serialization.*

/**
 * Represents a physical bank transaction recorded in the group's bank account.
 */
@Serializable
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
)

@Serializable
enum class BankTransactionStatus {
    @SerialName("unreconciled") UNRECONCILED,
    @SerialName("matched") RECONCILED, // DB uses 'matched'
    @SerialName("ignored") IGNORED,
    @SerialName("manual_fix") MANUAL_FIX
}

/**
 * Tracks the connection status between a group and their physical bank account.
 */
@Serializable
data class BankConnection(
    val id: String,
    @SerialName("group_id") val groupId: String,
    @SerialName("provider_id") val providerId: String,
    val status: BankConnectionStatus = BankConnectionStatus.CONNECTED,
    @SerialName("account_name") val accountName: String? = null,
    @SerialName("account_number_hidden") val accountNumberHidden: String? = null,
    @SerialName("linked_at") val linkedAt: String,
    @SerialName("expires_at") val expiresAt: String? = null
)

@Serializable
enum class BankConnectionStatus {
    @SerialName("connected") CONNECTED,
    @SerialName("pending") PENDING,
    @SerialName("expired") EXPIRED,
    @SerialName("disconnected") DISCONNECTED
}
