package com.sanibonani.save.data.local

import androidx.room.*
import androidx.room.Transaction as RoomTransaction
import com.sanibonani.save.domain.model.*
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "beneficiaries",
    primaryKeys = ["id", "group_id", "member_id"],
    indices = [
        Index("group_id"),
        Index("member_id"),
    ],
)
data class BeneficiaryEntity(
    val id: String,
    @ColumnInfo(name = "group_id") val groupId: String,
    @ColumnInfo(name = "member_id") val memberId: String,
    @ColumnInfo(name = "full_name") val fullName: String,
    @ColumnInfo(name = "id_number") val idNumber: String?,
    val relationship: String?,
    @ColumnInfo(name = "date_of_birth") val dateOfBirth: String?,
    @ColumnInfo(name = "is_over_65") val isOver65: Boolean,
    @ColumnInfo(name = "document_url") val documentUrl: String? = null,
    @ColumnInfo(name = "face_photo_url") val facePhotoUrl: String? = null,
    @ColumnInfo(name = "document_status") val documentStatus: DocumentStatus = DocumentStatus.PENDING,
    @ColumnInfo(name = "created_at") val createdAt: String?,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
)

@Dao
interface BeneficiaryDao {
    @Query("SELECT * FROM beneficiaries WHERE member_id = :memberId")
    fun observeBeneficiaries(memberId: String): Flow<List<BeneficiaryEntity>>

    @Query("SELECT * FROM beneficiaries WHERE group_id = :groupId")
    fun observeGroupBeneficiaries(groupId: String): Flow<List<BeneficiaryEntity>>

    @Query("SELECT * FROM beneficiaries WHERE member_id = :memberId")
    suspend fun getMemberBeneficiaries(memberId: String): List<BeneficiaryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBeneficiaries(beneficiaries: List<BeneficiaryEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBeneficiary(beneficiary: BeneficiaryEntity): Long

    @Query("SELECT * FROM beneficiaries WHERE id = :id LIMIT 1")
    suspend fun getBeneficiaryById(id: String): BeneficiaryEntity?

    @Query("DELETE FROM beneficiaries WHERE group_id = :groupId AND member_id = :memberId AND id = :id")
    suspend fun deleteBeneficiary(groupId: String, memberId: String, id: String)

    @Query("DELETE FROM beneficiaries")
    suspend fun clearAll()

    @RoomTransaction
    suspend fun syncBeneficiaries(memberId: String, beneficiaries: List<BeneficiaryEntity>) {
        val currentIds = getMemberBeneficiaries(memberId).map { it.id }.toSet()
        val incomingIds = beneficiaries.map { it.id }.toSet()
        (currentIds - incomingIds).forEach { deleteById(it) }
        if (beneficiaries.isNotEmpty()) upsertBeneficiaries(beneficiaries)
    }

    @Query("DELETE FROM beneficiaries WHERE id = :id")
    suspend fun deleteById(id: String)
}

// ── Mappers ──────────────────────────────────────────────────────────────────

fun BeneficiaryEntity.toModel() = Beneficiary(
    id = id,
    groupId = groupId,
    memberId = memberId,
    fullName = fullName,
    idNumber = idNumber,
    relationship = relationship,
    dateOfBirth = dateOfBirth,
    isOver65 = isOver65,
    documentUrl = documentUrl,
    facePhotoUrl = facePhotoUrl,
    documentStatus = documentStatus,
    createdAt = createdAt
)

fun Beneficiary.toEntity() = BeneficiaryEntity(
    id = id ?: java.util.UUID.randomUUID().toString(),
    groupId = groupId,
    memberId = memberId,
    fullName = fullName,
    idNumber = idNumber,
    relationship = relationship,
    dateOfBirth = dateOfBirth,
    isOver65 = isOver65,
    documentUrl = documentUrl,
    facePhotoUrl = facePhotoUrl,
    documentStatus = documentStatus,
    createdAt = createdAt
)
