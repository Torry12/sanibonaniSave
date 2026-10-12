@file:OptIn(ExperimentalSerializationApi::class)
package com.sanibonani.save.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue
import kotlinx.serialization.*
import kotlinx.serialization.json.JsonElement

@Serializable
@Parcelize
data class AuditLog(
    val id: String? = null,
    @SerialName("actor_id") val actorId: String = "",
    @SerialName("target_member_id") val targetMemberId: String? = null,
    @SerialName("target_group_id") val targetGroupId: String? = null,
    val action: String = "",
    @SerialName("related_id") val relatedId: String? = null,
    val details: @RawValue JsonElement? = null,
    @SerialName("created_at") val createdAt: String? = null
) : Parcelable

@Serializable
@Parcelize
data class MemberContributionStats(
    @SerialName("total_paid") val totalPaid: Money = Money.ZERO,
    @SerialName("total_outstanding") val totalOutstanding: Money = Money.ZERO,
    @SerialName("total_count") val totalCount: Int = 0,
    @SerialName("last_payment") val lastPayment: String? = null
) : Parcelable

@Serializable
@Parcelize
data class MemberLoanStats(
    @SerialName("total_borrowed") val totalBorrowed: Money = Money.ZERO,
    @SerialName("total_repaid") val totalRepaid: Money = Money.ZERO,
    @SerialName("outstanding") val outstanding: Money = Money.ZERO
) : Parcelable

@Serializable
@Parcelize
data class MemberBehaviorStats(
    val score: Double = 0.0,
    val consistency: Double = 0.0
) : Parcelable

@Serializable
@Parcelize
data class MemberAuditReport(
    @SerialName("member_id") val memberId: String = "",
    @SerialName("full_name") val fullName: String? = null,
    val status: String? = null,
    @SerialName("joined_at") val joinedAt: String? = null,
    val contributions: MemberContributionStats? = null,
    val loans: MemberLoanStats? = null,
    val behavior: MemberBehaviorStats? = null,
    @SerialName("recent_audit_logs") val recentAuditLogs: List<AuditLog> = emptyList(),
    val summary: Map<String, String> = emptyMap()
) : Parcelable

@Serializable
@Parcelize
data class GroupMembershipStats(
    val total: Int = 0,
    val active: Int = 0,
    val probation: Int = 0,
    val suspended: Int = 0
) : Parcelable

@Serializable
@Parcelize
data class GroupActuarialStats(
    @SerialName("risk_score") val riskScore: Int = 0,
    @SerialName("solvency_ratio") val solvencyRatio: Double = 0.0,
    @SerialName("reserve_adequacy") val reserveAdequacy: Double = 0.0
) : Parcelable

@Serializable
@Parcelize
data class GroupReconciliationStats(
    val status: String = "pending",
    val discrepancy: Money = Money.ZERO,
    @SerialName("last_check") val lastCheck: String? = null
) : Parcelable

@Serializable
@Parcelize
data class GroupFinances(
    val balance: Money = Money.ZERO,
    @SerialName("total_inflow") val totalInflow: Money = Money.ZERO,
    @SerialName("total_outflow") val totalOutflow: Money = Money.ZERO,
    @SerialName("outstanding_loans") val outstandingLoans: Money = Money.ZERO
) : Parcelable

@Serializable
@Parcelize
data class GroupAuditReport(
    @SerialName("group_id") val groupId: String = "",
    @SerialName("group_name") val name: String = "",
    val type: String = "",
    val balance: Money = Money.ZERO,
    val finances: GroupFinances? = null,
    val membership: GroupMembershipStats? = null,
    val actuarial: GroupActuarialStats? = null,
    val reconciliation: GroupReconciliationStats? = null,
    @SerialName("ledger_summary") val ledgerSummary: List<LedgerEntry>? = null,
    val findings: List<String> = emptyList()
) : Parcelable

@Serializable
@Parcelize
data class PlatformRiskStats(
    @SerialName("high_risk_members") val highRiskMembers: Int = 0,
    @SerialName("critical_risk_members") val criticalRiskMembers: Int = 0
) : Parcelable

@Serializable
@Parcelize
data class PlatformGroupStats(
    @SerialName("total_groups") val totalGroups: Int = 0,
    @SerialName("suspended_groups") val suspendedGroups: Int = 0
) : Parcelable

@Serializable
@Parcelize
data class PlatformAuditReport(
    @SerialName("generated_at") val generatedAt: String = "",
    val liquidity: Money = Money.ZERO,
    val revenue: Money = Money.ZERO,
    val risk: PlatformRiskStats? = null,
    val groups: PlatformGroupStats? = null,
    val issues: List<String> = emptyList(),
    @SerialName("top_level_stats") val topLevelStats: Map<String, String>? = null
) : Parcelable

@Serializable
@Parcelize
data class GroupAuditResult(
    @SerialName("group_id") val groupId: String = "",
    @SerialName("ledger_sum") val ledgerSum: Money = Money.ZERO,
    @SerialName("current_balance") val currentBalance: Money = Money.ZERO,
    val discrepancy: Money = Money.ZERO,
    val fixed: Boolean = false
) : Parcelable
