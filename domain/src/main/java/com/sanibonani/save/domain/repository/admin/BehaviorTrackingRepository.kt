package com.sanibonani.save.domain.repository.admin

import com.sanibonani.save.domain.model.*
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for member behavior tracking and fraud detection
 */
interface BehaviorTrackingRepository {

    // Observe behavior tracking
    fun observeGroupMembersBehavior(groupId: String): Flow<Result<List<MemberBehaviorTrack>>>

    // Get behavior tracking synchronously
    suspend fun getMemberBehavior(memberId: String): Result<MemberBehaviorTrack?>

    // Calculate and update behavior scores
    suspend fun calculateAndUpdateMemberBehavior(memberId: String, groupId: String): Result<MemberBehaviorTrack>

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

    // ML Finetuning
    suspend fun generateFinetuningDataset(groupId: String): Result<List<SftTrainingExample>>
}
