package com.sanibonani.save.data.service

import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.system.SyncRepository
import com.sanibonani.save.domain.service.*
import com.sanibonani.save.domain.utils.PaymentCalculator
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

@Singleton
class DataVerificationServiceImpl @Inject constructor(
    private val paymentCalculator: PaymentCalculator,
    private val syncRepo: SyncRepository
) : DataVerificationService {

    private val memberRules = listOf(
        MemberTotalPaidSyncRule(),
        MemberProbationConsistencyRule(),
        MemberRiskShortfallRule(paymentCalculator)
    )

    private val groupRules = listOf(
        GroupBalanceSyncRule(),
        GroupMemberCountRule(),
        GroupAdminAssignmentRule()
    )

    private val platformRules = listOf(
        PlatformGroupCountRule(),
        PlatformBalanceParityRule()
    )

    override suspend fun verifyMemberData(
        member: Member,
        group: Group,
        contributions: List<Contribution>
    ): VerificationResult = unverifiable(listOfNotNull(group.id)) ?: coroutineScope {
        val context = MemberVerificationContext(member, group, contributions)
        val issues = memberRules.map { async { it.verify(context) } }.awaitAll().flatten()
        VerificationResult(issues.isEmpty(), issues)
    }

    override suspend fun verifyGroupAdminData(
        group: Group,
        members: List<Member>,
        ledger: List<LedgerEntry>
    ): VerificationResult = unverifiable(listOfNotNull(group.id)) ?: coroutineScope {
        val context = GroupVerificationContext(group, members, ledger)
        val issues = groupRules.map { async { it.verify(context) } }.awaitAll().flatten()
        VerificationResult(issues.isEmpty(), issues)
    }

    override suspend fun verifyPlatformAdminData(
        analytics: PlatformAnalytics,
        groups: List<Group>
    ): VerificationResult = unverifiable(groups.mapNotNull { it.id }) ?: coroutineScope {
        val context = PlatformVerificationContext(analytics, groups)
        val issues = platformRules.map { async { it.verify(context) } }.awaitAll().flatten()
        VerificationResult(issues.isEmpty(), issues)
    }

    private fun outstandingMisses(groupId: String): Set<String> =
        syncRepo.staleGroups.value[groupId].orEmpty()

    /**
     * What the sync layer could not bring current for any of [groupIds], as an audit finding.
     *
     * Comparing a cached balance with cached rows is only an audit if those rows arrived. While a
     * pull is outstanding the app cannot separate drift from an outage, so it reports the gap
     * instead of letting the dashboard show a clean book it never saw. The ids are named because a
     * platform audit spans groups the operator has no other view of.
     */
    private fun unverifiable(groupIds: List<String>): VerificationResult? {
        val gaps = groupIds.mapNotNull { id ->
            outstandingMisses(id).takeIf { it.isNotEmpty() }?.let { id to it }
        }
        if (gaps.isEmpty()) return null
        return VerificationResult(
            isValid = false,
            issues = listOf(
                VerificationIssue(
                    category = "Sync",
                    message = "These books could not be checked: " +
                        gaps.joinToString { (id, misses) -> "$id (${misses.joinToString()})" } +
                        " did not reach the app. What is on screen is the last data it could verify.",
                    severity = IssueSeverity.WARNING
                )
            )
        )
    }
}
