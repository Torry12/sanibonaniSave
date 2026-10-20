package com.sanibonani.save.data.repository.admin

import com.sanibonani.save.data.repository.BaseRepository

import com.sanibonani.save.data.local.SanibonaniDatabase
import com.sanibonani.save.data.local.toEntity
import com.sanibonani.save.data.local.toModel
import com.sanibonani.save.domain.utils.BehaviorScoringUtils
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.admin.BehaviorTrackingRepository
import com.sanibonani.save.domain.repository.credit.LoanRepository
import com.sanibonani.save.domain.repository.equity.MemberContributionRepository
import com.sanibonani.save.domain.repository.member.MemberRepository
import io.github.jan.supabase.SupabaseClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import java.time.Instant
import com.sanibonani.save.domain.utils.safeRunCatching

class BehaviorTrackingRepositoryImpl @Inject constructor(
    private val supabaseProvider: javax.inject.Provider<SupabaseClient>,
    private val dbProvider: javax.inject.Provider<SanibonaniDatabase>,
    private val memberRepository: MemberRepository,
    private val contributionRepository: MemberContributionRepository,
    private val loanRepository: LoanRepository
) : BaseRepository("BehaviorTrackingRepository"), BehaviorTrackingRepository {

    private val supabase: SupabaseClient get() = supabaseProvider.get()
    private val db: SanibonaniDatabase get() = dbProvider.get()

    override fun observeGroupMembersBehavior(groupId: String): Flow<Result<List<MemberBehaviorTrack>>> = db.memberBehaviorTrackDao().observeGroupMembersBehavior(groupId).map { list -> Result.success(list.map { it.toModel() }) }

    override suspend fun getMemberBehavior(memberId: String): Result<MemberBehaviorTrack?> = safeRunCatching { db.memberBehaviorTrackDao().getByMemberId(memberId)?.toModel() }

    override suspend fun calculateAndUpdateMemberBehavior(memberId: String, groupId: String): Result<MemberBehaviorTrack> = safeRunCatching {
        val member = memberRepository.getMemberById(memberId).getOrThrow()
        var track = getMemberBehavior(memberId).getOrNull() ?: createDefaultBehaviorTrack(member, groupId)
        track = updateContributionMetrics(track, memberId)
        track = updateLoanMetrics(track, memberId)
        track = track.copy(behaviorScore = BehaviorScoringUtils.calculateBehaviorScore(track), fraudScore = BehaviorScoringUtils.calculateFraudScore(track), lastActivityAt = Instant.now().toString())
        db.memberBehaviorTrackDao().upsert(track.toEntity())
        track
    }

    override fun observeFraudEventsByMember(memberId: String): Flow<Result<List<FraudDetectionEvent>>> = db.fraudDetectionEventDao().observeEventsByMember(memberId).map { Result.success(it.map { e -> e.toModel() }) }

    override suspend fun recordFraudEvent(event: FraudDetectionEvent): Result<Unit> = safeRunCatching { db.fraudDetectionEventDao().upsert(event.toEntity()) }

    override suspend fun resolveFraudEvent(eventId: String, actionTaken: String): Result<Unit> = safeRunCatching {
        db.fraudDetectionEventDao().getById(eventId)?.let { event ->
            db.fraudDetectionEventDao().upsert(event.copy(actionTaken = actionTaken, resolved = true))
        }
    }

    override fun observeUnresolvedFraudEvents(groupId: String): Flow<Result<List<FraudDetectionEvent>>> = db.fraudDetectionEventDao().observeUnresolvedEventsByGroup(groupId).map { list -> Result.success(list.map { it.toModel() }) }


    override suspend fun flagMemberForReview(memberId: String, reason: String, reviewNotes: String?): Result<Unit> = safeRunCatching {
        val track = getMemberBehavior(memberId).getOrNull() ?: throw Exception("Not found")
        db.memberBehaviorTrackDao().upsert(track.copy(isFlaggedForReview = true, reviewNotes = reviewNotes ?: reason).toEntity())
    }

    override suspend fun unflagMember(memberId: String): Result<Unit> = safeRunCatching {
        val track = getMemberBehavior(memberId).getOrNull() ?: throw Exception("Not found")
        db.memberBehaviorTrackDao().upsert(track.copy(isFlaggedForReview = false).toEntity())
    }

    override suspend fun suspendMember(memberId: String, reason: String): Result<Unit> = safeRunCatching {
        val track = getMemberBehavior(memberId).getOrNull() ?: throw Exception("Not found")
        db.memberBehaviorTrackDao().upsert(track.copy(isSuspended = true, suspensionReason = reason, memberStatus = BehaviorStatus.SUSPENDED).toEntity())
    }

    override suspend fun unsuspendMember(memberId: String): Result<Unit> = safeRunCatching {
        val track = getMemberBehavior(memberId).getOrNull() ?: throw Exception("Not found")
        db.memberBehaviorTrackDao().upsert(track.copy(isSuspended = false, suspensionReason = null).toEntity())
    }

    /**
     * Generates a dataset formatted for Supervised Fine-Tuning (SFT) of Azure AI Foundry models.
     *
     * AI Optimization: This method exports raw financial behavior tracks as JSONL training examples.
     * The system prompt and user content are designed to train the model to perform risk
     * assessment based on real-world stokvel dynamics.
     */
    override suspend fun generateFinetuningDataset(groupId: String): Result<List<SftTrainingExample>> = retryWithExponentialBackoff {
        val tracks = observeGroupMembersBehavior(groupId).first().getOrThrow()

        tracks.map { track ->
            val systemPrompt = "You are a financial risk analyst for a savings group platform. Predict the risk level of the member based on their behavior metrics."
            val userContent = """
                Member Metrics:
                - Contributions: ${track.totalContributions} (${track.onTimeContributions} on time)
                - Overdue items: ${track.overdueCount}
                - Loans: ${track.totalLoansRequested} (${track.loanDefaultCount} defaults)
                - Fraud indicators: ${track.duplicateTransactionCount} duplicates, velocity fail: ${track.velocityCheckFailed}
            """.trimIndent()

            // AI Note: The 'assistantContent' represents the "Ground Truth" labels derived
            // from actual ledger integrity audits and fraudulent patterns.
            val assistantContent = "Risk Level: ${track.fraudRiskLevel}. Behavior Status: ${track.memberStatus}. Recommended Action: ${if (track.isSuspended) "SUSPENDED" else if (track.isFlaggedForReview) "REVIEW" else "MONITOR"}."

            SftTrainingExample(
                messages = listOf(
                    SftMessage("system", systemPrompt),
                    SftMessage("user", userContent),
                    SftMessage("assistant", assistantContent)
                )
            )
        }
    }

    private fun createDefaultBehaviorTrack(member: Member, groupId: String): MemberBehaviorTrack = MemberBehaviorTrack(id = java.util.UUID.randomUUID().toString(), memberId = member.id ?: "", memberIdNumber = member.idNumber ?: "", groupId = groupId, joinedAt = member.joinedAt, createdAt = Instant.now().toString())

    private suspend fun updateContributionMetrics(track: MemberBehaviorTrack, memberId: String): MemberBehaviorTrack {
        // A failed history read carries no amounts, so it cannot be scored as an empty one: the row
        // would go out saying this member has never contributed and the ledger total is R0.00.
        val contributions = contributionRepository.getMemberContributions(memberId, track.groupId)
            .first().getOrThrow()

        val paid = contributions.filter { it.status == ContributionStatus.PAID || it.status == ContributionStatus.PARTIAL }
        val totalAmount = paid.fold(Money.ZERO) { acc, c -> acc + c.amount }

        val onTime = paid.count { c -> val paidAt = c.paidAt; paidAt != null && paidAt <= c.dueDate }
        val late = paid.count { c -> val paidAt = c.paidAt; paidAt != null && paidAt > c.dueDate }
        val missed = contributions.count { it.status == ContributionStatus.OVERDUE }

        return track.copy(
            totalContributions = contributions.size,
            totalAmountContributed = totalAmount,
            onTimeContributions = onTime,
            lateContributions = late,
            overdueCount = missed
        )
    }

    private suspend fun updateLoanMetrics(track: MemberBehaviorTrack, memberId: String): MemberBehaviorTrack {
        val loans = loanRepository.getMemberLoans(memberId).first().getOrThrow()
        return track.copy(activeLoans = loans.count { it.status == LoanStatus.ACTIVE })
    }
}

