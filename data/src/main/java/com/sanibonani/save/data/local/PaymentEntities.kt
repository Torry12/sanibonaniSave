package com.sanibonani.save.data.local

import androidx.room.*
import com.sanibonani.save.domain.model.*
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "payments",
    indices = [
        Index("member_id"),
        Index("group_id"),
        Index("status"),
        Index("payment_type"),
        Index("created_at"),
    ],
)
data class PaymentEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "member_id") val memberId: String,
    @ColumnInfo(name = "group_id") val groupId: String,
    val amount: Money,
    @ColumnInfo(name = "payment_type") val paymentType: PaymentType,
    @ColumnInfo(name = "payment_method") val paymentMethod: PaymentMethod,
    @ColumnInfo(name = "transaction_id") val transactionId: String?,
    val status: PaymentStatus,
    @ColumnInfo(name = "processed_at") val processedAt: String?,
    @ColumnInfo(name = "created_at") val createdAt: String?,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
)

@Dao
interface PaymentDao {
    @Query("SELECT * FROM payments WHERE group_id = :groupId ORDER BY created_at DESC")
    fun observeGroupPayments(groupId: String): Flow<List<PaymentEntity>>

    @Query("SELECT * FROM payments WHERE group_id = :groupId")
    suspend fun getGroupPayments(groupId: String): List<PaymentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPayments(payments: List<PaymentEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPayment(payment: PaymentEntity): Long

    @Query("DELETE FROM payments WHERE group_id = :groupId")
    suspend fun deletePaymentsByGroupId(groupId: String)

    @Query("DELETE FROM payments")
    suspend fun clearAll()

    @androidx.room.Transaction
    suspend fun syncPayments(groupId: String, payments: List<PaymentEntity>) {
        val current = getGroupPayments(groupId).map { it.id }.toSet()
        val incoming = payments.map { it.id }.toSet()
        (current - incoming).forEach { deletePayment(it) }
        upsertPayments(payments)
    }

    @Query("DELETE FROM payments WHERE id = :id")
    suspend fun deletePayment(id: String)

    @Query("SELECT * FROM payments WHERE payment_type = 'platform_fee' ORDER BY created_at DESC")
    fun observePlatformPayments(): Flow<List<PaymentEntity>>
}
