package com.sanibonani.save.data.repository

import com.sanibonani.save.data.local.*
import com.sanibonani.save.domain.model.*
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

// ─────────────────────────────────────────────────────────────────────────────
//  GROUP MAPPERS
// ─────────────────────────────────────────────────────────────────────────────

fun Group.toEntity() = GroupEntity(
    id = id ?: "", 
    name = name, 
    type = type, 
    province = province ?: "", 
    city = city ?: "",
    township = township ?: "", 
    description = description ?: "", 
    logoEmoji = logoEmoji,
    joiningFee = joiningFee, 
    monthlyContribution = monthlyContribution,
    lateFee = lateFee, 
    lateFeeGraceDays = lateFeeGraceDays,
    probationMonths = probationMonths, 
    paymentDueDay = paymentDueDay,
    maxMembers = maxMembers, 
    currentMembers = currentMembers,
    isPublic = isPublic, 
    allowPartialPayment = allowPartialPayment,
    autoSuspendAfter = autoSuspendAfter, 
    bankName = bankName,
    accountNumber = accountNumber, 
    branchCode = branchCode,
    accountType = accountType, 
    gatewayPublicKey = gatewayPublicKey,
    balance = balance, 
    adminUserId = adminUserId, 
    feeStatus = feeStatus,
    registrationPaid = registrationPaid, 
    isPlatformSuspended = isPlatformSuspended,
    goalAmount = goalAmount,
    periodMonths = periodMonths,
    constitutionUrl = constitutionUrl,
    constitutionStatus = constitutionStatus,

    maxBeneficiaries = maxBeneficiaries ?: 0,
    beneficiaryIncreasePct = beneficiaryIncreasePct ?: 0.0,

    createdAt = createdAt, 
    latitude = latitude, 
    longitude = longitude, 
    geohash = geohash,
    rotationMethod = rotationMethod
)

fun GroupEntity.toModel() = Group(
    id = id, 
    name = name, 
    type = type, 
    province = province ?: "", 
    city = city ?: "",
    township = township ?: "", 
    description = description ?: "", 
    logoEmoji = logoEmoji,
    joiningFee = joiningFee, 
    monthlyContribution = monthlyContribution,
    lateFee = lateFee, 
    lateFeeGraceDays = lateFeeGraceDays,
    probationMonths = probationMonths, 
    paymentDueDay = paymentDueDay,
    maxMembers = maxMembers, 
    currentMembers = currentMembers,
    isPublic = isPublic, 
    allowPartialPayment = allowPartialPayment,
    autoSuspendAfter = autoSuspendAfter, 
    bankName = bankName,
    accountNumber = accountNumber, 
    branchCode = branchCode,
    accountType = accountType, 
    gatewayPublicKey = gatewayPublicKey,
    balance = balance, 
    adminUserId = adminUserId, 
    feeStatus = feeStatus,
    registrationPaid = registrationPaid, 
    isPlatformSuspended = isPlatformSuspended,
    goalAmount = goalAmount,
    periodMonths = periodMonths,
    constitutionUrl = constitutionUrl,
    constitutionStatus = constitutionStatus,

    maxBeneficiaries = maxBeneficiaries,
    beneficiaryIncreasePct = beneficiaryIncreasePct,

    createdAt = createdAt, 
    latitude = latitude, 
    longitude = longitude, 
    geohash = geohash,
    rotationMethod = rotationMethod
)

// ─────────────────────────────────────────────────────────────────────────────
//  MEMBER MAPPERS
// ─────────────────────────────────────────────────────────────────────────────

fun Member.toEntity() = MemberEntity(
    id = id ?: "", 
    groupId = groupId, 
    userId = userId ?: "", 
    memberKey = memberKey ?: "",
    fullName = fullName,
    idNumber = idNumber ?: "", 
    phone = phone ?: "", 
    email = email ?: "",
    street = street ?: "",
    suburb = suburb ?: "",
    city = city ?: "",
    province = province ?: "",
    status = status,
    joinedAt = joinedAt ?: "", 
    probationEndAt = probationEndAt ?: "",
    profilePhotoUrl = profilePhotoUrl, 
    document1Url = document1Url,
    document1Type = document1Type, 
    document1Status = document1Status,
    document2Url = document2Url, 
    document2Type = document2Type,
    document2Status = document2Status, 
    document3Url = document3Url,
    document3Type = document3Type,
    document3Status = document3Status,
    document4Url = document4Url,
    document4Type = document4Type,
    document4Status = document4Status,
    document5Url = document5Url,
    document5Type = document5Type,
    document5Status = document5Status,

    beneficiaryCount = beneficiaryCount ?: 0,
    beneficiaryOver65Count = beneficiaryOver65Count ?: 0,
    monthlyContributionOverride = monthlyContributionOverride,

    totalContributions = totalContributions ?: 0,
    totalPaid = totalPaid ?: 0.0,
    fcmToken = fcmToken, 
    notificationPref = notificationPref, 
    createdAt = createdAt
)

fun MemberEntity.toModel() = Member(
    id = id, 
    groupId = groupId, 
    userId = userId, 
    fullName = fullName,
    idNumber = idNumber,
    phone = phone, 
    email = email, 
    street = street,
    suburb = suburb,
    city = city,
    province = province,
    status = status,
    joinedAt = joinedAt, 
    probationEndAt = probationEndAt,
    profilePhotoUrl = profilePhotoUrl, 
    document1Url = document1Url,
    document1Type = document1Type, 
    document1Status = document1Status,
    document2Url = document2Url, 
    document2Type = document2Type,
    document2Status = document2Status, 
    document3Url = document3Url,
    document3Type = document3Type,
    document3Status = document3Status,
    document4Url = document4Url,
    document4Type = document4Type,
    document4Status = document4Status,
    document5Url = document5Url,
    document5Type = document5Type,
    document5Status = document5Status,

    beneficiaryCount = beneficiaryCount ?: 0,
    beneficiaryOver65Count = beneficiaryOver65Count ?: 0,
    monthlyContributionOverride = monthlyContributionOverride,

    totalContributions = totalContributions ?: 0,
    totalPaid = totalPaid,
    fcmToken = fcmToken, 
    notificationPref = notificationPref, 
    createdAt = createdAt,
    memberKey = memberKey
)

// ─────────────────────────────────────────────────────────────────────────────
//  CONTRIBUTION MAPPERS
// ─────────────────────────────────────────────────────────────────────────────

fun Contribution.toEntity() = ContributionEntity(
    id = id ?: "", 
    memberId = memberId, 
    groupId = groupId, 
    policyId = policyId,
    amount = amount,
    createdAt = createdAt,
    dueDate = dueDate, 
    paidAt = paidAt, 
    status = status,
    type = type,
    paymentMethod = paymentMethod,
    lateFeesApplied = lateFeesApplied,
    transactionId = transactionId
)

fun ContributionEntity.toModel() = Contribution(
    id = id, 
    memberId = memberId, 
    groupId = groupId,
    policyId = policyId,
    amount = amount,
    createdAt = createdAt,
    dueDate = dueDate, 
    paidAt = paidAt, 
    status = status,
    type = type,
    paymentMethod = paymentMethod,
    transactionId = transactionId,
    lateFeesApplied = lateFeesApplied
)

// ─────────────────────────────────────────────────────────────────────────────
//  PAYMENT MAPPERS
// ─────────────────────────────────────────────────────────────────────────────

fun Payment.toEntity() = PaymentEntity(
    id = id ?: "", 
    memberId = memberId, 
    groupId = groupId, 
    amount = amount,
    paymentType = paymentType, 
    paymentMethod = paymentMethod,
    transactionId = transactionId, 
    status = status,
    processedAt = processedAt, 
    createdAt = createdAt
)

fun PaymentEntity.toModel() = Payment(
    id = id, 
    memberId = memberId, 
    groupId = groupId, 
    amount = amount,
    paymentType = paymentType, 
    paymentMethod = paymentMethod,
    transactionId = transactionId, 
    status = status,
    processedAt = processedAt, 
    createdAt = createdAt
)

// ─────────────────────────────────────────────────────────────────────────────
//  BENEFICIARY MAPPERS
// ─────────────────────────────────────────────────────────────────────────────

fun Beneficiary.toEntity() = BeneficiaryEntity(
    groupId = groupId,
    memberId = memberId,
    id = id ?: "",
    fullName = fullName,
    idNumber = idNumber,
    relationship = relationship,
    dateOfBirth = dateOfBirth,
    isOver65 = isOver65,
    documentUrl = documentUrl,
    documentStatus = documentStatus.name.lowercase(),
    createdAt = createdAt
)

fun BeneficiaryEntity.toModel() = Beneficiary(
    groupId = groupId,
    memberId = memberId,
    id = id,
    fullName = fullName,
    idNumber = idNumber,
    relationship = relationship,
    dateOfBirth = dateOfBirth,
    isOver65 = isOver65,
    documentUrl = documentUrl,
    documentStatus = DocumentStatus.entries.find { it.name.equals(documentStatus, true) } ?: DocumentStatus.PENDING,
    createdAt = createdAt
)

// ─────────────────────────────────────────────────────────────────────────────
//  PAYOUT MAPPERS
// ─────────────────────────────────────────────────────────────────────────────

fun PayoutRequest.toEntity() = PayoutEntity(
    id = id ?: "",
    groupId = groupId,
    amount = amount,
    bankName = bankName,
    accountNo = accountNo,
    branchCode = branchCode,
    status = status,
    processedBy = processedBy,
    processedAt = processedAt,
    payoutReference = payoutReference,
    createdAt = createdAt
)

fun PayoutEntity.toModel() = PayoutRequest(
    id = id,
    groupId = groupId,
    amount = amount,
    bankName = bankName,
    accountNo = accountNo,
    branchCode = branchCode,
    status = status,
    processedBy = processedBy,
    processedAt = processedAt,
    payoutReference = payoutReference,
    createdAt = createdAt
)

// ─────────────────────────────────────────────────────────────────────────────
//  MEMBER DOCUMENT MAPPERS
// ─────────────────────────────────────────────────────────────────────────────

fun MemberDocument.toEntity() = MemberDocumentEntity(
    id = id ?: "",
    memberId = memberId,
    groupId = groupId,
    label = label,
    documentUrl = documentUrl,
    documentType = documentType,
    status = status,
    createdAt = createdAt
)

fun MemberDocumentEntity.toModel() = MemberDocument(
    id = id,
    memberId = memberId,
    groupId = groupId,
    label = label,
    documentUrl = documentUrl,
    documentType = documentType,
    status = status,
    createdAt = createdAt
)

// ─────────────────────────────────────────────────────────────────────────────
//  NOTIFICATION MAPPERS
// ─────────────────────────────────────────────────────────────────────────────

fun AppNotification.toEntity() = NotificationEntity(
    id = id ?: "",
    groupId = groupId,
    memberId = memberId,
    message = message,
    channel = channel,
    triggerEvent = triggerEvent,
    createdAt = createdAt
)

fun NotificationEntity.toModel() = AppNotification(
    id = id,
    groupId = groupId,
    memberId = memberId,
    message = message,
    channel = channel,
    triggerEvent = triggerEvent,
    createdAt = createdAt
)

// ─────────────────────────────────────────────────────────────────────────────
//  LOAN MAPPERS
// ─────────────────────────────────────────────────────────────────────────────

fun Loan.toEntity() = LoanEntity(
    id = id ?: "",
    memberId = memberId,
    groupId = groupId,
    amount = amount,
    interestRate = interestRate,
    totalToRepay = totalToRepay,
    totalRepaid = totalRepaid,
    monthlyRepayment = monthlyRepayment,
    startDate = startDate,
    endDate = endDate,
    nextPaymentDate = nextPaymentDate,
    status = status,
    purpose = purpose,
    createdAt = createdAt
)

fun LoanEntity.toModel() = Loan(
    id = id,
    memberId = memberId,
    groupId = groupId,
    amount = amount,
    interestRate = interestRate,
    totalToRepay = totalToRepay,
    totalRepaid = totalRepaid,
    monthlyRepayment = monthlyRepayment,
    startDate = startDate,
    endDate = endDate,
    nextPaymentDate = nextPaymentDate,
    status = status,
    purpose = purpose,
    createdAt = createdAt
)

fun LoanRepayment.toEntity() = LoanRepaymentEntity(
    id = id ?: "",
    loanId = loanId,
    memberId = memberId,
    groupId = groupId,
    amount = amount,
    paidAt = paidAt,
    paymentMethod = paymentMethod,
    transactionId = transactionId,
    createdAt = createdAt
)

fun LoanRepaymentEntity.toModel() = LoanRepayment(
    id = id,
    loanId = loanId,
    memberId = memberId,
    groupId = groupId,
    amount = amount,
    paidAt = paidAt,
    paymentMethod = paymentMethod,
    transactionId = transactionId,
    createdAt = createdAt
)

// ─────────────────────────────────────────────────────────────────────────────
//  BURIAL CLAIM MAPPERS
// ─────────────────────────────────────────────────────────────────────────────

fun BeneficiaryPayoutClaim.toEntity() = BeneficiaryClaimEntity(
    id = id ?: java.util.UUID.randomUUID().toString(),
    groupId = groupId,
    memberId = memberId,
    beneficiaryId = beneficiaryId,
    beneficiaryName = beneficiaryName,
    causeOfDeath = causeOfDeath,
    dateOfDeath = dateOfDeath,
    claimAmount = claimAmount,
    bankName = bankName,
    accountNo = accountNo,
    branchCode = branchCode,
    accountHolder = accountHolder,
    notes = notes,
    status = status,
    reviewedBy = reviewedBy,
    reviewedAt = reviewedAt,
    adminNotes = adminNotes,
    rejectionReason = rejectionReason,
    createdAt = createdAt
)

fun BeneficiaryClaimEntity.toModel() = BeneficiaryPayoutClaim(
    id = id,
    groupId = groupId,
    memberId = memberId,
    beneficiaryId = beneficiaryId,
    beneficiaryName = beneficiaryName,
    causeOfDeath = causeOfDeath,
    dateOfDeath = dateOfDeath,
    claimAmount = claimAmount,
    bankName = bankName,
    accountNo = accountNo,
    branchCode = branchCode,
    accountHolder = accountHolder,
    notes = notes,
    status = status,
    reviewedBy = reviewedBy,
    reviewedAt = reviewedAt,
    adminNotes = adminNotes,
    rejectionReason = rejectionReason,
    createdAt = createdAt
)

// ─────────────────────────────────────────────────────────────────────────────
//  LEDGER MAPPERS
// ─────────────────────────────────────────────────────────────────────────────

fun LedgerEntryEntity.toModel() = LedgerEntry(
    id = id,
    groupId = groupId,
    transactionId = transactionId,
    amount = amount,
    balanceAfter = balanceAfter,
    description = description,
    category = category,
    createdAt = createdAt
)

fun LedgerEntry.toLedgerEntity() = LedgerEntryEntity(
    id = id ?: java.util.UUID.randomUUID().toString(),
    groupId = groupId,
    transactionId = transactionId,
    amount = amount,
    balanceAfter = balanceAfter,
    description = description,
    category = category,
    createdAt = createdAt
)

// ─────────────────────────────────────────────────────────────────────────────
// BEHAVIOR TRACKING MAPPERS
// ─────────────────────────────────────────────────────────────────────────────

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
    joinedAt = joinedAt,
    lastActivityAt = lastActivityAt,
    lastContributionAt = lastContributionAt,
    adminNotes = adminNotes,
    lastReviewedAt = lastReviewedAt,
    reviewedBy = reviewedBy,
    createdAt = createdAt,
    updatedAt = updatedAt
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
    updatedAt = updatedAt
)

fun FraudDetectionEvent.toEntity() = FraudDetectionEventEntity(
    id = id ?: java.util.UUID.randomUUID().toString(),
    memberId = memberId,
    groupId = groupId,
    eventType = eventType,
    severity = severity.name,
    detailsJson = try {
        Json.encodeToString(
            MapSerializer(
                String.serializer(),
                String.serializer()
            ),
            details
        )
    } catch (e: Exception) {
        "{}"
    },
    actionTaken = actionTaken,
    resolved = resolved,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun FraudDetectionEventEntity.toModel() = FraudDetectionEvent(
    id = id,
    memberId = memberId,
    groupId = groupId,
    eventType = eventType,
    severity = FraudRiskLevel.valueOf(severity),
    details = try {
        Json.decodeFromString(
            MapSerializer(
                String.serializer(),
                String.serializer()
            ),
            detailsJson
        )
    } catch (e: Exception) {
        emptyMap()
    },
    actionTaken = actionTaken,
    resolved = resolved,
    createdAt = createdAt,
    updatedAt = updatedAt
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
    calculatedAt = calculatedAt
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
    calculatedAt = calculatedAt
)
