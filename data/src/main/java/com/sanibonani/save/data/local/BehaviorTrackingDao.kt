package com.sanibonani.save.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

// ─────────────────────────────────────────────────────────────────────────────
// BEHAVIOR TRACKING DAOs
// ─────────────────────────────────────────────────────────────────────────────

@Dao
interface MemberBehaviorTrackDao {

    // Create/Update operations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: MemberBehaviorTrackEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<MemberBehaviorTrackEntity>)

    @Update
    suspend fun update(entity: MemberBehaviorTrackEntity)

    @Upsert
    suspend fun upsert(entity: MemberBehaviorTrackEntity)

    @Upsert
    suspend fun upsertAll(entities: List<MemberBehaviorTrackEntity>)

    @Delete
    suspend fun delete(entity: MemberBehaviorTrackEntity)

    // Read operations - single member
    @Query("SELECT * FROM member_behavior_track WHERE member_id = :memberId")
    suspend fun getByMemberId(memberId: String): MemberBehaviorTrackEntity?

    @Query("SELECT * FROM member_behavior_track WHERE member_id_number = :idNumber AND group_id = :groupId")
    suspend fun getByIdNumber(idNumber: String, groupId: String): MemberBehaviorTrackEntity?

    @Query("SELECT * FROM member_behavior_track WHERE id = :id")
    suspend fun getById(id: String): MemberBehaviorTrackEntity?

    // Read operations - flows (reactive)
    @Query("SELECT * FROM member_behavior_track WHERE member_id = :memberId")
    fun observeByMemberId(memberId: String): Flow<MemberBehaviorTrackEntity?>

    @Query("SELECT * FROM member_behavior_track WHERE member_id_number = :idNumber AND group_id = :groupId")
    fun observeByIdNumber(idNumber: String, groupId: String): Flow<MemberBehaviorTrackEntity?>

    @Query("SELECT * FROM member_behavior_track WHERE fraud_risk_level = :riskLevel")
    fun observeByFraudRiskLevel(riskLevel: String): Flow<List<MemberBehaviorTrackEntity>>

    @Query("SELECT * FROM member_behavior_track WHERE is_flagged_for_review = 1")
    fun observeFlaggedForReview(): Flow<List<MemberBehaviorTrackEntity>>

    @Query("SELECT * FROM member_behavior_track WHERE is_suspended = 1")
    fun observeSuspendedMembers(): Flow<List<MemberBehaviorTrackEntity>>

    // Group queries
    @Query("SELECT * FROM member_behavior_track WHERE group_id = :groupId ORDER BY behavior_score DESC")
    suspend fun getGroupMembersBehavior(groupId: String): List<MemberBehaviorTrackEntity>

    @Query("SELECT * FROM member_behavior_track WHERE group_id = :groupId ORDER BY behavior_score DESC")
    fun observeGroupMembersBehavior(groupId: String): Flow<List<MemberBehaviorTrackEntity>>

    @Query("SELECT * FROM member_behavior_track WHERE group_id = :groupId AND fraud_risk_level IN ('HIGH', 'CRITICAL') ORDER BY fraud_score DESC")
    fun observeHighRiskMembers(groupId: String): Flow<List<MemberBehaviorTrackEntity>>

    @Query("SELECT * FROM member_behavior_track WHERE group_id = :groupId AND is_flagged_for_review = 1")
    fun observeFlaggedMembersInGroup(groupId: String): Flow<List<MemberBehaviorTrackEntity>>

    // Statistics
    @Query("SELECT COUNT(*) FROM member_behavior_track WHERE group_id = :groupId")
    suspend fun countByGroup(groupId: String): Int

    @Query("SELECT COUNT(*) FROM member_behavior_track WHERE group_id = :groupId AND member_status = :status")
    suspend fun countByGroupAndStatus(groupId: String, status: String): Int

    @Query("SELECT COUNT(*) FROM member_behavior_track WHERE group_id = :groupId AND fraud_risk_level = :riskLevel")
    suspend fun countByFraudRiskLevel(groupId: String, riskLevel: String): Int

    @Query("SELECT AVG(behavior_score) FROM member_behavior_track WHERE group_id = :groupId")
    suspend fun getAverageBehaviorScore(groupId: String): Double?

    @Query("SELECT AVG(fraud_score) FROM member_behavior_track WHERE group_id = :groupId")
    suspend fun getAverageFraudScore(groupId: String): Double?

    // Sync operations
    @Query("SELECT * FROM member_behavior_track WHERE group_id = :groupId")
    suspend fun getAllByGroup(groupId: String): List<MemberBehaviorTrackEntity>

    @Query("DELETE FROM member_behavior_track WHERE group_id = :groupId")
    suspend fun deleteByGroup(groupId: String)

    @Query("DELETE FROM member_behavior_track")
    suspend fun deleteAll()
}

@Dao
interface FraudDetectionEventDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: FraudDetectionEventEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<FraudDetectionEventEntity>)

    @Update
    suspend fun update(entity: FraudDetectionEventEntity)

    @Upsert
    suspend fun upsert(entity: FraudDetectionEventEntity)

    @Delete
    suspend fun delete(entity: FraudDetectionEventEntity)

    @Query("SELECT * FROM fraud_detection_events WHERE member_id = :memberId ORDER BY created_at DESC")
    suspend fun getEventsByMember(memberId: String): List<FraudDetectionEventEntity>

    @Query("SELECT * FROM fraud_detection_events WHERE member_id = :memberId ORDER BY created_at DESC")
    fun observeEventsByMember(memberId: String): Flow<List<FraudDetectionEventEntity>>

    @Query("SELECT * FROM fraud_detection_events WHERE group_id = :groupId AND resolved = 0 ORDER BY severity, created_at DESC")
    fun observeUnresolvedEventsByGroup(groupId: String): Flow<List<FraudDetectionEventEntity>>

    @Query("SELECT * FROM fraud_detection_events WHERE id = :id")
    suspend fun getById(id: String): FraudDetectionEventEntity?

    @Query("SELECT COUNT(*) FROM fraud_detection_events WHERE member_id = :memberId AND resolved = 0")
    suspend fun countUnresolvedByMember(memberId: String): Int

    @Query("SELECT COUNT(*) FROM fraud_detection_events WHERE member_id = :memberId AND created_at > :afterDate")
    suspend fun countRecentEventsByMember(memberId: String, afterDate: String): Int

    @Query("DELETE FROM fraud_detection_events WHERE group_id = :groupId")
    suspend fun deleteByGroup(groupId: String)

    @Query("DELETE FROM fraud_detection_events")
    suspend fun deleteAll()
}

@Dao
interface BehaviorAnalyticsSummaryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: BehaviorAnalyticsSummaryEntity)

    @Update
    suspend fun update(entity: BehaviorAnalyticsSummaryEntity)

    @Upsert
    suspend fun upsert(entity: BehaviorAnalyticsSummaryEntity)

    @Delete
    suspend fun delete(entity: BehaviorAnalyticsSummaryEntity)

    @Query("SELECT * FROM behavior_analytics_summary WHERE group_id = :groupId")
    suspend fun getByGroup(groupId: String): BehaviorAnalyticsSummaryEntity?

    @Query("SELECT * FROM behavior_analytics_summary WHERE group_id = :groupId")
    fun observeByGroup(groupId: String): Flow<BehaviorAnalyticsSummaryEntity?>

    @Query("DELETE FROM behavior_analytics_summary WHERE group_id = :groupId")
    suspend fun deleteByGroup(groupId: String)

    @Query("DELETE FROM behavior_analytics_summary")
    suspend fun deleteAll()
}

