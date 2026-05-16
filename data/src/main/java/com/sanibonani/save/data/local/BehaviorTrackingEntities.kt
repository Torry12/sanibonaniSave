package com.sanibonani.save.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.ColumnInfo
import androidx.room.PrimaryKey

// ─────────────────────────────────────────────────────────────────────────────
// BEHAVIOR TRACKING ENTITIES
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Room entity for member behavior tracking
 * Indexed by member_id and member_id_number for efficient queries
 */
@Entity(
    tableName = "member_behavior_track",
    indices = [
        Index("member_id", unique = true),
        Index("member_id_number", unique = false),
        Index("group_id"),
        Index("fraud_risk_level"),
        Index("is_flagged_for_review")
    ]
)
data class MemberBehaviorTrackEntity(
    @PrimaryKey val id: String,
    
    // Primary indices
    @ColumnInfo(name = "member_id") val memberId: String,
    @ColumnInfo(name = "member_id_number") val memberIdNumber: String,
    @ColumnInfo(name = "group_id") val groupId: String,
    
    // Payment Metrics
    @ColumnInfo(name = "total_contributions") val totalContributions: Int = 0,
    @ColumnInfo(name = "on_time_contributions") val onTimeContributions: Int = 0,
    @ColumnInfo(name = "late_contributions") val lateContributions: Int = 0,
    @ColumnInfo(name = "overdue_count") val overdueCount: Int = 0,
    @ColumnInfo(name = "missed_contributions") val missedContributions: Int = 0,
    @ColumnInfo(name = "payment_consistency_score") val paymentConsistencyScore: Double = 0.0,
    @ColumnInfo(name = "average_days_late") val averageDaysLate: Double = 0.0,
    
    // Contribution Streaks
    @ColumnInfo(name = "current_payment_streak") val currentPaymentStreak: Int = 0,
    @ColumnInfo(name = "longest_payment_streak") val longestPaymentStreak: Int = 0,
    @ColumnInfo(name = "has_broken_streak_recently") val hasBrokenStreakRecently: Boolean = false,
    
    // Financial Metrics
    @ColumnInfo(name = "total_amount_contributed") val totalAmountContributed: Double = 0.0,
    @ColumnInfo(name = "total_late_fees_paid") val totalLateFeesPaid: Double = 0.0,
    @ColumnInfo(name = "pending_late_fees") val pendingLateFees: Double = 0.0,
    @ColumnInfo(name = "total_outstanding_amount") val totalOutstandingAmount: Double = 0.0,
    
    // Loan Metrics
    @ColumnInfo(name = "total_loans_requested") val totalLoansRequested: Int = 0,
    @ColumnInfo(name = "total_loans_approved") val totalLoansApproved: Int = 0,
    @ColumnInfo(name = "total_loans_completed") val totalLoansCompleted: Int = 0,
    @ColumnInfo(name = "active_loans") val activeLoans: Int = 0,
    @ColumnInfo(name = "overdue_loans") val overdueLoans: Int = 0,
    @ColumnInfo(name = "loan_default_count") val loanDefaultCount: Int = 0,
    @ColumnInfo(name = "loan_completion_rate") val loanCompletionRate: Double = 0.0,
    
    // Fraud Indicators
    @ColumnInfo(name = "duplicate_transaction_count") val duplicateTransactionCount: Int = 0,
    @ColumnInfo(name = "suspicious_activity_count") val suspiciousActivityCount: Int = 0,
    @ColumnInfo(name = "unusual_payment_patterns") val unusualPaymentPatterns: Boolean = false,
    @ColumnInfo(name = "multiple_accounts_detected") val multipleAccountsDetected: Boolean = false,
    @ColumnInfo(name = "velocity_check_failed") val velocityCheckFailed: Boolean = false,
    @ColumnInfo(name = "rapid_disbursement_attempts") val rapidDisbursementAttempts: Int = 0,
    
    // Behavioral Flags
    @ColumnInfo(name = "member_status") val memberStatus: String = "FAIR",
    @ColumnInfo(name = "fraud_risk_level") val fraudRiskLevel: String = "LOW",
    @ColumnInfo(name = "fraud_score") val fraudScore: Double = 0.0,
    @ColumnInfo(name = "behavior_score") val behaviorScore: Double = 0.0,
    
    // Flags and Warnings
    @ColumnInfo(name = "is_flagged_for_review") val isFlaggedForReview: Boolean = false,
    @ColumnInfo(name = "is_suspended") val isSuspended: Boolean = false,
    @ColumnInfo(name = "suspension_reason") val suspensionReason: String? = null,
    @ColumnInfo(name = "review_notes") val reviewNotes: String? = null,
    
    // Membership Duration
    @ColumnInfo(name = "months_in_group") val monthsInGroup: Int = 0,
    @ColumnInfo(name = "joined_at") val joinedAt: String? = null,
    @ColumnInfo(name = "last_activity_at") val lastActivityAt: String? = null,
    @ColumnInfo(name = "last_contribution_at") val lastContributionAt: String? = null,
    
    // Admin Notes
    @ColumnInfo(name = "admin_notes") val adminNotes: String? = null,
    @ColumnInfo(name = "last_reviewed_at") val lastReviewedAt: String? = null,
    @ColumnInfo(name = "reviewed_by") val reviewedBy: String? = null,
    
    @ColumnInfo(name = "created_at") val createdAt: String? = null,
    @ColumnInfo(name = "updated_at") val updatedAt: String? = null
)

/**
 * Room entity for fraud detection events
 */
@Entity(
    tableName = "fraud_detection_events",
    indices = [
        Index("member_id"),
        Index("group_id"),
        Index("severity"),
        Index("resolved")
    ]
)
data class FraudDetectionEventEntity(
    @PrimaryKey val id: String,
    
    @ColumnInfo(name = "member_id") val memberId: String,
    @ColumnInfo(name = "group_id") val groupId: String,
    @ColumnInfo(name = "event_type") val eventType: String,
    @ColumnInfo(name = "severity") val severity: String,
    @ColumnInfo(name = "details_json") val detailsJson: String, // JSON string
    @ColumnInfo(name = "action_taken") val actionTaken: String? = null,
    @ColumnInfo(name = "resolved") val resolved: Boolean = false,
    
    @ColumnInfo(name = "created_at") val createdAt: String? = null,
    @ColumnInfo(name = "updated_at") val updatedAt: String? = null
)

/**
 * Room entity for behavior analytics summary
 */
@Entity(
    tableName = "behavior_analytics_summary",
    indices = [
        Index("group_id", unique = true)
    ]
)
data class BehaviorAnalyticsSummaryEntity(
    @PrimaryKey @ColumnInfo(name = "group_id") val groupId: String,
    
    @ColumnInfo(name = "total_members_tracked") val totalMembersTracked: Int = 0,
    @ColumnInfo(name = "excellent_members") val excellentMembers: Int = 0,
    @ColumnInfo(name = "good_members") val goodMembers: Int = 0,
    @ColumnInfo(name = "fair_members") val fairMembers: Int = 0,
    @ColumnInfo(name = "poor_members") val poorMembers: Int = 0,
    @ColumnInfo(name = "suspended_members") val suspendedMembers: Int = 0,
    @ColumnInfo(name = "high_fraud_risk_count") val highFraudRiskCount: Int = 0,
    @ColumnInfo(name = "flagged_members_count") val flaggedMembersCount: Int = 0,
    @ColumnInfo(name = "average_behavior_score") val averageBehaviorScore: Double = 0.0,
    @ColumnInfo(name = "average_fraud_score") val averageFraudScore: Double = 0.0,
    @ColumnInfo(name = "on_time_payment_rate") val onTimePaymentRate: Double = 0.0,
    @ColumnInfo(name = "loan_default_rate") val loanDefaultRate: Double = 0.0,
    @ColumnInfo(name = "calculated_at") val calculatedAt: String? = null
)

