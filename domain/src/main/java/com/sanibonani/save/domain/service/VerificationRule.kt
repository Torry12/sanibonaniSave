package com.sanibonani.save.domain.service

import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.utils.PaymentCalculator
import kotlin.math.abs

/**
 * Base interface for data verification rules.
 */
interface VerificationRule<T> {
    suspend fun verify(context: T): List<VerificationIssue>
}

// ── MEMBER RULES ─────────────────────────────────────────────────────────────

data class MemberVerificationContext(
    val member: Member,
    val group: Group,
    val contributions: List<Contribution>
)

class MemberTotalPaidSyncRule : VerificationRule<MemberVerificationContext> {
    override suspend fun verify(context: MemberVerificationContext): List<VerificationIssue> {
        val calculatedTotalPaid = context.contributions
            .filter { it.status == ContributionStatus.PAID || it.status == ContributionStatus.PARTIAL }
            .fold(Money.ZERO) { acc, next -> acc + next.amount }

        val displayedTotalPaid = context.member.totalPaid

        return if ((calculatedTotalPaid - displayedTotalPaid).abs() > Money("0.01")) {
            listOf(VerificationIssue(
                category = "Financial",
                message = "Displayed 'Total Paid' (${displayedTotalPaid.format()}) does not match sum of transactions (${calculatedTotalPaid.format()}).",
                severity = IssueSeverity.WARNING,
                suggestedAction = "Refresh data or contact support if the discrepancy persists."
            ))
        } else emptyList()
    }
}

class MemberProbationConsistencyRule : VerificationRule<MemberVerificationContext> {
    override suspend fun verify(context: MemberVerificationContext): List<VerificationIssue> {
        return if (context.group.probationMonths == 0 && context.member.status == MemberStatus.PROBATION) {
            listOf(VerificationIssue(
                category = "Status",
                message = "Member is in 'Probation' status but the group has 0 probation months configured.",
                severity = IssueSeverity.INFO,
                suggestedAction = "Admin should update member status to 'Active'."
            ))
        } else emptyList()
    }
}

class MemberRiskShortfallRule(private val paymentCalculator: PaymentCalculator) : VerificationRule<MemberVerificationContext> {
    override suspend fun verify(context: MemberVerificationContext): List<VerificationIssue> {
        val calculation = paymentCalculator.calculateStatus(context.group, context.member, context.contributions)
        val threshold = context.group.monthlyContribution * 3
        return if (calculation.isOverdue && context.member.status == MemberStatus.ACTIVE && calculation.shortfall > threshold) {
            listOf(VerificationIssue(
                category = "Risk",
                message = "Member is 'Active' but has a significant shortfall (${calculation.shortfall.format()}).",
                severity = IssueSeverity.WARNING,
                suggestedAction = "Review member standing; auto-suspension might be pending."
            ))
        } else emptyList()
    }
}

// ── GROUP RULES ──────────────────────────────────────────────────────────────

data class GroupVerificationContext(
    val group: Group,
    val members: List<Member>,
    val ledger: List<LedgerEntry>
)

class GroupBalanceSyncRule : VerificationRule<GroupVerificationContext> {
    override suspend fun verify(context: GroupVerificationContext): List<VerificationIssue> {
        val displayedBalance = context.group.balance
        val latestEntry = context.ledger.maxByOrNull { it.createdAt ?: "" } ?: context.ledger.firstOrNull()
        val ledgerBalance = latestEntry?.balanceAfter ?: Money.ZERO

        return if ((displayedBalance - ledgerBalance).abs() > Money("0.01") && context.ledger.isNotEmpty()) {
            listOf(VerificationIssue(
                category = "Financial",
                message = "Group balance (${displayedBalance.format()}) is out of sync with the latest ledger entry (${ledgerBalance.format()}).",
                severity = IssueSeverity.CRITICAL,
                suggestedAction = "Perform a manual balance reconciliation."
            ))
        } else emptyList()
    }
}

class GroupMemberCountRule : VerificationRule<GroupVerificationContext> {
    override suspend fun verify(context: GroupVerificationContext): List<VerificationIssue> {
        val displayedCount = context.group.currentMembers
        val actualCount = context.members.size

        return if (displayedCount != actualCount) {
            listOf(VerificationIssue(
                category = "Integrity",
                message = "Group reports $displayedCount members, but only $actualCount records were found.",
                severity = IssueSeverity.WARNING,
                suggestedAction = "Refresh member list to re-sync counters."
            ))
        } else emptyList()
    }
}

class GroupAdminAssignmentRule : VerificationRule<GroupVerificationContext> {
    override suspend fun verify(context: GroupVerificationContext): List<VerificationIssue> {
        return if (context.group.adminUserId == null) {
            listOf(VerificationIssue(
                category = "Security",
                message = "Group has no primary administrator assigned.",
                severity = IssueSeverity.CRITICAL,
                suggestedAction = "Assign an administrator immediately to prevent data loss."
            ))
        } else emptyList()
    }
}

// ── PLATFORM RULES ───────────────────────────────────────────────────────────

data class PlatformVerificationContext(
    val analytics: PlatformAnalytics,
    val groups: List<Group>
)

class PlatformGroupCountRule : VerificationRule<PlatformVerificationContext> {
    override suspend fun verify(context: PlatformVerificationContext): List<VerificationIssue> {
        return if (context.analytics.totalGroups != context.groups.size) {
            listOf(VerificationIssue(
                category = "Analytics",
                message = "Total groups in analytics (${context.analytics.totalGroups}) does not match the loaded group count (${context.groups.size}).",
                severity = IssueSeverity.INFO
            ))
        } else emptyList()
    }
}

class PlatformBalanceParityRule : VerificationRule<PlatformVerificationContext> {
    override suspend fun verify(context: PlatformVerificationContext): List<VerificationIssue> {
        val calculatedTotalBalance = context.groups.fold(Money.ZERO) { acc, next -> acc + next.balance }
        val reportedTotalBalance = context.analytics.totalBalance

        return if ((calculatedTotalBalance - reportedTotalBalance).abs() > Money("1.0")) {
            listOf(VerificationIssue(
                category = "Analytics",
                message = "Global network balance (${reportedTotalBalance.format()}) differs from the sum of group balances (${calculatedTotalBalance.format()}).",
                severity = IssueSeverity.WARNING,
                suggestedAction = "Check for stale analytics cache in the backend."
            ))
        } else emptyList()
    }
}
