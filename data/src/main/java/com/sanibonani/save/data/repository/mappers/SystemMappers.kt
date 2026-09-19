package com.sanibonani.save.data.repository.mappers

import com.sanibonani.save.data.local.*
import com.sanibonani.save.domain.model.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement

fun AppNotification.toEntity() = NotificationEntity(
    id = id ?: "",
    groupId = groupId,
    memberId = memberId,
    message = message,
    channel = channel,
    triggerEvent = triggerEvent,
    createdAt = createdAt,
)

fun NotificationEntity.toModel() = AppNotification(
    id = id,
    groupId = groupId,
    memberId = memberId,
    message = message,
    channel = channel,
    triggerEvent = triggerEvent,
    createdAt = createdAt,
)

fun AuditLogEntity.toModel(): AuditLog {
    val detailsJson = details?.let {
        try {
            Json.parseToJsonElement(it)
        } catch (_: Exception) {
            null
        }
    }
    return AuditLog(
        id = id,
        actorId = actorId,
        targetMemberId = targetMemberId,
        targetGroupId = targetGroupId,
        action = action,
        details = detailsJson,
        createdAt = createdAt
    )
}

fun AuditLog.toEntity() = AuditLogEntity(
    id = id ?: java.util.UUID.randomUUID().toString(),
    actorId = actorId,
    targetMemberId = targetMemberId,
    targetGroupId = targetGroupId,
    action = action,
    details = details?.toString(),
    createdAt = createdAt
)

fun MemberBehaviorTrack.toEntity() = MemberBehaviorTrackEntity(
    id = id ?: java.util.UUID.randomUUID().toString(),
    memberId = memberId,
    memberIdNumber = memberIdNumber,
    groupId = groupId,
    totalContributions = totalContributions,
    onTimeContributions = onTimeContributions,
    lateContributions = lateContributions,
    overdueCount = overdueCount,
    missedContributions = missedContributions,
    paymentConsistencyScore = paymentConsistencyScore,
    averageDaysLate = averageDaysLate,
    currentPaymentStreak = currentPaymentStreak,
    longestPaymentStreak = longestPaymentStreak,
    hasBrokenStreakRecently = hasBrokenStreakRecently,
    totalAmountContributed = totalAmountContributed,
    totalLateFeesPaid = totalLateFeesPaid,
    pendingLateFees = pendingLateFees,
    totalOutstandingAmount = totalOutstandingAmount,
    totalLoansRequested = totalLoansRequested,
    totalLoansApproved = totalLoansApproved,
    totalLoansCompleted = totalLoansCompleted,
    activeLoans = activeLoans,
    overdueLoans = overdueLoans,
    loanDefaultCount = loanDefaultCount,
    loanCompletionRate = loanCompletionRate,
    duplicateTransactionCount = duplicateTransactionCount,
    suspiciousActivityCount = suspiciousActivityCount,
    unusualPaymentPatterns = unusualPaymentPatterns,
    multipleAccountsDetected = multipleAccountsDetected,
    velocityCheckFailed = velocityCheckFailed,
    rapidDisbursementAttempts = rapidDisbursementAttempts,
    memberStatus = memberStatus.name,
    fraudRiskLevel = fraudRiskLevel.name,
    fraudScore = fraudScore,
    behaviorScore = behaviorScore,
    isFlaggedForReview = isFlaggedForReview,
    isSuspended = isSuspended,
    suspensionReason = suspensionReason,
    reviewNotes = reviewNotes,
    monthsInGroup = monthsInGroup,
    joinedAt = joinedAt ?: "",
    lastActivityAt = lastActivityAt ?: "",
    lastContributionAt = lastContributionAt,
    adminNotes = adminNotes,
    lastReviewedAt = lastReviewedAt,
    reviewedBy = reviewedBy,
    createdAt = createdAt ?: "",
    updatedAt = System.currentTimeMillis(),
)

fun MemberBehaviorTrackEntity.toModel() = MemberBehaviorTrack(
    id = id,
    memberId = memberId,
    memberIdNumber = memberIdNumber,
    groupId = groupId,
    totalContributions = totalContributions,
    onTimeContributions = onTimeContributions,
    lateContributions = lateContributions,
    overdueCount = overdueCount,
    missedContributions = missedContributions,
    paymentConsistencyScore = paymentConsistencyScore,
    averageDaysLate = averageDaysLate,
    currentPaymentStreak = currentPaymentStreak,
    longestPaymentStreak = longestPaymentStreak,
    hasBrokenStreakRecently = hasBrokenStreakRecently,
    totalAmountContributed = totalAmountContributed,
    totalLateFeesPaid = totalLateFeesPaid,
    pendingLateFees = pendingLateFees,
    totalOutstandingAmount = totalOutstandingAmount,
    totalLoansRequested = totalLoansRequested,
    totalLoansApproved = totalLoansApproved,
    totalLoansCompleted = totalLoansCompleted,
    activeLoans = activeLoans,
    overdueLoans = overdueLoans,
    loanDefaultCount = loanDefaultCount,
    loanCompletionRate = loanCompletionRate,
    duplicateTransactionCount = duplicateTransactionCount,
    suspiciousActivityCount = suspiciousActivityCount,
    unusualPaymentPatterns = unusualPaymentPatterns,
    multipleAccountsDetected = multipleAccountsDetected,
    velocityCheckFailed = velocityCheckFailed,
    rapidDisbursementAttempts = rapidDisbursementAttempts,
    memberStatus = BehaviorStatus.valueOf(memberStatus),
    fraudRiskLevel = FraudRiskLevel.valueOf(fraudRiskLevel),
    fraudScore = fraudScore,
    behaviorScore = behaviorScore,
    isFlaggedForReview = isFlaggedForReview,
    isSuspended = isSuspended,
    suspensionReason = suspensionReason,
    reviewNotes = reviewNotes,
    monthsInGroup = monthsInGroup,
    joinedAt = joinedAt,
    lastActivityAt = lastActivityAt,
    lastContributionAt = lastContributionAt,
    adminNotes = adminNotes,
    lastReviewedAt = lastReviewedAt,
    reviewedBy = reviewedBy,
    createdAt = createdAt,
    updatedAt = updatedAt.toString(),
)

fun FraudDetectionEvent.toEntity() = FraudDetectionEventEntity(
    id = id ?: java.util.UUID.randomUUID().toString(),
    memberId = memberId,
    groupId = groupId,
    eventType = eventType,
    severity = severity.name,
    detailsJson = try {
        Json.encodeToString(JsonElement.serializer(), Json.encodeToJsonElement(details))
    } catch (_: Exception) {
        "{}"
    },
    actionTaken = actionTaken,
    resolved = resolved,
    createdAt = createdAt ?: "",
    updatedAt = System.currentTimeMillis(),
)

fun FraudDetectionEventEntity.toModel() = FraudDetectionEvent(
    id = id,
    memberId = memberId,
    groupId = groupId,
    eventType = eventType,
    severity = FraudRiskLevel.valueOf(severity),
    details = try {
        Json.decodeFromString(JsonElement.serializer(), detailsJson)
    } catch (_: Exception) {
        null
    },
    actionTaken = actionTaken,
    resolved = resolved,
    createdAt = createdAt,
    updatedAt = updatedAt.toString(),
)

fun BehaviorAnalyticsSummary.toEntity() = BehaviorAnalyticsSummaryEntity(
    groupId = groupId,
    totalMembersTracked = totalMembersTracked,
    excellentMembers = excellentMembers,
    goodMembers = goodMembers,
    fairMembers = fairMembers,
    poorMembers = poorMembers,
    suspendedMembers = suspendedMembers,
    highFraudRiskCount = highFraudRiskCount,
    flaggedMembersCount = flaggedMembersCount,
    averageBehaviorScore = averageBehaviorScore,
    averageFraudScore = averageFraudScore,
    onTimePaymentRate = onTimePaymentRate,
    loanDefaultRate = loanDefaultRate,
    calculatedAt = calculatedAt ?: "",
)

fun BehaviorAnalyticsSummaryEntity.toModel() = BehaviorAnalyticsSummary(
    groupId = groupId,
    totalMembersTracked = totalMembersTracked,
    excellentMembers = excellentMembers,
    goodMembers = goodMembers,
    fairMembers = fairMembers,
    poorMembers = poorMembers,
    suspendedMembers = suspendedMembers,
    highFraudRiskCount = highFraudRiskCount,
    flaggedMembersCount = flaggedMembersCount,
    averageBehaviorScore = averageBehaviorScore,
    averageFraudScore = averageFraudScore,
    onTimePaymentRate = onTimePaymentRate,
    loanDefaultRate = loanDefaultRate,
    calculatedAt = calculatedAt,
)

fun PlatformFeeConfig.toEntity() = PlatformFeeConfigEntity(
    groupType = groupType,
    roscaCycle = roscaCycle,
    registrationFee = registrationFee,
    monthlyFee = monthlyFee,
    shortfallRecoveryFee = shortfallRecoveryFee
)

fun PlatformFeeConfigEntity.toModel() = PlatformFeeConfig(
    groupType = groupType,
    roscaCycle = roscaCycle,
    registrationFee = registrationFee,
    monthlyFee = monthlyFee,
    shortfallRecoveryFee = shortfallRecoveryFee
)
