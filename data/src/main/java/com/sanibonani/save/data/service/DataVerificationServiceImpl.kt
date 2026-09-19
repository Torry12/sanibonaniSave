package com.sanibonani.save.data.service

import com.sanibonani.save.data.utils.PaymentCalculator
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.service.DataVerificationService
import com.sanibonani.save.domain.utils.roundMoneyToTwoDecimals
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataVerificationServiceImpl @Inject constructor() : DataVerificationService {

    override fun verifyMemberData(
        member: Member,
        group: Group,
        contributions: List<Contribution>
    ): VerificationResult {
        val issues = mutableListOf<VerificationIssue>()

        // 1. Verify Total Paid consistency
        val calculatedTotalPaid = contributions
            .filter { it.status == ContributionStatus.PAID || it.status == ContributionStatus.PARTIAL }
            .sumOf { it.amount }
            .roundMoneyToTwoDecimals()
        
        val displayedTotalPaid = (member.totalPaid ?: 0.0).roundMoneyToTwoDecimals()

        if (Math.abs(calculatedTotalPaid - displayedTotalPaid) > 0.01) {
            issues.add(VerificationIssue(
                category = "Financial",
                message = "Displayed 'Total Paid' (R$displayedTotalPaid) does not match sum of transactions (R$calculatedTotalPaid).",
                severity = IssueSeverity.WARNING,
                suggestedAction = "Refresh data or contact support if the discrepancy persists."
            ))
        }

        // 2. Verify Status consistency
        if (group.probationMonths == 0 && member.status == MemberStatus.PROBATION) {
            issues.add(VerificationIssue(
                category = "Status",
                message = "Member is in 'Probation' status but the group has 0 probation months configured.",
                severity = IssueSeverity.INFO,
                suggestedAction = "Admin should update member status to 'Active'."
            ))
        }

        // 3. Verify Calculation accuracy
        val calculation = PaymentCalculator.calculateStatus(group, member, contributions)
        // We check if the 'isOverdue' logic in UI matches our current check
        // This is more about verifying the logic itself, but here we can check if data supports the flag
        if (calculation.isOverdue && member.status == MemberStatus.ACTIVE && calculation.shortfall > (group.monthlyContribution * 3)) {
             issues.add(VerificationIssue(
                category = "Risk",
                message = "Member is 'Active' but has a significant shortfall (R${calculation.shortfall}).",
                severity = IssueSeverity.WARNING,
                suggestedAction = "Review member standing; auto-suspension might be pending."
            ))
        }

        return VerificationResult(issues.isEmpty(), issues)
    }

    override fun verifyGroupAdminData(
        group: Group,
        members: List<Member>,
        ledger: List<LedgerEntry>
    ): VerificationResult {
        val issues = mutableListOf<VerificationIssue>()

        // 1. Verify Balance vs Ledger
        val displayedBalance = group.balance.roundMoneyToTwoDecimals()
        val ledgerBalance = ledger.lastOrNull()?.balanceAfter?.roundMoneyToTwoDecimals() ?: 0.0

        if (Math.abs(displayedBalance - ledgerBalance) > 0.01 && ledger.isNotEmpty()) {
            issues.add(VerificationIssue(
                category = "Financial",
                message = "Group balance (R$displayedBalance) is out of sync with the latest ledger entry (R$ledgerBalance).",
                severity = IssueSeverity.CRITICAL,
                suggestedAction = "Perform a manual balance reconciliation."
            ))
        }

        // 2. Verify Member Count
        val displayedCount = group.currentMembers
        val actualCount = members.size

        if (displayedCount != actualCount) {
            issues.add(VerificationIssue(
                category = "Integrity",
                message = "Group reports $displayedCount members, but only $actualCount records were found.",
                severity = IssueSeverity.WARNING,
                suggestedAction = "Refresh member list to re-sync counters."
            ))
        }

        // 3. Verify Admin Authorization
        if (group.adminUserId == null) {
            issues.add(VerificationIssue(
                category = "Security",
                message = "Group has no primary administrator assigned.",
                severity = IssueSeverity.CRITICAL,
                suggestedAction = "Assign an administrator immediately to prevent data loss."
            ))
        }

        return VerificationResult(issues.isEmpty(), issues)
    }

    override fun verifyPlatformAdminData(
        analytics: PlatformAnalytics,
        groups: List<Group>
    ): VerificationResult {
        val issues = mutableListOf<VerificationIssue>()

        // 1. Global Group Count
        if (analytics.totalGroups != groups.size) {
            issues.add(VerificationIssue(
                category = "Analytics",
                message = "Total groups in analytics (${analytics.totalGroups}) does not match the loaded group count (${groups.size}).",
                severity = IssueSeverity.INFO
            ))
        }

        // 2. Global Balance Sum
        val calculatedTotalBalance = groups.sumOf { it.balance }.roundMoneyToTwoDecimals()
        val reportedTotalBalance = analytics.totalBalance.roundMoneyToTwoDecimals()

        if (Math.abs(calculatedTotalBalance - reportedTotalBalance) > 1.0) { // Larger threshold for global totals
            issues.add(VerificationIssue(
                category = "Analytics",
                message = "Global network balance (R$reportedTotalBalance) differs from the sum of group balances (R$calculatedTotalBalance).",
                severity = IssueSeverity.WARNING,
                suggestedAction = "Check for stale analytics cache in the backend."
            ))
        }

        return VerificationResult(issues.isEmpty(), issues)
    }
}
