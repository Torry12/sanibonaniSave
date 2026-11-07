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

/**
 * The types `internal_post_contribution` sends to `record_platform_revenue_v2` instead of
 * `increment_group_balance`, so the money lands in `platform_ledger` - which has no `member_id`
 * column - and the group never holds it. `members.total_paid` still counts a registration fee as
 * paid in, so anything that calls that column "savings" has to take these rows back out.
 * `joining_fee` is deliberately absent: the older spelling is booked to the group.
 */
private val platformBookedContributionTypes = setOf(
    "registration_contribution", "member_fee", "member_fee_ledger"
)

val Contribution.isPlatformFee: Boolean
    get() = type in platformBookedContributionTypes
