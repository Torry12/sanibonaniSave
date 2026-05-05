package com.sanibonani.save.domain.usecase

import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.*
import kotlinx.datetime.Clock
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * Calculate composite health score for a group based on actuarial metrics.
 *
 * Score = 25% * solvency + 25% * loss_ratio + 20% * reserve + 20% * funding + 10% * retention
 *
 * All components normalized to 0-100 scale before weighted averaging.
 */
class CalculateGroupHealthScoreUseCase @Inject constructor(
    private val groupRepository: GroupRepository,
    private val memberRepository: MemberRepository
) {

    suspend operator fun invoke(groupId: String): Result<GroupHealthScore> = runCatching {
        val group = groupRepository.getGroupById(groupId).getOrThrow()
        val membersResult: Result<List<Member>> = memberRepository.getGroupMembers(groupId).first()
        val members = membersResult.getOrThrow()
        val contributionsResult: Result<List<Contribution>> = memberRepository.getGroupContributions(groupId).first()
        val contributions = contributionsResult.getOrThrow()
        val currentMemberCount = members.size
        val previousMonthMemberCount = currentMemberCount

        // Calculate raw metrics
        val solvencyRatio = calculateSolvencyRatio(group, contributions)
        val lossRatio = calculateLossRatio(contributions)
        val reserveAdequacy = calculateReserveAdequacy(group, contributions)
        val fundingRatio = calculateFundingRatio(group, contributions)
        val memberRetention = calculateMemberRetention(currentMemberCount, previousMonthMemberCount)

        // Normalize to 0-100 scale
        val solvencyScore = normalizeSolvencyToScore(solvencyRatio)       // 0-25 points
        val lossScore = normalizeLossRatioToScore(lossRatio)             // 0-25 points
        val reserveScore = normalizeReserveAdequacyToScore(reserveAdequacy) // 0-20 points
        val fundingScore = normalizeFundingRatioToScore(fundingRatio)    // 0-20 points
        val retentionScore = normalizeRetentionToScore(memberRetention) // 0-10 points

        // Calculate weighted composite
        val totalScore = (solvencyScore + lossScore + reserveScore + fundingScore + retentionScore).toInt()

        // Determine risk zone
        val zone = when {
            totalScore < 40 -> RiskZone.RED
            totalScore < 70 -> RiskZone.YELLOW
            else -> RiskZone.GREEN
        }

        // Generate recommendations based on weak components
        val recommendations = generateRecommendations(
            solvencyRatio, lossRatio, reserveAdequacy, fundingRatio, memberRetention, zone
        )

        // Create timestamp
        val now = Clock.System.now().toString()
        val sevenDaysFromNow = LocalDateTime.now().plusDays(7).toString()

        GroupHealthScore(
            groupId = groupId,
            overallScore = totalScore,
            zone = zone,
            components = mapOf(
                "Solvency Ratio" to solvencyScore.toInt(),
                "Loss Ratio" to lossScore.toInt(),
                "Reserve Adequacy" to reserveScore.toInt(),
                "Funding Ratio" to fundingScore.toInt(),
                "Member Retention" to retentionScore.toInt()
            ),
            recommendations = recommendations,
            generatedAt = now,
            expiresAt = sevenDaysFromNow
        )
    }

    // ===== Component Calculations =====

    private fun calculateSolvencyRatio(group: Group, contributions: List<Contribution>): Double {
        val avgMonthlyContribution = contributions
            .groupBy { it.dueDate.substringBefore("-") }
            .values
            .mapNotNull { list -> list.sumOf { c -> c.amount }.takeIf { it > 0 } }
            .average()
            .takeIf { !it.isNaN() } ?: 1.0

        return if (avgMonthlyContribution > 0) group.balance / avgMonthlyContribution else 0.0
    }

    private fun calculateLossRatio(contributions: List<Contribution>): Double {
        val claimTypes = setOf("claim", "payout")
        val incomingTypes = setOf("contribution", "joining_fee", "late_fee", "registration_contribution")
        val totalClaims = contributions.filter { it.type in claimTypes }.sumOf { it.amount }
        val totalIncoming = contributions.filter { it.type in incomingTypes }.sumOf { it.amount }
        return if (totalIncoming > 0) totalClaims / totalIncoming else 0.0
    }

    private fun calculateReserveAdequacy(group: Group, contributions: List<Contribution>): Double {
        val avgMonthlyContribution = contributions
            .groupBy { it.dueDate.substringBefore("-") }
            .values
            .mapNotNull { list -> list.sumOf { c -> c.amount }.takeIf { it > 0 } }
            .average()
            .takeIf { !it.isNaN() } ?: 1.0

        val sixMonthsExpected = avgMonthlyContribution * 6
        return if (sixMonthsExpected > 0) group.balance / sixMonthsExpected else 0.0
    }

    private fun calculateFundingRatio(group: Group, contributions: List<Contribution>): Double {
        // Estimate liabilities from recent claims where explicit liabilities are unavailable.
        val estimatedLiability = contributions
            .filter { it.type == "claim" || it.type == "payout" }
            .sumOf { it.amount }
            .coerceAtLeast(group.monthlyContribution)
        return if (estimatedLiability > 0) group.balance / estimatedLiability else 1.5
    }

    private fun calculateMemberRetention(currentMembers: Int, previousMonthMembers: Int): Double {
        return if (previousMonthMembers > 0) currentMembers.toDouble() / previousMonthMembers else 1.0
    }

    // ===== Normalization Functions (Convert Raw Metrics to 0-100 Scale) =====

    private fun normalizeSolvencyToScore(ratio: Double): Double {
        // Ideal is 1.0 (100%). Score peaks at 1.5 (150%).
        // <0.5: 0 pts, 0.5-1.5: linear scale, >1.5: 25 pts
        return when {
            ratio < 0.5 -> 0.0
            ratio > 1.5 -> 25.0
            else -> ((ratio - 0.5) / 1.0 * 25)
        }
    }

    private fun normalizeLossRatioToScore(ratio: Double): Double {
        // Ideal is 0% (no claims). Problematic above 70%.
        // >0.7: 0 pts, 0-0.7: inverse scale, 0%: 25 pts
        return when {
            ratio > 0.7 -> 0.0
            ratio < 0.0 -> 25.0
            else -> ((1 - ratio / 0.7) * 25)
        }
    }

    private fun normalizeReserveAdequacyToScore(ratio: Double): Double {
        // Ideal is 1.0 (one year of contributions). Problematic below 25%.
        // <0.25: 0 pts, 0.25-1.5: scale, >1.5: 20 pts
        return when {
            ratio < 0.25 -> 0.0
            ratio > 1.5 -> 20.0
            else -> ((ratio - 0.25) / 1.25 * 20)
        }
    }

    private fun normalizeFundingRatioToScore(ratio: Double): Double {
        // Ideal is 1.2 (120% funded). Problematic below 80%.
        return when {
            ratio < 0.8 -> 0.0
            ratio > 1.5 -> 20.0
            else -> ((ratio - 0.8) / 0.7 * 20)
        }
    }

    private fun normalizeRetentionToScore(retention: Double): Double {
        // Ideal > 95%. Problematic below 80%.
        return when {
            retention < 0.8 -> 0.0
            retention > 0.95 -> 10.0
            else -> ((retention - 0.8) / 0.15 * 10)
        }
    }

    // ===== Recommendation Generation =====

    private fun generateRecommendations(
        solvency: Double,
        lossRatio: Double,
        reserve: Double,
        funding: Double,
        retention: Double,
        zone: RiskZone
    ): List<String> {
        val recommendations = mutableListOf<String>()

        if (solvency < 0.7) {
            recommendations.add("🔴 Increase monthly contribution to build reserves (currently ${(solvency * 100).toInt()}% of target)")
        }
        if (lossRatio > 0.5) {
            recommendations.add("🔴 Claims are ${(lossRatio * 100).toInt()}% of contributions. Consider eligibility restrictions.")
        }
        if (reserve < 0.5) {
            recommendations.add("🟡 Reserves are low. Consider suspending payouts until balance reaches 6 months income.")
        }
        if (retention < 0.9) {
            recommendations.add("🟡 Member retention is ${(retention * 100).toInt()}%. Investigate membership challenges.")
        }
        if (funding < 1.0) {
            recommendations.add("🟡 Group is underfunded. Plan for contribution increases in next quarter.")
        }
        if (zone == RiskZone.GREEN) {
            recommendations.add("✅ Your group is in excellent standing. Consider expanding offerings (loans, investments).")
        }

        return recommendations.take(5)  // Limit to 5 recommendations
    }
}

