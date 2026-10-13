package com.sanibonani.save.data.utils

import com.sanibonani.save.domain.model.*
import kotlin.math.max
import kotlin.math.min

/**
 * Utility object for behavior tracking calculations and fraud detection algorithms
 */
object BehaviorScoringUtils {

    // ─────────────────────────────────────────────────────────────────────────
    // BEHAVIOR SCORE CALCULATION (0-100)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Calculate overall behavior score (0-100) based on payment history and compliance
     *
     * Scoring breakdown:
     * - Payment Consistency (40%): On-time payment rate
     * - Loan Performance (30%): Loan completion and repayment rate
     * - Current Standing (20%): Current streak and absence of overdue items
     * - Duration & Engagement (10%): Tenure in group and recent activity
     */
    fun calculateBehaviorScore(track: MemberBehaviorTrack): Double {
        val paymentConsistencyScore = calculatePaymentConsistencyScore(track)
        val loanPerformanceScore = calculateLoanPerformanceScore(track)
        val currentStandingScore = calculateCurrentStandingScore(track)
        val durationScore = calculateDurationScore(track)

        // Weighted average
        return (paymentConsistencyScore * 0.40 +
                loanPerformanceScore * 0.30 +
                currentStandingScore * 0.20 +
                durationScore * 0.10).coerceIn(0.0, 100.0)
    }

    private fun calculatePaymentConsistencyScore(track: MemberBehaviorTrack): Double {
        if (track.totalContributions == 0) return 50.0 // Default for new members

        val onTimeRate = (track.onTimeContributions.toDouble() / track.totalContributions) * 100
        val missedPenalty = min((track.missedContributions * 10).toDouble(), 30.0)

        return max(0.0, onTimeRate - missedPenalty)
    }

    private fun calculateLoanPerformanceScore(track: MemberBehaviorTrack): Double {
        if (track.totalLoansRequested == 0) return 75.0 // Neutral score if no loans

        val completionRate = track.loanCompletionRate
        val defaultPenalty = track.loanDefaultCount * 20.0

        return max(0.0, completionRate - defaultPenalty)
    }

    private fun calculateCurrentStandingScore(track: MemberBehaviorTrack): Double {
        var score = 100.0

        // Penalty for overdue items
        score -= min((track.overdueCount * 5.0), 30.0)

        // Penalty for suspended loans
        score -= min((track.overdueLoans * 10.0), 20.0)

        // Reward for current streak
        score += min((track.currentPaymentStreak * 2.0), 10.0)

        // Penalty for broken streak recently
        if (track.hasBrokenStreakRecently) {
            score -= 10.0
        }

        return score.coerceIn(0.0, 100.0)
    }

    private fun calculateDurationScore(track: MemberBehaviorTrack): Double {
        // Score based on months in group
        return when {
            track.monthsInGroup >= 24 -> 100.0
            track.monthsInGroup >= 12 -> 80.0
            track.monthsInGroup >= 6 -> 60.0
            track.monthsInGroup >= 3 -> 40.0
            else -> 20.0
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // FRAUD SCORE CALCULATION (0-100)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Calculate fraud risk score (0-100) based on suspicious activities and patterns
     *
     * Risk indicators:
     * - Duplicate/Suspicious Transactions (30%): Duplicate entries, failed velocity checks
     * - Payment Pattern Anomalies (25%): Unusual patterns, rapid disbursement attempts
     * - Account Issues (25%): Multiple accounts detected, rapid account changes
     * - Historical Flags (20%): Previous defaults, suspension reason
     */
    fun calculateFraudScore(track: MemberBehaviorTrack): Double {
        val transactionRiskScore = calculateTransactionRiskScore(track)
        val patternRiskScore = calculatePatternRiskScore(track)
        val accountRiskScore = calculateAccountRiskScore(track)
        val historicalRiskScore = calculateHistoricalRiskScore(track)

        return (transactionRiskScore * 0.30 +
                patternRiskScore * 0.25 +
                accountRiskScore * 0.25 +
                historicalRiskScore * 0.20).coerceIn(0.0, 100.0)
    }

    private fun calculateTransactionRiskScore(track: MemberBehaviorTrack): Double {
        var score = 0.0

        // Duplicate transaction check
        score += min((track.duplicateTransactionCount * 15.0), 50.0)

        // Velocity check failure
        if (track.velocityCheckFailed) {
            score += 30.0
        }

        // Suspicious activity count
        score += min((track.suspiciousActivityCount * 10.0), 20.0)

        return score.coerceIn(0.0, 100.0)
    }

    private fun calculatePatternRiskScore(track: MemberBehaviorTrack): Double {
        var score = 0.0

        // Unusual payment patterns
        if (track.unusualPaymentPatterns) {
            score += 40.0
        }

        // Rapid disbursement attempts
        score += min((track.rapidDisbursementAttempts * 12.0), 40.0)

        return score.coerceIn(0.0, 100.0)
    }

    private fun calculateAccountRiskScore(track: MemberBehaviorTrack): Double {
        var score = 0.0

        // Multiple accounts detected
        if (track.multipleAccountsDetected) {
            score += 50.0
        }

        return score.coerceIn(0.0, 100.0)
    }

    private fun calculateHistoricalRiskScore(track: MemberBehaviorTrack): Double {
        var score = 0.0

        // Loan defaults
        score += min((track.loanDefaultCount * 20.0), 50.0)

        // Current suspension status
        if (track.isSuspended) {
            score += 40.0
        }

        // Already flagged for review
        if (track.isFlaggedForReview) {
            score += 15.0
        }

        return score.coerceIn(0.0, 100.0)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // FRAUD RISK LEVEL DETERMINATION
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Determine fraud risk level based on score and other indicators
     */
    fun determineFraudRiskLevel(fraudScore: Double, track: MemberBehaviorTrack): FraudRiskLevel {
        return when {
            // Critical indicators
            track.multipleAccountsDetected && track.velocityCheckFailed -> FraudRiskLevel.CRITICAL
            track.isSuspended && fraudScore >= 80 -> FraudRiskLevel.CRITICAL
            fraudScore >= 85 -> FraudRiskLevel.CRITICAL

            // High indicators
            track.unusualPaymentPatterns && track.duplicateTransactionCount > 3 -> FraudRiskLevel.HIGH
            fraudScore >= 65 -> FraudRiskLevel.HIGH
            track.multipleAccountsDetected -> FraudRiskLevel.HIGH

            // Medium indicators
            fraudScore >= 45 -> FraudRiskLevel.MEDIUM
            track.unusualPaymentPatterns -> FraudRiskLevel.MEDIUM
            track.velocityCheckFailed -> FraudRiskLevel.MEDIUM

            // Low risk
            else -> FraudRiskLevel.LOW
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // BEHAVIOR STATUS DETERMINATION
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Determine member behavior status based on scores and metrics
     */
    fun determineBehaviorStatus(behaviorScore: Double, fraudScore: Double, track: MemberBehaviorTrack): BehaviorStatus {
        return when {
            track.isSuspended -> BehaviorStatus.SUSPENDED
            behaviorScore >= 85 && fraudScore <= 20 -> BehaviorStatus.EXCELLENT
            behaviorScore >= 70 && fraudScore <= 35 -> BehaviorStatus.GOOD
            behaviorScore >= 50 && fraudScore <= 50 -> BehaviorStatus.FAIR
            else -> BehaviorStatus.POOR
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // FRAUD DETECTION ALGORITHMS
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Detect duplicate transactions based on amount, date, and member
     * Returns true if potential duplicate is found
     */
    fun detectDuplicateTransaction(
        transactions: List<Map<String, Any>>,
        newTransaction: Map<String, Any>,
        timeWindowMinutes: Int = 60
    ): Boolean {
        val newAmount = (newTransaction["amount"] as? Number)?.toDouble() ?: return false
        val newTime = newTransaction["timestamp"] as? Long ?: return false
        val newMemberId = newTransaction["member_id"] as? String ?: return false

        return transactions.any { tx ->
            val txAmount = (tx["amount"] as? Number)?.toDouble() ?: return@any false
            val txTime = tx["timestamp"] as? Long ?: return@any false
            val txMemberId = tx["member_id"] as? String ?: return@any false

            txMemberId == newMemberId &&
            txAmount == newAmount &&
            (Math.abs(newTime - txTime) <= (timeWindowMinutes * 60 * 1000)) // within time window
        }
    }

    /**
     * Detect velocity spike - multiple transactions in short time window
     */
    fun detectVelocitySpike(
        transactions: List<Map<String, Any>>,
        memberId: String,
        maxTransactionsInWindow: Int = 5,
        timeWindowMinutes: Int = 30
    ): Boolean {
        val memberTransactions = transactions.filter { it["member_id"] == memberId }
        if (memberTransactions.isEmpty()) return false

        val mostRecentTime = (memberTransactions.maxOfOrNull { it["timestamp"] as? Long ?: 0L }) ?: return false
        val windowStart = mostRecentTime - (timeWindowMinutes * 60 * 1000)

        val transactionsInWindow = memberTransactions.count {
            val txTime = it["timestamp"] as? Long ?: 0L
            txTime >= windowStart
        }

        return transactionsInWindow > maxTransactionsInWindow
    }

    /**
     * Detect unusual payment patterns (e.g., sudden large payments after consistent small payments)
     */
    fun detectUnusualPaymentPattern(
        contributions: List<Map<String, Any>>,
        memberId: String
    ): Boolean {
        val memberContributions = contributions
            .filter { it["member_id"] == memberId }
            .sortedByDescending { it["created_at"] as? String }
            .take(10)

        if (memberContributions.size < 5) return false

        val amounts = memberContributions.mapNotNull { (it["amount"] as? Number)?.toDouble() }
        if (amounts.size < 5) return false

        val recentAmount = amounts.first()
        val averagePrevious = amounts.drop(1).average()
        if (averagePrevious <= 0.0) return false

        // Flag if recent amount differs by more than 200% from average
        val percentageDifference = Math.abs(recentAmount - averagePrevious) / averagePrevious * 100
        return percentageDifference > 200
    }

    /**
     * Calculate payment consistency score (0-100) based on on-time rate
     */
    fun calculatePaymentConsistency(
        totalContributions: Int,
        onTimeContributions: Int
    ): Double {
        if (totalContributions == 0) return 50.0
        return ((onTimeContributions.toDouble() / totalContributions) * 100).coerceIn(0.0, 100.0)
    }

    /**
     * Calculate loan completion rate (0-100)
     */
    fun calculateLoanCompletionRate(
        totalLoansCompleted: Int,
        totalLoansRequested: Int
    ): Double {
        if (totalLoansRequested == 0) return 0.0
        return ((totalLoansCompleted.toDouble() / totalLoansRequested) * 100).coerceIn(0.0, 100.0)
    }

    /**
     * Should member be flagged for review?
     */
    fun shouldFlagForReview(track: MemberBehaviorTrack): Boolean {
        return track.fraudScore >= 50 ||
               track.loanDefaultCount >= 2 ||
               (track.overdueCount >= 3 && track.behaviorScore < 50) ||
               track.multipleAccountsDetected ||
               (track.unusualPaymentPatterns && track.behaviorScore < 60)
    }

    /**
     * Should member be suspended?
     */
    fun shouldSuspend(track: MemberBehaviorTrack): Boolean {
        if (track.isSuspended) return false
        return track.fraudScore >= 80 ||
               track.loanDefaultCount >= 3 ||
               (track.overdueCount >= 5 && track.behaviorScore < 30) ||
               track.multipleAccountsDetected && track.velocityCheckFailed
    }
}

