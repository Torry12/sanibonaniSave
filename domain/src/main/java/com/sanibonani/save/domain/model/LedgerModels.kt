@file:OptIn(ExperimentalSerializationApi::class)
package com.sanibonani.save.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.json.*
import java.time.Instant
import java.time.OffsetDateTime

@Serializable
@Parcelize
data class LedgerEntry(
    val id: String? = null,
    @SerialName("group_id") val groupId: String = "",
    @SerialName("member_id") val memberId: String? = null,
    @SerialName("transaction_id") val transactionId: String? = null,
    val amount: Money = Money.ZERO,
    @SerialName("balance_after") val balanceAfter: Money = Money.ZERO,
    val description: String = "",
    val category: String = "",
    @SerialName("is_platform") val isPlatform: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null
) : Parcelable

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
    val status: SettlementStatus = SettlementStatus.PENDING,
    @SerialName("settled_at") val settledAt: Long? = null,
    @SerialName("fee_deducted") val feeDeducted: Money = Money.ZERO
) : Parcelable

object FlexibleEpochSerializer : KSerializer<Long> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("FlexibleEpochSerializer", PrimitiveKind.LONG)

    override fun serialize(encoder: Encoder, value: Long) {
        encoder.encodeLong(value)
    }

    override fun deserialize(decoder: Decoder): Long {
        val input = decoder as? JsonDecoder ?: return 0L
        val element = input.decodeJsonElement()
        return when {
            element is JsonPrimitive && element.isString -> {
                val str = element.content
                str.toLongOrNull() ?: parseIsoToEpochMillis(str)
            }
            element is JsonPrimitive -> element.longOrNull ?: 0L
            else -> 0L
        }
    }

    private fun parseIsoToEpochMillis(isoString: String): Long {
        return try {
            Instant.parse(isoString).toEpochMilli()
        } catch (_: Exception) {
            try {
                OffsetDateTime.parse(isoString).toInstant().toEpochMilli()
            } catch (_: Exception) {
                0L
            }
        }
    }
}

@Serializable
@Parcelize
data class ReconciliationRecord(
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val id: String? = null,
    @SerialName("group_id") val groupId: String = "",
    @Serializable(with = FlexibleEpochSerializer::class)
    @SerialName("period_start") val periodStart: Long = 0L,
    @Serializable(with = FlexibleEpochSerializer::class)
    @SerialName("period_end") val periodEnd: Long = 0L,
    val status: ReconciliationStatus = ReconciliationStatus.PENDING,
    @SerialName("ledger_balance") val ledgerBalance: Money = Money.ZERO,
    @SerialName("actual_balance") val actualBalance: Money = Money.ZERO,
    val discrepancy: Money = Money.ZERO,
    val notes: String? = null,
    @SerialName("performed_by") val performedBy: String? = null,
    @SerialName("created_at") val createdAt: String? = null
) : Parcelable

@Serializable
enum class SettlementStatus {
    @SerialName("pending") PENDING,
    @SerialName("settled") SETTLED,
    @SerialName("failed") FAILED
}

@Serializable
enum class ReconciliationStatus {
    @SerialName("pending") PENDING,
    @SerialName("balanced") BALANCED,
    @SerialName("discrepancy") @JsonNames("discrepancy_found") DISCREPANCY,
    @SerialName("resolved") RESOLVED
}

@Serializable
@Parcelize
data class ConsolidationReport(
    @SerialName("group_id") val groupId: String,
    @SerialName("entries_reconstructed") val entriesCreated: Int = 0,
    @SerialName("duplicates_found") val duplicatesFound: Int = 0,
    @SerialName("final_balance") val finalBalance: Money = Money.ZERO,
    @SerialName("reconciliation_report") val reconciliationRecord: ReconciliationRecord? = null,
    val errors: List<String> = emptyList()
) : Parcelable

