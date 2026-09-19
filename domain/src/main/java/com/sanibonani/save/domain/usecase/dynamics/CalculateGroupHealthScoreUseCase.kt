package com.sanibonani.save.domain.usecase.dynamics

import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.*
import com.sanibonani.save.domain.repository.dynamics.HealthScoreRepository
import com.sanibonani.save.domain.repository.equity.ContributionRepository
import kotlinx.coroutines.flow.first
import java.time.Instant
import javax.inject.Inject

/**
 * Calculate composite health score for a group based on actuarial metrics.
 */
class CalculateGroupHealthScoreUseCase @Inject constructor(
    private val groupRepository: GroupRepository,
    private val memberRepository: MemberRepository,
    private val contributionRepository: ContributionRepository,
    private val healthScoreRepository: HealthScoreRepository
) {

    suspend operator fun invoke(groupId: String): Result<GroupHealthScore> = runCatching {
        val group = groupRepository.getGroupById(groupId).getOrThrow()
        val members = memberRepository.getGroupMembers(groupId).first().getOrThrow()
        val contributions = contributionRepository.observeGroupContributions(groupId).first().getOrThrow()

        val currentMemberCount = members.size
        val oneMonthAgo = java.time.LocalDate.now().minusMonths(1)
        val previousMonthMemberCount = members.count { member ->
            runCatching {
                val joined = member.joinedAt?.substringBefore("T")?.let { java.time.LocalDate.parse(it) }
                joined != null && !joined.isAfter(oneMonthAgo)
            }.getOrDefault(false)
        }.coerceAtLeast(1)

        val solvencyRatio = calculateSolvencyRatio(group, contributions)
        val lossRatio = calculateLossRatio(contributions)
        val reserveAdequacy = calculateReserveAdequacy(group, contributions)
        val fundingRatio = calculateFundingRatio(group, contributions)
        val memberRetention = calculateMemberRetention(currentMemberCount, previousMonthMemberCount)

        val solvencyScore = normalizeSolvencyToScore(solvencyRatio)
        val lossScore = normalizeLossRatioToScore(lossRatio)
        val reserveScore = normalizeReserveAdequacyToScore(reserveAdequacy)
        val fundingScore = normalizeFundingRatioToScore(fundingRatio)
        val retentionScore = normalizeRetentionToScore(memberRetention)

        val totalScore = (solvencyScore + lossScore + reserveScore + fundingScore + retentionScore).toInt()

        val zone = when {
            totalScore < 40 -> RiskZone.RED
            totalScore < 70 -> RiskZone.YELLOW
            else -> RiskZone.GREEN
        }

        val recommendations = generateRecommendations(
            solvencyRatio, lossRatio, reserveAdequacy, fundingRatio, memberRetention, zone
        )

        val score = GroupHealthScore(
            groupId = groupId,
            overallScore = totalScore,
            zone = zone,
            components = mapOf(
                "Solvency" to solvencyScore.toInt(),
                "Loss" to lossScore.toInt(),
                "Reserve" to reserveScore.toInt(),
                "Funding" to fundingScore.toInt(),
                "Retention" to retentionScore.toInt()
            ),
            recommendations = recommendations,
            generatedAt = Instant.now().toString(),
            expiresAt = java.time.OffsetDateTime.now().plusDays(7).toString()
        )

        healthScoreRepository.saveHealthScore(score)
        score
    }

    private fun calculateSolvencyRatio(group: Group, contributions: List<Contribution>): Double {
        val avgMonthly = contributions.filter { it.type == "contribution" }.groupBy { it.dueDate.substringBeforeLast("-") }.values.map { it.fold(Money.ZERO) { acc, c -> acc + c.amount }.toDouble() }.average().takeIf { !it.isNaN() } ?: 1.0
        return if (avgMonthly > 0) group.balance.toDouble() / avgMonthly else 0.0
    }

    private fun calculateLossRatio(contributions: List<Contribution>): Double {
        val totalClaims = contributions.filter { it.type in setOf("claim", "payout") }.fold(Money.ZERO) { acc, c -> acc + c.amount }
        val totalIncoming = contributions.filter { it.type == "contribution" }.fold(Money.ZERO) { acc, c -> acc + c.amount }
        return if (totalIncoming.isPositive) totalClaims.toDouble() / totalIncoming.toDouble() else 0.0
    }

    private fun calculateReserveAdequacy(group: Group, contributions: List<Contribution>): Double {
        val avgMonthly = contributions.filter { it.type == "contribution" }.groupBy { it.dueDate.substringBeforeLast("-") }.values.map { it.fold(Money.ZERO) { acc, c -> acc + c.amount }.toDouble() }.average().takeIf { !it.isNaN() } ?: 1.0
        return if (avgMonthly > 0) group.balance.toDouble() / (avgMonthly * 6) else 0.0
    }

    private fun calculateFundingRatio(group: Group, contributions: List<Contribution>): Double {
        val estimatedLiability = contributions.filter { it.type in setOf("claim", "payout") }.fold(Money.ZERO) { acc, c -> acc + c.amount }.max(group.monthlyContribution)
        return if (estimatedLiability.isPositive) group.balance.toDouble() / estimatedLiability.toDouble() else 1.5
    }

    private fun calculateMemberRetention(current: Int, previous: Int): Double = if (previous > 0) current.toDouble() / previous else 1.0

    private fun normalizeSolvencyToScore(ratio: Double): Double = (ratio.coerceIn(0.5, 1.5) - 0.5) / 1.0 * 25.0
    private fun normalizeLossRatioToScore(ratio: Double): Double = (1 - ratio.coerceIn(0.0, 0.7) / 0.7) * 25.0
    private fun normalizeReserveAdequacyToScore(ratio: Double): Double = (ratio.coerceIn(0.25, 1.5) - 0.25) / 1.25 * 20.0
    private fun normalizeFundingRatioToScore(ratio: Double): Double = (ratio.coerceIn(0.8, 1.5) - 0.8) / 0.7 * 20.0
    private fun normalizeRetentionToScore(retention: Double): Double = (retention.coerceIn(0.8, 0.95) - 0.8) / 0.15 * 10.0

    private fun generateRecommendations(solvency: Double, loss: Double, reserve: Double, funding: Double, retention: Double, zone: RiskZone): List<String> {
        val recs = mutableListOf<String>()
        if (solvency < 0.7) recs.add("🔴 Increase contributions to build reserves.")
        if (loss > 0.5) recs.add("🔴 High loss ratio detected.")
        if (reserve < 0.5) recs.add("🟡 Low reserves.")
        if (zone == RiskZone.GREEN) recs.add("✅ Group is in excellent standing.")
        return recs.take(5)
    }
}
