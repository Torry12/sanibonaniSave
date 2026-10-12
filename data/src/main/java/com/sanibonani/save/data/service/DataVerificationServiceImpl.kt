package com.sanibonani.save.data.service

import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.service.*
import com.sanibonani.save.domain.utils.PaymentCalculator
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

@Singleton
class DataVerificationServiceImpl @Inject constructor(
    private val paymentCalculator: PaymentCalculator
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
    ): VerificationResult = coroutineScope {
        val context = MemberVerificationContext(member, group, contributions)
        val issues = memberRules.map { async { it.verify(context) } }.awaitAll().flatten()
        VerificationResult(issues.isEmpty(), issues)
    }

    override suspend fun verifyGroupAdminData(
        group: Group,
        members: List<Member>,
        ledger: List<LedgerEntry>
    ): VerificationResult = coroutineScope {
        val context = GroupVerificationContext(group, members, ledger)
        val issues = groupRules.map { async { it.verify(context) } }.awaitAll().flatten()
        VerificationResult(issues.isEmpty(), issues)
    }

    override suspend fun verifyPlatformAdminData(
        analytics: PlatformAnalytics,
        groups: List<Group>
    ): VerificationResult = coroutineScope {
        val context = PlatformVerificationContext(analytics, groups)
        val issues = platformRules.map { async { it.verify(context) } }.awaitAll().flatten()
        VerificationResult(issues.isEmpty(), issues)
    }
}
