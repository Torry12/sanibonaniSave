package com.sanibonani.save.data.local

import androidx.room.*
import androidx.room.Transaction as RoomTransaction
import com.sanibonani.save.domain.model.*
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "beneficiary_payout_claims",
    indices = [
        Index("group_id"),
        Index("member_id"),
        Index("beneficiary_id"),
        Index("status"),
    ],
)
data class BeneficiaryClaimEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "group_id")         val groupId: String,
    @ColumnInfo(name = "member_id")        val memberId: String,
    @ColumnInfo(name = "beneficiary_id")   val beneficiaryId: String,
    @ColumnInfo(name = "beneficiary_name") val beneficiaryName: String,
    @ColumnInfo(name = "face_photo_url")    val facePhotoUrl: String? = null,
    @ColumnInfo(name = "face_photo_status") val facePhotoStatus: DocumentStatus = DocumentStatus.PENDING,
    @ColumnInfo(name = "beneficiary_id_url") val beneficiaryIdUrl: String? = null,
    @ColumnInfo(name = "beneficiary_id_status") val beneficiaryIdStatus: DocumentStatus = DocumentStatus.PENDING,
    @ColumnInfo(name = "death_certificate_url") val deathCertificateUrl: String? = null,
    @ColumnInfo(name = "death_certificate_status") val deathCertificateStatus: DocumentStatus = DocumentStatus.PENDING,
    @ColumnInfo(name = "member_id_url")      val memberIdUrl: String? = null,
    @ColumnInfo(name = "member_id_status")   val memberIdStatus: DocumentStatus = DocumentStatus.PENDING,
    @ColumnInfo(name = "cause_of_death")   val causeOfDeath: String,
    @ColumnInfo(name = "date_of_death")    val dateOfDeath: String,
    @ColumnInfo(name = "claim_amount")     val claimAmount: Money,
    @ColumnInfo(name = "bank_name")        val bankName: String,
    @ColumnInfo(name = "account_no")       val accountNo: String,
    @ColumnInfo(name = "branch_code")      val branchCode: String,
    @ColumnInfo(name = "account_holder")   val accountHolder: String,
    val notes: String? = null,
    val status: BeneficiaryClaimStatus = BeneficiaryClaimStatus.SUBMITTED,
    @ColumnInfo(name = "reviewed_by")      val reviewedBy: String? = null,
    @ColumnInfo(name = "reviewed_at")      val reviewedAt: String? = null,
    @ColumnInfo(name = "admin_notes")      val adminNotes: String? = null,
    @ColumnInfo(name = "rejection_reason") val rejectionReason: String? = null,
    @ColumnInfo(name = "created_at")       val createdAt: String? = null,
    @ColumnInfo(name = "updated_at")       val updatedAt: Long = System.currentTimeMillis(),
)

@Dao
interface BeneficiaryClaimDao {
    @Query("SELECT * FROM beneficiary_payout_claims WHERE member_id = :memberId AND group_id = :groupId ORDER BY created_at DESC")
    fun observeClaimsForMember(memberId: String, groupId: String): Flow<List<BeneficiaryClaimEntity>>

    @Query("SELECT * FROM beneficiary_payout_claims WHERE group_id = :groupId ORDER BY created_at DESC")
    fun observeClaimsForGroup(groupId: String): Flow<List<BeneficiaryClaimEntity>>

    @Query("SELECT * FROM beneficiary_payout_claims WHERE group_id = :groupId")
    suspend fun getGroupClaims(groupId: String): List<BeneficiaryClaimEntity>

    @Query("SELECT * FROM beneficiary_payout_claims WHERE status = 'ESCALATED' ORDER BY created_at DESC")
    fun observeEscalatedClaims(): Flow<List<BeneficiaryClaimEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertClaim(claim: BeneficiaryClaimEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertClaims(claims: List<BeneficiaryClaimEntity>): List<Long>

    @Query("SELECT * FROM beneficiary_payout_claims WHERE id = :id LIMIT 1")
    suspend fun getClaimById(id: String): BeneficiaryClaimEntity?

    @Query("DELETE FROM beneficiary_payout_claims WHERE group_id = :groupId")
    suspend fun deleteByGroupId(groupId: String)

    @Query("DELETE FROM beneficiary_payout_claims")
    suspend fun clearAll()

    @RoomTransaction
    suspend fun syncForGroup(groupId: String, claims: List<BeneficiaryClaimEntity>) {
        val current = getGroupClaims(groupId).map { it.id }.toSet()
        val incoming = claims.map { it.id }.toSet()
        (current - incoming).forEach { deleteClaim(it) }
        upsertClaims(claims)
    }

    @Query("SELECT * FROM beneficiary_payout_claims WHERE member_id = :memberId AND group_id = :groupId")
    suspend fun getMemberClaims(memberId: String, groupId: String): List<BeneficiaryClaimEntity>

    @RoomTransaction
    suspend fun syncForMember(memberId: String, groupId: String, claims: List<BeneficiaryClaimEntity>) {
        val current = getMemberClaims(memberId, groupId).map { it.id }.toSet()
        val incoming = claims.map { it.id }.toSet()
        (current - incoming).forEach { deleteClaim(it) }
        upsertClaims(claims)
    }

    @Query("DELETE FROM beneficiary_payout_claims WHERE id = :id")
    suspend fun deleteClaim(id: String)
}

// ── Mappers ──────────────────────────────────────────────────────────────────

fun BeneficiaryClaimEntity.toModel() = BeneficiaryPayoutClaim(
    id = id,
    groupId = groupId,
    memberId = memberId,
    beneficiaryId = beneficiaryId,
    beneficiaryName = beneficiaryName,
    facePhotoUrl = facePhotoUrl,
    facePhotoStatus = facePhotoStatus,
    beneficiaryIdUrl = beneficiaryIdUrl,
    beneficiaryIdStatus = beneficiaryIdStatus,
    deathCertificateUrl = deathCertificateUrl,
    deathCertificateStatus = deathCertificateStatus,
    memberIdUrl = memberIdUrl,
    memberIdStatus = memberIdStatus,
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

fun BeneficiaryPayoutClaim.toEntity() = BeneficiaryClaimEntity(
    id = id ?: java.util.UUID.randomUUID().toString(),
    groupId = groupId,
    memberId = memberId,
    beneficiaryId = beneficiaryId,
    beneficiaryName = beneficiaryName,
    facePhotoUrl = facePhotoUrl,
    facePhotoStatus = facePhotoStatus,
    beneficiaryIdUrl = beneficiaryIdUrl,
    beneficiaryIdStatus = beneficiaryIdStatus,
    deathCertificateUrl = deathCertificateUrl,
    deathCertificateStatus = deathCertificateStatus,
    memberIdUrl = memberIdUrl,
    memberIdStatus = memberIdStatus,
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
