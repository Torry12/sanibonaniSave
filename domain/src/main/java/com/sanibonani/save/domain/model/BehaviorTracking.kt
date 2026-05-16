package com.sanibonani.save.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.EncodeDefault

// ─────────────────────────────────────────────────────────────────────────────
// BEHAVIOR SCORE MODELS
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Enum representing fraud risk levels
 */
@Serializable
enum class FraudRiskLevel {
    @SerialName("low")
    LOW,
    @SerialName("medium")
    MEDIUM,
    @SerialName("high")
    HIGH,
    @SerialName("critical")
    CRITICAL
}

/**
 * Enum representing member behavior status
 */
@Serializable
enum class BehaviorStatus {
    @SerialName("excellent")
    EXCELLENT,
    @SerialName("good")
    GOOD,
    @SerialName("fair")
    FAIR,
    @SerialName("poor")
    POOR,
    @SerialName("suspended")
    SUSPENDED
}

/**
 * Comprehensive member behavior tracking and fraud detection model
 * Indexed by member ID number for efficient lookups
 */
@Serializable
@Parcelize
data class MemberBehaviorTrack(
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val id: String? = null,

    // Primary index
    @SerialName("member_id") val memberId: String = "",
    @SerialName("member_id_number") val memberIdNumber: String = "",
    @SerialName("group_id") val groupId: String = "",

    // Payment Metrics
    @SerialName("total_contributions") val totalContributions: Int = 0,
    @SerialName("on_time_contributions") val onTimeContributions: Int = 0,
    @SerialName("late_contributions") val lateContributions: Int = 0,
    @SerialName("overdue_count") val overdueCount: Int = 0,
    @SerialName("missed_contributions") val missedContributions: Int = 0,
    @SerialName("payment_consistency_score") val paymentConsistencyScore: Double = 0.0, // 0-100
    @SerialName("average_days_late") val averageDaysLate: Double = 0.0,

    // Contribution Streaks
    @SerialName("current_payment_streak") val currentPaymentStreak: Int = 0,
    @SerialName("longest_payment_streak") val longestPaymentStreak: Int = 0,
    @SerialName("has_broken_streak_recently") val hasBrokenStreakRecently: Boolean = false,

    // Financial Metrics
    @SerialName("total_amount_contributed") val totalAmountContributed: Double = 0.0,
    @SerialName("total_late_fees_paid") val totalLateFeesPaid: Double = 0.0,
    @SerialName("pending_late_fees") val pendingLateFees: Double = 0.0,
    @SerialName("total_outstanding_amount") val totalOutstandingAmount: Double = 0.0,

    // Loan Metrics
    @SerialName("total_loans_requested") val totalLoansRequested: Int = 0,
    @SerialName("total_loans_approved") val totalLoansApproved: Int = 0,
    @SerialName("total_loans_completed") val totalLoansCompleted: Int = 0,
    @SerialName("active_loans") val activeLoans: Int = 0,
    @SerialName("overdue_loans") val overdueLoans: Int = 0,
    @SerialName("loan_default_count") val loanDefaultCount: Int = 0,
    @SerialName("loan_completion_rate") val loanCompletionRate: Double = 0.0, // 0-100

    // Fraud Indicators
    @SerialName("duplicate_transaction_count") val duplicateTransactionCount: Int = 0,
    @SerialName("suspicious_activity_count") val suspiciousActivityCount: Int = 0,
    @SerialName("unusual_payment_patterns") val unusualPaymentPatterns: Boolean = false,
    @SerialName("multiple_accounts_detected") val multipleAccountsDetected: Boolean = false,
    @SerialName("velocity_check_failed") val velocityCheckFailed: Boolean = false,
    @SerialName("rapid_disbursement_attempts") val rapidDisbursementAttempts: Int = 0,

    // Behavioral Flags
    @SerialName("member_status") val memberStatus: BehaviorStatus = BehaviorStatus.FAIR,
    @SerialName("fraud_risk_level") val fraudRiskLevel: FraudRiskLevel = FraudRiskLevel.LOW,
    @SerialName("fraud_score") val fraudScore: Double = 0.0, // 0-100
    @SerialName("behavior_score") val behaviorScore: Double = 0.0, // 0-100 (overall trust score)

    // Flags and Warnings
    @SerialName("is_flagged_for_review") val isFlaggedForReview: Boolean = false,
    @SerialName("is_suspended") val isSuspended: Boolean = false,
    @SerialName("suspension_reason") val suspensionReason: String? = null,
    @SerialName("review_notes") val reviewNotes: String? = null,

    // Membership Duration
    @SerialName("months_in_group") val monthsInGroup: Int = 0,
    @SerialName("joined_at") val joinedAt: String? = null,
    @SerialName("last_activity_at") val lastActivityAt: String? = null,
    @SerialName("last_contribution_at") val lastContributionAt: String? = null,

    // Admin Notes
    @SerialName("admin_notes") val adminNotes: String? = null,
    @SerialName("last_reviewed_at") val lastReviewedAt: String? = null,
    @SerialName("reviewed_by") val reviewedBy: String? = null,

    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("created_at") val createdAt: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("updated_at") val updatedAt: String? = null
) : Parcelable

/**
 * Detailed fraud detection event for audit trail
 */
@Serializable
@Parcelize
data class FraudDetectionEvent(
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val id: String? = null,

    @SerialName("member_id") val memberId: String = "",
    @SerialName("group_id") val groupId: String = "",
    @SerialName("event_type") val eventType: String = "", // e.g., "duplicate_transaction", "velocity_check", etc.
    @SerialName("severity") val severity: FraudRiskLevel = FraudRiskLevel.LOW,
    @SerialName("details") val details: Map<String, String> = emptyMap(),
    @SerialName("action_taken") val actionTaken: String? = null,
    @SerialName("resolved") val resolved: Boolean = false,

    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("created_at") val createdAt: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("updated_at") val updatedAt: String? = null
) : Parcelable

/**
 * Behavior analytics summary for dashboard/reporting
 */
@Serializable
@Parcelize
data class BehaviorAnalyticsSummary(
    @SerialName("group_id") val groupId: String = "",
    @SerialName("total_members_tracked") val totalMembersTracked: Int = 0,
    @SerialName("excellent_members") val excellentMembers: Int = 0,
    @SerialName("good_members") val goodMembers: Int = 0,
    @SerialName("fair_members") val fairMembers: Int = 0,
    @SerialName("poor_members") val poorMembers: Int = 0,
    @SerialName("suspended_members") val suspendedMembers: Int = 0,
    @SerialName("high_fraud_risk_count") val highFraudRiskCount: Int = 0,
    @SerialName("flagged_members_count") val flaggedMembersCount: Int = 0,
    @SerialName("average_behavior_score") val averageBehaviorScore: Double = 0.0,
    @SerialName("average_fraud_score") val averageFraudScore: Double = 0.0,
    @SerialName("on_time_payment_rate") val onTimePaymentRate: Double = 0.0, // Percentage
    @SerialName("loan_default_rate") val loanDefaultRate: Double = 0.0, // Percentage
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("calculated_at") val calculatedAt: String? = null
) : Parcelable

