package com.sanibonani.save.data.repository.dynamics

import com.sanibonani.save.data.local.SanibonaniDatabase
import com.sanibonani.save.data.repository.BaseRepository
import com.sanibonani.save.data.repository.mappers.*
import com.sanibonani.save.domain.utils.BehaviorScoringUtils
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.dynamics.BehaviorTrackingRepository
import com.sanibonani.save.domain.repository.credit.LoanRepository
import com.sanibonani.save.domain.repository.MemberRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import javax.inject.Inject
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

class BehaviorTrackingRepositoryImpl @Inject constructor(
    private val supabaseProvider: javax.inject.Provider<SupabaseClient>,
    private val dbProvider: javax.inject.Provider<SanibonaniDatabase>,
    private val memberRepository: MemberRepository,
    private val loanRepository: LoanRepository
) : BaseRepository("BehaviorTrackingRepository"), BehaviorTrackingRepository {

    private val supabase: SupabaseClient get() = supabaseProvider.get()
    private val db: SanibonaniDatabase get() = dbProvider.get()

    override fun observeMemberBehavior(memberId: String): Flow<Result<MemberBehaviorTrack?>> = db.memberBehaviorTrackDao().observeByMemberId(memberId).map { Result.success(it?.toModel()) }

    override fun observeMemberBehaviorByIdNumber(idNumber: String, groupId: String): Flow<Result<MemberBehaviorTrack?>> = db.memberBehaviorTrackDao().observeByGroupId(groupId).map { list -> Result.success(list.find { it.memberIdNumber == idNumber }?.toModel()) }

    override fun observeGroupMembersBehavior(groupId: String): Flow<Result<List<MemberBehaviorTrack>>> = db.memberBehaviorTrackDao().observeByGroupId(groupId).map { list -> Result.success(list.map { it.toModel() }) }

    override fun observeHighRiskMembers(groupId: String): Flow<Result<List<MemberBehaviorTrack>>> = db.memberBehaviorTrackDao().observeByGroupId(groupId).map { list -> Result.success(list.filter { it.fraudRiskLevel == "HIGH" || it.fraudRiskLevel == "CRITICAL" }.map { it.toModel() }) }

    override fun observeFlaggedMembers(groupId: String): Flow<Result<List<MemberBehaviorTrack>>> = db.memberBehaviorTrackDao().observeByGroupId(groupId).map { list -> Result.success(list.filter { it.isFlaggedForReview }.map { it.toModel() }) }

    override fun observeSuspendedMembers(groupId: String): Flow<Result<List<MemberBehaviorTrack>>> = db.memberBehaviorTrackDao().observeByGroupId(groupId).map { list -> Result.success(list.filter { it.isSuspended }.map { it.toModel() }) }

    override suspend fun getMemberBehavior(memberId: String): Result<MemberBehaviorTrack?> = runCatching { db.memberBehaviorTrackDao().observeByMemberId(memberId).first()?.toModel() }

    override suspend fun getMemberBehaviorByIdNumber(idNumber: String, groupId: String): Result<MemberBehaviorTrack?> = runCatching { db.memberBehaviorTrackDao().observeByGroupId(groupId).first().find { it.memberIdNumber == idNumber }?.toModel() }

    override suspend fun calculateAndUpdateMemberBehavior(memberId: String, groupId: String): Result<MemberBehaviorTrack> = runCatching {
        val member = memberRepository.getMemberById(memberId).getOrThrow()
        var track = getMemberBehavior(memberId).getOrNull() ?: createDefaultBehaviorTrack(member, groupId)
        track = updateContributionMetrics(track, memberId)
        track = updateLoanMetrics(track, memberId)
        track = track.copy(behaviorScore = BehaviorScoringUtils.calculateBehaviorScore(track), fraudScore = BehaviorScoringUtils.calculateFraudScore(track), lastActivityAt = Instant.now().toString())
        db.memberBehaviorTrackDao().upsert(track.toEntity())
        track
    }

    override suspend fun recalculateGroupBehaviorScores(groupId: String): Result<Unit> = runCatching { memberRepository.getGroupMembers(groupId).first().getOrNull()?.forEach { it.id?.let { id -> calculateAndUpdateMemberBehavior(id, groupId) } } }

    override suspend fun saveBehaviorTrack(track: MemberBehaviorTrack): Result<Unit> = runCatching { db.memberBehaviorTrackDao().upsert(track.toEntity()) }

    override suspend fun saveBehaviorTracks(tracks: List<MemberBehaviorTrack>): Result<Unit> = runCatching { tracks.forEach { saveBehaviorTrack(it) } }

    override fun observeFraudEventsByMember(memberId: String): Flow<Result<List<FraudDetectionEvent>>> = db.fraudDetectionEventDao().observeByMemberId(memberId).map { Result.success(it.map { e -> e.toModel() }) }

    override suspend fun recordFraudEvent(event: FraudDetectionEvent): Result<Unit> = runCatching { db.fraudDetectionEventDao().upsert(event.toEntity()) }

    override suspend fun resolveFraudEvent(eventId: String, actionTaken: String): Result<Unit> = runCatching { }

    override fun observeUnresolvedFraudEvents(groupId: String): Flow<Result<List<FraudDetectionEvent>>> = db.fraudDetectionEventDao().observeByGroupId(groupId).map { list -> Result.success(list.filter { !it.resolved }.map { it.toModel() }) }

    override suspend fun flagMemberForReview(memberId: String, reason: String, reviewNotes: String?): Result<Unit> = runCatching {
        val track = getMemberBehavior(memberId).getOrNull() ?: throw Exception("Not found")
        db.memberBehaviorTrackDao().upsert(track.copy(isFlaggedForReview = true, reviewNotes = reviewNotes ?: reason).toEntity())
    }

    override suspend fun unflagMember(memberId: String): Result<Unit> = runCatching {
        val track = getMemberBehavior(memberId).getOrNull() ?: throw Exception("Not found")
        db.memberBehaviorTrackDao().upsert(track.copy(isFlaggedForReview = false).toEntity())
    }

    override suspend fun suspendMember(memberId: String, reason: String): Result<Unit> = runCatching {
        val track = getMemberBehavior(memberId).getOrNull() ?: throw Exception("Not found")
        db.memberBehaviorTrackDao().upsert(track.copy(isSuspended = true, suspensionReason = reason, memberStatus = BehaviorStatus.SUSPENDED).toEntity())
    }

    override suspend fun unsuspendMember(memberId: String): Result<Unit> = runCatching {
        val track = getMemberBehavior(memberId).getOrNull() ?: throw Exception("Not found")
        db.memberBehaviorTrackDao().upsert(track.copy(isSuspended = false, suspensionReason = null).toEntity())
    }

    override fun observeBehaviorAnalytics(groupId: String): Flow<Result<BehaviorAnalyticsSummary?>> = db.behaviorAnalyticsSummaryDao().observeByGroupId(groupId).map { Result.success(it?.toModel()) }

    override suspend fun calculateBehaviorAnalytics(groupId: String): Result<BehaviorAnalyticsSummary> = runCatching {
        val summary = BehaviorAnalyticsSummary(groupId = groupId, calculatedAt = Instant.now().toString())
        db.behaviorAnalyticsSummaryDao().upsert(summary.toEntity())
        summary
    }

    override suspend fun getMembersBehaviorStats(groupId: String): Result<JsonObject> = runCatching { JsonObject(emptyMap()) }

    private fun createDefaultBehaviorTrack(member: Member, groupId: String): MemberBehaviorTrack = MemberBehaviorTrack(id = java.util.UUID.randomUUID().toString(), memberId = member.id ?: "", memberIdNumber = member.idNumber ?: "", groupId = groupId, joinedAt = member.joinedAt, createdAt = Instant.now().toString())

    private suspend fun updateContributionMetrics(track: MemberBehaviorTrack, memberId: String): MemberBehaviorTrack = track

    private suspend fun updateLoanMetrics(track: MemberBehaviorTrack, memberId: String): MemberBehaviorTrack {
        val loans = loanRepository.getMemberLoans(memberId).first().getOrDefault(emptyList())
        return track.copy(activeLoans = loans.count { it.status == LoanStatus.ACTIVE })
    }
}
