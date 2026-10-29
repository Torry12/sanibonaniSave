@file:OptIn(ExperimentalSerializationApi::class)
package com.sanibonani.save.domain.model

import kotlinx.serialization.*
import kotlinx.serialization.json.JsonElement

@Serializable
data class AuditLog(
    val id: String? = null,
    @SerialName("actor_id") val actorId: String = "",
    @SerialName("target_member_id") val targetMemberId: String? = null,
    @SerialName("target_group_id") val targetGroupId: String? = null,
    val action: String = "",
    @SerialName("related_id") val relatedId: String? = null,
    val details: JsonElement? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class MemberContributionStats(
    @SerialName("total_paid") val totalPaid: Money = Money.ZERO,
    @SerialName("total_outstanding") val totalOutstanding: Money = Money.ZERO,
    @SerialName("total_count") val totalCount: Int = 0,
    @SerialName("last_payment") val lastPayment: String? = null
)

@Serializable
data class MemberLoanStats(
    @SerialName("total_borrowed") val totalBorrowed: Money = Money.ZERO,
    @SerialName("total_repaid") val totalRepaid: Money = Money.ZERO,
    @SerialName("outstanding") val outstanding: Money = Money.ZERO
)

@Serializable
data class MemberBehaviorStats(
    val score: Double = 0.0,
    val consistency: Double = 0.0
)

@Serializable
data class MemberAuditReport(
    @SerialName("member_id") val memberId: String = "",
    @SerialName("full_name") val fullName: String? = null,
    val status: String? = null,
    @SerialName("joined_at") val joinedAt: String? = null,
    val contributions: MemberContributionStats? = null,
    val loans: MemberLoanStats? = null,
    val behavior: MemberBehaviorStats? = null,
    @SerialName("recent_audit_logs") val recentAuditLogs: List<AuditLog> = emptyList()
)

@Serializable
data class GroupMembershipStats(
    val total: Int = 0,
    val active: Int = 0,
    val probation: Int = 0,
    val suspended: Int = 0
)

@Serializable
data class GroupActuarialStats(
    @SerialName("risk_score") val riskScore: Int = 0,
    @SerialName("solvency_ratio") val solvencyRatio: Double = 0.0,
    @SerialName("reserve_adequacy") val reserveAdequacy: Double = 0.0
)

@Serializable
data class GroupReconciliationStats(
    val status: String = "pending",
    val discrepancy: Money = Money.ZERO,
    @SerialName("last_check") val lastCheck: String? = null
)

@Serializable
data class GroupFinances(
    @SerialName("total_inflow") val totalInflow: Money = Money.ZERO,
    @SerialName("total_outflow") val totalOutflow: Money = Money.ZERO
)

@Serializable
data class GroupAuditReport(
    @SerialName("group_id") val groupId: String = "",
    // `generate_group_audit_report_v1` emits this key as "name"; SerialName must match it or
    // ignoreUnknownKeys leaves the report header blank on the Group Audit tab.
    @SerialName("name") val name: String = "",
    val type: String = "",
    val balance: Money = Money.ZERO,
    val finances: GroupFinances? = null,
    val membership: GroupMembershipStats? = null,
    val actuarial: GroupActuarialStats? = null,
    val reconciliation: GroupReconciliationStats? = null
)

@Serializable
data class PlatformRiskStats(
    @SerialName("high_risk_members") val highRiskMembers: Int = 0,
    @SerialName("critical_risk_members") val criticalRiskMembers: Int = 0
)

@Serializable
data class PlatformGroupStats(
    @SerialName("total_groups") val totalGroups: Int = 0,
    @SerialName("suspended_groups") val suspendedGroups: Int = 0
)

@Serializable
data class PlatformAuditReport(
    @SerialName("generated_at") val generatedAt: String = "",
    val liquidity: Money = Money.ZERO,
    val revenue: Money = Money.ZERO,
    val risk: PlatformRiskStats? = null,
    val groups: PlatformGroupStats? = null,
    @SerialName("top_level_stats") val topLevelStats: Map<String, String>? = null
)

@Serializable
data class GroupAuditResult(
    @SerialName("group_id") val groupId: String = "",
    @SerialName("ledger_sum") val ledgerSum: Money = Money.ZERO,
    @SerialName("current_balance") val currentBalance: Money = Money.ZERO,
    val discrepancy: Money = Money.ZERO,
    val fixed: Boolean = false
)
