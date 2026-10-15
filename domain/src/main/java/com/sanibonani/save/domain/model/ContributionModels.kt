@file:OptIn(ExperimentalSerializationApi::class)

package com.sanibonani.save.domain.model

import kotlinx.serialization.*

// ─────────────────────────────────────────────────────────────────────────────
//  CONTRIBUTION
// ─────────────────────────────────────────────────────────────────────────────
@Serializable
data class Contribution(
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val id: String? = null,
    @SerialName("member_id")            val memberId: String = "",
    @SerialName("group_id")             val groupId: String = "",
    @SerialName("policy_id")            val policyId: String? = null,
    val amount: Money = Money.ZERO,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("created_at")           val createdAt: String? = null,
    @SerialName("due_date")             val dueDate: String = "",
    @SerialName("paid_at")              val paidAt: String? = null,
    @SerialName("status")               val status: ContributionStatus = ContributionStatus.DUE,
    @SerialName("type")                 val type: String = "contribution",
    @SerialName("payment_method")       val paymentMethod: String = "bank",
    @SerialName("transaction_id")       val transactionId: String? = null,
    @SerialName("late_fees_applied")    val lateFeesApplied: Boolean = false,
    @SerialName("late_fee_amount")      val lateFeeAmount: Money = Money.ZERO,
    @SerialName("principal_amount")     val principalAmount: Money = Money.ZERO
)

@Serializable
enum class ContributionStatus(val displayName: String) {
    @SerialName("paid")    PAID("Paid"),
    @SerialName("due")     DUE("Due"),
    @SerialName("overdue") OVERDUE("Overdue"),
    @SerialName("partial") PARTIAL("Partial")
}

@Serializable
data class RecordContributionResult(
    val contribution: Contribution,
    @SerialName("new_balance") val newBalance: Money
)
