package com.sanibonani.save.domain.repository

import com.sanibonani.save.domain.model.*
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for member behavior tracking and fraud detection
 */
interface BehaviorTrackingRepository {

    // Observe behavior tracking
    fun observeMemberBehavior(memberId: String): Flow<Result<MemberBehaviorTrack?>>
    fun observeMemberBehaviorByIdNumber(idNumber: String, groupId: String): Flow<Result<MemberBehaviorTrack?>>
    fun observeGroupMembersBehavior(groupId: String): Flow<Result<List<MemberBehaviorTrack>>>
    fun observeHighRiskMembers(groupId: String): Flow<Result<List<MemberBehaviorTrack>>>
    fun observeFlaggedMembers(groupId: String): Flow<Result<List<MemberBehaviorTrack>>>
    fun observeSuspendedMembers(groupId: String): Flow<Result<List<MemberBehaviorTrack>>>

    // Get behavior tracking synchronously
    suspend fun getMemberBehavior(memberId: String): Result<MemberBehaviorTrack?>
    suspend fun getMemberBehaviorByIdNumber(idNumber: String, groupId: String): Result<MemberBehaviorTrack?>

    // Calculate and update behavior scores
    suspend fun calculateAndUpdateMemberBehavior(memberId: String, groupId: String): Result<MemberBehaviorTrack>
    suspend fun recalculateGroupBehaviorScores(groupId: String): Result<Unit>

    // Save behavior tracking
    suspend fun saveBehaviorTrack(track: MemberBehaviorTrack): Result<Unit>
    suspend fun saveBehaviorTracks(tracks: List<MemberBehaviorTrack>): Result<Unit>

    // Fraud detection events
    fun observeFraudEventsByMember(memberId: String): Flow<Result<List<FraudDetectionEvent>>>
    suspend fun recordFraudEvent(event: FraudDetectionEvent): Result<Unit>
    suspend fun resolveFraudEvent(eventId: String, actionTaken: String): Result<Unit>
    fun observeUnresolvedFraudEvents(groupId: String): Flow<Result<List<FraudDetectionEvent>>>

    // Member flagging
    suspend fun flagMemberForReview(memberId: String, reason: String, reviewNotes: String? = null): Result<Unit>
    suspend fun unflagMember(memberId: String): Result<Unit>
    suspend fun suspendMember(memberId: String, reason: String): Result<Unit>
    suspend fun unsuspendMember(memberId: String): Result<Unit>

    // Analytics and reporting
    fun observeBehaviorAnalytics(groupId: String): Flow<Result<BehaviorAnalyticsSummary?>>
    suspend fun calculateBehaviorAnalytics(groupId: String): Result<BehaviorAnalyticsSummary>
    suspend fun getMembersBehaviorStats(groupId: String): Result<Map<String, Any>>
}

