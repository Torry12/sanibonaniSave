package com.sanibonani.save.data.repository

import com.sanibonani.save.data.local.PaymentEntity
import com.sanibonani.save.data.local.SanibonaniDatabase
import com.sanibonani.save.domain.event.EventBus
import com.sanibonani.save.domain.event.PaymentProcessedEvent
import com.sanibonani.save.domain.model.Payment
import com.sanibonani.save.domain.repository.PaymentRepository
import com.sanibonani.save.domain.utils.OperationKeys
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

class PaymentRepositoryImpl @Inject constructor(
    private val supabase: SupabaseClient,
    private val db: SanibonaniDatabase
) : BaseRepository("PaymentRepository"), PaymentRepository {
    private val PAYMENT_COLUMNS_SAFE = """
        id, member_id, group_id, amount, payment_type,
        payment_method, transaction_id, status, processed_at, created_at
    """.trimIndent().replace("\n", "")

    override suspend fun recordPayment(payment: Payment): Result<String> = runCatching {
        retryWithExponentialBackoff {
            val paymentId = payment.id ?: OperationKeys.stableUuid(
                "payment_record",
                payment.transactionId ?: "",
                payment.groupId,
                payment.memberId,
                payment.paymentType.name,
                payment.amount,
                payment.processedAt ?: ""
            )

            supabase.postgrest["payments"].select(columns = Columns.raw(PAYMENT_COLUMNS_SAFE)) {
                filter { eq("id", paymentId) }
            }.decodeSingleOrNull<Payment>()?.let { existing ->
                db.paymentDao().upsertPayment(existing.toEntity())
                return@retryWithExponentialBackoff existing.id ?: paymentId
            }

            val insertData = buildJsonObject {
                put("id", paymentId)
                put("member_id", payment.memberId)
                put("group_id", payment.groupId)
                put("amount", payment.amount)
                put("payment_type", payment.paymentType.name.lowercase())
                put("payment_method", payment.paymentMethod.name.lowercase())
                payment.transactionId?.let { put("transaction_id", it) }
                put("status", payment.status.name.lowercase())
                payment.processedAt?.let { put("processed_at", it) }
            }
            val created = supabase.postgrest["payments"].upsert(insertData) {
                onConflict = "id"
                select(columns = Columns.raw(PAYMENT_COLUMNS_SAFE))
            }.decodeSingle<Payment>()
            db.paymentDao().upsertPayment(created.toEntity())
            
            // Centralized event emission for all recorded payments
            EventBus.emit(PaymentProcessedEvent(created))

            created.id ?: paymentId
        }
    }

    override fun getPayments(groupId: String): Flow<Result<List<Payment>>> = this.observeAndSync(
        dbFlow = db.paymentDao().observeGroupPayments(groupId),
        mapper = { entity: PaymentEntity -> entity.toModel() },
        toEntity = { model: Payment -> model.toEntity() },
        networkFetch = {
            supabase.postgrest["payments"].select(columns = Columns.raw(PAYMENT_COLUMNS_SAFE)) {
                filter { eq("group_id", groupId) } 
            }.decodeList<Payment>() 
        },
        cacheSync = { list: List<PaymentEntity> -> db.paymentDao().syncPayments(groupId, list) }
    )
}
