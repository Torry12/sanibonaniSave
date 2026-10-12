package com.sanibonani.save.data.local

import androidx.room.*
import androidx.room.Transaction as RoomTransaction
import com.sanibonani.save.domain.model.*
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "member_documents",
    indices = [
        Index("member_id"),
        Index("group_id"),
    ],
)
data class MemberDocumentEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "member_id") val memberId: String,
    @ColumnInfo(name = "group_id") val groupId: String,
    val label: String,
    @ColumnInfo(name = "document_url") val documentUrl: String,
    @ColumnInfo(name = "document_type") val documentType: String?,
    val status: DocumentStatus,
    @ColumnInfo(name = "created_at") val createdAt: String?,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
)

@Dao
interface MemberDocumentDao {
    @Query("SELECT * FROM member_documents WHERE member_id = :memberId")
    fun observeDocuments(memberId: String): Flow<List<MemberDocumentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDocuments(documents: List<MemberDocumentEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDocument(document: MemberDocumentEntity): Long

    @Query("DELETE FROM member_documents WHERE member_id = :memberId")
    suspend fun deleteDocumentsByMemberId(memberId: String)

    @Query("DELETE FROM member_documents")
    suspend fun clearAll()

    @RoomTransaction
    suspend fun syncDocuments(memberId: String, documents: List<MemberDocumentEntity>) {
        deleteDocumentsByMemberId(memberId)
        upsertDocuments(documents)
    }

    @Query("DELETE FROM member_documents WHERE group_id = :groupId")
    suspend fun deleteDocumentsByGroupId(groupId: String)

    @Query("DELETE FROM member_documents WHERE id = :id")
    suspend fun deleteById(id: String)
}

// ── Mappers ──────────────────────────────────────────────────────────────────

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

fun MemberDocument.toEntity() = MemberDocumentEntity(
    id = id ?: java.util.UUID.randomUUID().toString(),
    memberId = memberId,
    groupId = groupId,
    label = label,
    documentUrl = documentUrl,
    documentType = documentType,
    status = status,
    createdAt = createdAt
)
