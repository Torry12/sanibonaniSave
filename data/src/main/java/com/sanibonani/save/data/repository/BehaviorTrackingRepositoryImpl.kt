package com.sanibonani.save.data.repository

import com.sanibonani.save.data.local.SanibonaniDatabase
import com.sanibonani.save.data.utils.BehaviorScoringUtils
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.BehaviorTrackingRepository
import com.sanibonani.save.domain.repository.LoanRepository
import com.sanibonani.save.domain.repository.MemberRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import javax.inject.Inject
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

class BehaviorTrackingRepositoryImpl @Inject constructor(
    private val supabase: SupabaseClient,
    private val db: SanibonaniDatabase,
    private val memberRepository: MemberRepository,
    private val loanRepository: LoanRepository
) : BaseRepository("BehaviorTrackingRepository"), BehaviorTrackingRepository {

    private val BEHAVIOR_COLUMNS_SAFE = """
        id, member_id, member_id_number, group_id, total_contributions, on_time_contributions,
        late_contributions, overdue_count, missed_contributions, payment_consistency_score,
        average_days_late, current_payment_streak, longest_payment_streak,
        has_broken_streak_recently, total_amount_contributed, total_late_fees_paid,
        pending_late_fees, total_outstanding_amount, total_loans_requested,
        total_loans_approved, total_loans_completed, active_loans, overdue_loans,
        loan_default_count, loan_completion_rate, duplicate_transaction_count,
        suspicious_activity_count, unusual_payment_patterns, multiple_accounts_detected,
        velocity_check_failed, rapid_disbursement_attempts, member_status, fraud_risk_level,
        fraud_score, behavior_score, is_flagged_for_review, is_suspended,
        suspension_reason, review_notes, months_in_group, joined_at, last_activity_at,
        last_contribution_at, admin_notes, last_reviewed_at, reviewed_by, created_at, updated_at
    """

    // ─────────────────────────────────────────────────────────────────────────────
    // OBSERVE BEHAVIOR TRACKING
    // ─────────────────────────────────────────────────────────────────────────────

    override fun observeMemberBehavior(memberId: String): Flow<Result<MemberBehaviorTrack?>> {
        return db.memberBehaviorTrackDao().observeByMemberId(memberId)
            .map { entity -> Result.success(entity?.toModel()) }
    }

    override fun observeMemberBehaviorByIdNumber(idNumber: String, groupId: String): Flow<Result<MemberBehaviorTrack?>> {
        return db.memberBehaviorTrackDao().observeByIdNumber(idNumber, groupId)
            .map { entity -> Result.success(entity?.toModel()) }
    }

    override fun observeGroupMembersBehavior(groupId: String): Flow<Result<List<MemberBehaviorTrack>>> {
        return db.memberBehaviorTrackDao().observeGroupMembersBehavior(groupId)
            .map { entities -> Result.success(entities.map { it.toModel() }) }
    }

    override fun observeHighRiskMembers(groupId: String): Flow<Result<List<MemberBehaviorTrack>>> {
        return db.memberBehaviorTrackDao().observeHighRiskMembers(groupId)
            .map { entities -> Result.success(entities.map { it.toModel() }) }
    }

    override fun observeFlaggedMembers(groupId: String): Flow<Result<List<MemberBehaviorTrack>>> {
        return db.memberBehaviorTrackDao().observeFlaggedMembersInGroup(groupId)
            .map { entities -> Result.success(entities.map { it.toModel() }) }
    }

    override fun observeSuspendedMembers(groupId: String): Flow<Result<List<MemberBehaviorTrack>>> {
        return db.memberBehaviorTrackDao().observeSuspendedMembers()
            .map { entities ->
                Result.success(entities.filter { it.groupId == groupId }.map { it.toModel() })
            }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // GET BEHAVIOR TRACKING
    // ─────────────────────────────────────────────────────────────────────────────

    override suspend fun getMemberBehavior(memberId: String): Result<MemberBehaviorTrack?> = runCatching {
        db.memberBehaviorTrackDao().getByMemberId(memberId)?.toModel()
    }

    override suspend fun getMemberBehaviorByIdNumber(idNumber: String, groupId: String): Result<MemberBehaviorTrack?> = runCatching {
        db.memberBehaviorTrackDao().getByIdNumber(idNumber, groupId)?.toModel()
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // CALCULATE AND UPDATE BEHAVIOR SCORES
    // ─────────────────────────────────────────────────────────────────────────────

    override suspend fun calculateAndUpdateMemberBehavior(memberId: String, groupId: String): Result<MemberBehaviorTrack> = runCatching {
        val member = memberRepository.getMemberById(memberId).getOrThrow()
            ?: throw IllegalStateException("Member not found")

        // Get or create behavior track
        var track = db.memberBehaviorTrackDao().getByMemberId(memberId)?.toModel()
            ?: createDefaultBehaviorTrack(member, groupId)

        // Update metrics from contributions and loans
        track = updateContributionMetrics(track, memberId)
        track = updateLoanMetrics(track, memberId)

        // Recalculate scores
        track = track.copy(
            behaviorScore = BehaviorScoringUtils.calculateBehaviorScore(track),
            fraudScore = BehaviorScoringUtils.calculateFraudScore(track)
        )

        // Determine statuses
        track = track.copy(
            fraudRiskLevel = BehaviorScoringUtils.determineFraudRiskLevel(track.fraudScore, track),
            memberStatus = BehaviorScoringUtils.determineBehaviorStatus(track.behaviorScore, track.fraudScore, track),
            isFlaggedForReview = BehaviorScoringUtils.shouldFlagForReview(track),
            isSuspended = BehaviorScoringUtils.shouldSuspend(track),
            lastActivityAt = Instant.now().toString(),
            updatedAt = Instant.now().toString()
        )

        // Save to database
        db.memberBehaviorTrackDao().upsert(track.toEntity())

        // Sync to Supabase if network available
        try {
            supabase.postgrest["member_behavior_tracks"].upsert(track) {
                select(columns = Columns.raw(BEHAVIOR_COLUMNS_SAFE))
            }
        } catch (e: Exception) {
            com.sanibonani.save.data.logging.AppLogger.e("BehaviorTracking", "Failed to sync behavior track to Supabase", e)
        }

        track
    }

    override suspend fun recalculateGroupBehaviorScores(groupId: String): Result<Unit> = runCatching {
        val members = memberRepository.getGroupMembers(groupId)
            .first()
            .getOrNull()
            .orEmpty()
            .mapNotNull { it.id }
            .ifEmpty {
                db.memberBehaviorTrackDao().getAllByGroup(groupId).mapNotNull { it.memberId }
            }

        members.forEach { memberId ->
            calculateAndUpdateMemberBehavior(memberId, groupId)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // SAVE BEHAVIOR TRACKING
    // ─────────────────────────────────────────────────────────────────────────────

    override suspend fun saveBehaviorTrack(track: MemberBehaviorTrack): Result<Unit> = runCatching {
        db.memberBehaviorTrackDao().upsert(track.toEntity())
    }

    override suspend fun saveBehaviorTracks(tracks: List<MemberBehaviorTrack>): Result<Unit> = runCatching {
        db.memberBehaviorTrackDao().upsertAll(tracks.map { it.toEntity() })
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // FRAUD DETECTION EVENTS
    // ─────────────────────────────────────────────────────────────────────────────

    override fun observeFraudEventsByMember(memberId: String): Flow<Result<List<FraudDetectionEvent>>> {
        return db.fraudDetectionEventDao().observeEventsByMember(memberId)
            .map { entities -> Result.success(entities.map { it.toModel() }) }
    }

    override suspend fun recordFraudEvent(event: FraudDetectionEvent): Result<Unit> = runCatching {
        db.fraudDetectionEventDao().upsert(event.toEntity())
    }

    override suspend fun resolveFraudEvent(eventId: String, actionTaken: String): Result<Unit> = runCatching {
        val event = db.fraudDetectionEventDao().getById(eventId)
            ?: throw IllegalStateException("Fraud event not found")

        val resolved = event.copy(
            actionTaken = actionTaken,
            resolved = true,
            updatedAt = Instant.now().toString()
        ).toModel()

        db.fraudDetectionEventDao().update(resolved.toEntity())
    }

    override fun observeUnresolvedFraudEvents(groupId: String): Flow<Result<List<FraudDetectionEvent>>> {
        return db.fraudDetectionEventDao().observeUnresolvedEventsByGroup(groupId)
            .map { entities -> Result.success(entities.map { it.toModel() }) }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // MEMBER FLAGGING AND SUSPENSION
    // ─────────────────────────────────────────────────────────────────────────────

    override suspend fun flagMemberForReview(memberId: String, reason: String, reviewNotes: String?): Result<Unit> = runCatching {
        val track = db.memberBehaviorTrackDao().getByMemberId(memberId)?.toModel()
            ?: throw IllegalStateException("Behavior track not found")

        val updated = track.copy(
            isFlaggedForReview = true,
            reviewNotes = reviewNotes ?: reason,
            lastReviewedAt = Instant.now().toString(),
            reviewedBy = try { supabase.auth.currentUserOrNull()?.id } catch (e: Exception) { null },
            updatedAt = Instant.now().toString()
        )

        db.memberBehaviorTrackDao().update(updated.toEntity())
    }

    override suspend fun unflagMember(memberId: String): Result<Unit> = runCatching {
        val track = db.memberBehaviorTrackDao().getByMemberId(memberId)?.toModel()
            ?: throw IllegalStateException("Behavior track not found")

        val updated = track.copy(
            isFlaggedForReview = false,
            lastReviewedAt = Instant.now().toString(),
            reviewedBy = try { supabase.auth.currentUserOrNull()?.id } catch (e: Exception) { null },
            updatedAt = Instant.now().toString()
        )

        db.memberBehaviorTrackDao().update(updated.toEntity())
    }

    override suspend fun suspendMember(memberId: String, reason: String): Result<Unit> = runCatching {
        val track = db.memberBehaviorTrackDao().getByMemberId(memberId)?.toModel()
            ?: throw IllegalStateException("Behavior track not found")

        val updated = track.copy(
            isSuspended = true,
            suspensionReason = reason,
            memberStatus = BehaviorStatus.SUSPENDED,
            updatedAt = Instant.now().toString()
        )

        db.memberBehaviorTrackDao().update(updated.toEntity())
    }

    override suspend fun unsuspendMember(memberId: String): Result<Unit> = runCatching {
        val track = db.memberBehaviorTrackDao().getByMemberId(memberId)?.toModel()
            ?: throw IllegalStateException("Behavior track not found")

        val updated = track.copy(
            isSuspended = false,
            suspensionReason = null,
            updatedAt = Instant.now().toString()
        )

        db.memberBehaviorTrackDao().update(updated.toEntity())
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // ANALYTICS
    // ─────────────────────────────────────────────────────────────────────────────

    override fun observeBehaviorAnalytics(groupId: String): Flow<Result<BehaviorAnalyticsSummary?>> {
        return db.behaviorAnalyticsSummaryDao().observeByGroup(groupId)
            .map { entity -> Result.success(entity?.toModel()) }
    }

    override suspend fun calculateBehaviorAnalytics(groupId: String): Result<BehaviorAnalyticsSummary> = runCatching {
        val tracks = db.memberBehaviorTrackDao().getAllByGroup(groupId)
            .map { it.toModel() }

        if (tracks.isEmpty()) {
            return@runCatching BehaviorAnalyticsSummary(
                groupId = groupId,
                calculatedAt = Instant.now().toString()
            )
        }

        val excellentCount = tracks.count { it.memberStatus == BehaviorStatus.EXCELLENT }
        val goodCount = tracks.count { it.memberStatus == BehaviorStatus.GOOD }
        val fairCount = tracks.count { it.memberStatus == BehaviorStatus.FAIR }
        val poorCount = tracks.count { it.memberStatus == BehaviorStatus.POOR }
        val suspendedCount = tracks.count { it.isSuspended }
        val highFraudCount = tracks.count { it.fraudRiskLevel in listOf(FraudRiskLevel.HIGH, FraudRiskLevel.CRITICAL) }
        val flaggedCount = tracks.count { it.isFlaggedForReview }

        val averageBehaviorScore = tracks.map { it.behaviorScore }.average()
        val averageFraudScore = tracks.map { it.fraudScore }.average()

        val totalOnTimeContributions = tracks.sumOf { it.onTimeContributions }
        val totalContributions = tracks.sumOf { it.totalContributions }
        val onTimeRate = if (totalContributions > 0) (totalOnTimeContributions.toDouble() / totalContributions) * 100 else 0.0

        val totalDefaults = tracks.sumOf { it.loanDefaultCount }
        val totalLoans = tracks.sumOf { it.totalLoansRequested }
        val defaultRate = if (totalLoans > 0) (totalDefaults.toDouble() / totalLoans) * 100 else 0.0

        val summary = BehaviorAnalyticsSummary(
            groupId = groupId,
            totalMembersTracked = tracks.size,
            excellentMembers = excellentCount,
            goodMembers = goodCount,
            fairMembers = fairCount,
            poorMembers = poorCount,
            suspendedMembers = suspendedCount,
            highFraudRiskCount = highFraudCount,
            flaggedMembersCount = flaggedCount,
            averageBehaviorScore = averageBehaviorScore,
            averageFraudScore = averageFraudScore,
            onTimePaymentRate = onTimeRate,
            loanDefaultRate = defaultRate,
            calculatedAt = Instant.now().toString()
        )

        db.behaviorAnalyticsSummaryDao().upsert(summary.toEntity())
        summary
    }

    override suspend fun getMembersBehaviorStats(groupId: String): Result<Map<String, Any>> = runCatching {
        val tracks = db.memberBehaviorTrackDao().getAllByGroup(groupId)
            .map { it.toModel() }

        mapOf(
            "totalMembers" to tracks.size,
            "averageBehaviorScore" to (tracks.map { it.behaviorScore }.average() ?: 0.0),
            "averageFraudScore" to (tracks.map { it.fraudScore }.average() ?: 0.0),
            "excellentMembers" to tracks.count { it.memberStatus == BehaviorStatus.EXCELLENT },
            "goodMembers" to tracks.count { it.memberStatus == BehaviorStatus.GOOD },
            "fairMembers" to tracks.count { it.memberStatus == BehaviorStatus.FAIR },
            "poorMembers" to tracks.count { it.memberStatus == BehaviorStatus.POOR },
            "suspendedMembers" to tracks.count { it.isSuspended },
            "highRiskMembers" to tracks.count { it.fraudRiskLevel in listOf(FraudRiskLevel.HIGH, FraudRiskLevel.CRITICAL) },
            "flaggedMembers" to tracks.count { it.isFlaggedForReview }
        )
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // HELPER FUNCTIONS
    // ─────────────────────────────────────────────────────────────────────────────

    private fun createDefaultBehaviorTrack(member: Member, groupId: String): MemberBehaviorTrack {
        val joinDate = member.joinedAt?.let { LocalDate.parse(it.substringBefore("T")) }
            ?: LocalDate.now()

        val joinYearMonth = YearMonth.from(joinDate)
        val monthsInGroup = ChronoUnit.MONTHS
            .between(joinYearMonth, YearMonth.now())
            .toInt()
            .coerceAtLeast(0)

        return MemberBehaviorTrack(
            id = java.util.UUID.randomUUID().toString(),
            memberId = member.id ?: "",
            memberIdNumber = member.idNumber ?: "",
            groupId = groupId,
            monthsInGroup = monthsInGroup,
            joinedAt = member.joinedAt,
            createdAt = Instant.now().toString()
        )
    }

    private suspend fun updateContributionMetrics(track: MemberBehaviorTrack, memberId: String): MemberBehaviorTrack {
        val contributions = db.contributionDao()
            .observeContributions(memberId)
            .first()
            .filter { it.groupId == track.groupId && it.type == "contribution" }
            .map { it.toModel() }

        if (contributions.isEmpty()) {
            return track.copy(
                totalContributions = 0,
                onTimeContributions = 0,
                lateContributions = 0,
                overdueCount = 0,
                missedContributions = 0,
                paymentConsistencyScore = BehaviorScoringUtils.calculatePaymentConsistency(0, 0),
                averageDaysLate = 0.0,
                currentPaymentStreak = 0,
                longestPaymentStreak = 0,
                hasBrokenStreakRecently = false,
                totalAmountContributed = 0.0,
                totalOutstandingAmount = 0.0,
                lastContributionAt = null,
                duplicateTransactionCount = 0,
                velocityCheckFailed = false,
                unusualPaymentPatterns = false,
                suspiciousActivityCount = 0
            )
        }

        val now = Instant.now()
        val totalContributions = contributions.size
        val overdueCount = contributions.count { it.status == ContributionStatus.OVERDUE }
        val missedContributions = contributions.count {
            it.status == ContributionStatus.DUE && parseInstantOrNull(it.dueDate)?.isBefore(now) == true
        }

        val paidContributions = contributions.filter { it.status == ContributionStatus.PAID }
        val onTimeContributions = paidContributions.count { contribution ->
            val paidAt = parseInstantOrNull(contribution.paidAt)
            val dueAt = parseInstantOrNull(contribution.dueDate)
            paidAt != null && dueAt != null && !paidAt.isAfter(dueAt)
        }
        val lateContributions = paidContributions.size - onTimeContributions

        val lateDays = paidContributions.mapNotNull { contribution ->
            val paidAt = parseInstantOrNull(contribution.paidAt)
            val dueAt = parseInstantOrNull(contribution.dueDate)
            if (paidAt != null && dueAt != null && paidAt.isAfter(dueAt)) {
                ChronoUnit.DAYS.between(dueAt, paidAt).toDouble().coerceAtLeast(0.0)
            } else {
                null
            }
        }

        val obligations = contributions
            .sortedBy { parseInstantOrNull(it.dueDate) ?: Instant.EPOCH }
            .map { contribution ->
                when (contribution.status) {
                    ContributionStatus.PAID -> {
                        val paidAt = parseInstantOrNull(contribution.paidAt)
                        val dueAt = parseInstantOrNull(contribution.dueDate)
                        paidAt != null && dueAt != null && !paidAt.isAfter(dueAt)
                    }
                    ContributionStatus.PARTIAL, ContributionStatus.DUE, ContributionStatus.OVERDUE -> false
                }
            }

        val longestPaymentStreak = calculateLongestStreak(obligations)
        val currentPaymentStreak = calculateCurrentStreak(obligations)
        val hasBrokenStreakRecently = obligations.takeLast(3).any { !it }

        val totalAmountContributed = contributions
            .filter { it.status == ContributionStatus.PAID || it.status == ContributionStatus.PARTIAL }
            .sumOf { it.amount }

        val contributionOutstanding = contributions
            .filter { it.status == ContributionStatus.DUE || it.status == ContributionStatus.OVERDUE }
            .sumOf { it.amount }

        val latestPaidAt = paidContributions
            .mapNotNull { parseInstantOrNull(it.paidAt) }
            .maxOrNull()
            ?.toString()

        val paidTransactions = paidContributions
            .mapNotNull { contribution ->
                parseInstantOrNull(contribution.paidAt)?.toEpochMilli()?.let { paidAtMillis ->
                    mapOf(
                        "id" to (contribution.id ?: ""),
                        "member_id" to memberId,
                        "amount" to contribution.amount,
                        "timestamp" to paidAtMillis,
                        "created_at" to (contribution.paidAt ?: contribution.createdAt ?: "")
                    )
                }
            }

        // Fix: Detect unique duplicate pairs to avoid double counting (A counts B, then B counts A)
        val duplicateIds = mutableSetOf<String>()
        paidTransactions.forEachIndexed { index, tx ->
            val matches = paidTransactions.filterIndexed { i, other -> 
                i > index && BehaviorScoringUtils.detectDuplicateTransaction(listOf(other), tx) 
            }
            if (matches.isNotEmpty()) {
                duplicateIds.add(tx["id"] as String)
                matches.forEach { duplicateIds.add(it["id"] as String) }
            }
        }
        val duplicateTransactionCount = duplicateIds.size

        val velocityCheckFailed = BehaviorScoringUtils.detectVelocitySpike(
            transactions = paidTransactions,
            memberId = memberId
        )
        val unusualPaymentPatterns = BehaviorScoringUtils.detectUnusualPaymentPattern(
            contributions = paidTransactions,
            memberId = memberId
        )

        val suspiciousActivityCount = listOf(
            duplicateTransactionCount > 0,
            velocityCheckFailed,
            unusualPaymentPatterns
        ).count { it }

        return track.copy(
            totalContributions = totalContributions,
            onTimeContributions = onTimeContributions,
            lateContributions = lateContributions,
            overdueCount = overdueCount,
            missedContributions = missedContributions,
            paymentConsistencyScore = BehaviorScoringUtils.calculatePaymentConsistency(totalContributions, onTimeContributions),
            averageDaysLate = lateDays.average().takeIf { !it.isNaN() } ?: 0.0,
            currentPaymentStreak = currentPaymentStreak,
            longestPaymentStreak = longestPaymentStreak,
            hasBrokenStreakRecently = hasBrokenStreakRecently,
            totalAmountContributed = totalAmountContributed,
            totalOutstandingAmount = contributionOutstanding,
            lastContributionAt = latestPaidAt,
            duplicateTransactionCount = duplicateTransactionCount,
            velocityCheckFailed = velocityCheckFailed,
            unusualPaymentPatterns = unusualPaymentPatterns,
            suspiciousActivityCount = suspiciousActivityCount
        )
    }

    private suspend fun updateLoanMetrics(track: MemberBehaviorTrack, memberId: String): MemberBehaviorTrack {
        val loans = loanRepository.getMemberLoans(memberId)
            .first()
            .getOrDefault(emptyList())
            .filter { it.groupId == track.groupId }

        if (loans.isEmpty()) {
            return track.copy(
                totalLoansRequested = 0,
                totalLoansApproved = 0,
                totalLoansCompleted = 0,
                activeLoans = 0,
                overdueLoans = 0,
                loanDefaultCount = 0,
                loanCompletionRate = 0.0
            )
        }

        val totalLoansRequested = loans.size
        val totalLoansApproved = loans.count {
            it.status == LoanStatus.APPROVED ||
                it.status == LoanStatus.ACTIVE ||
                it.status == LoanStatus.PARTIALLY_PAID ||
                it.status == LoanStatus.COMPLETED
        }
        val totalLoansCompleted = loans.count { it.status == LoanStatus.COMPLETED }
        val activeLoans = loans.count { it.status == LoanStatus.ACTIVE || it.status == LoanStatus.PARTIALLY_PAID }
        val overdueLoans = loans.count { it.status == LoanStatus.OVERDUE }

        val loanDefaultCount = loans.count { loan ->
            if (loan.status != LoanStatus.OVERDUE) {
                false
            } else {
                val endDate = parseInstantOrNull(loan.endDate)
                endDate == null || ChronoUnit.DAYS.between(endDate, Instant.now()) >= 30
            }
        }

        val loanCompletionRate = BehaviorScoringUtils.calculateLoanCompletionRate(
            totalLoansCompleted = totalLoansCompleted,
            totalLoansRequested = totalLoansRequested
        )

        val loanOutstanding = loans.sumOf { it.balanceRemaining }
        val rapidDisbursementAttempts = loans
            .mapNotNull { parseInstantOrNull(it.createdAt) }
            .count { ChronoUnit.HOURS.between(it, Instant.now()) <= 24 }

        return track.copy(
            totalLoansRequested = totalLoansRequested,
            totalLoansApproved = totalLoansApproved,
            totalLoansCompleted = totalLoansCompleted,
            activeLoans = activeLoans,
            overdueLoans = overdueLoans,
            loanDefaultCount = loanDefaultCount,
            loanCompletionRate = loanCompletionRate,
            totalOutstandingAmount = track.totalOutstandingAmount + loanOutstanding,
            rapidDisbursementAttempts = rapidDisbursementAttempts
        )
    }

    private fun parseInstantOrNull(value: String?): Instant? {
        if (value.isNullOrBlank()) return null
        return runCatching { Instant.parse(value) }
            .recoverCatching { LocalDateTime.parse(value).toInstant(ZoneOffset.UTC) }
            .getOrNull()
    }

    private fun calculateLongestStreak(values: List<Boolean>): Int {
        var longest = 0
        var current = 0
        values.forEach { isOnTime ->
            current = if (isOnTime) current + 1 else 0
            if (current > longest) {
                longest = current
            }
        }
        return longest
    }

    private fun calculateCurrentStreak(values: List<Boolean>): Int {
        var streak = 0
        for (index in values.indices.reversed()) {
            if (!values[index]) break
            streak++
        }
        return streak
    }
}


