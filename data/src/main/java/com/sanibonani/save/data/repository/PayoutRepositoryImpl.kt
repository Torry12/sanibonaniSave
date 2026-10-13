package com.sanibonani.save.data.repository

import com.sanibonani.save.data.local.SanibonaniDatabase
import com.sanibonani.save.domain.event.EventBus


import com.sanibonani.save.domain.event.PaymentProcessedEvent
import com.sanibonani.save.domain.model.Payment
import com.sanibonani.save.domain.model.PaymentMethod
import com.sanibonani.save.domain.model.PaymentStatus
import com.sanibonani.save.domain.model.PaymentType
import com.sanibonani.save.domain.model.PayoutRequest
import com.sanibonani.save.domain.model.PayoutStatus
import com.sanibonani.save.domain.repository.GroupRepository
import com.sanibonani.save.domain.repository.PayoutRepository
import dagger.Lazy
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

class PayoutRepositoryImpl @Inject constructor(
    private val supabase: SupabaseClient,
    private val db: SanibonaniDatabase,
    private val groupRepo: Lazy<GroupRepository>
) : BaseRepository("PayoutRepository"), PayoutRepository {

    private val PAYOUT_COLUMNS_SAFE = """
        id, group_id, amount, bank_name, account_no, branch_code, 
        status, processed_by, processed_at, payout_reference, created_at
    """.trimIndent().replace("\n", "")

    override suspend fun requestPayout(payout: PayoutRequest): Result<String> = retryWithExponentialBackoff {
        runCatching {
            val insertData = buildJsonObject {
                payout.id?.let { put("id", it) }
                put("group_id", payout.groupId)
                put("amount", payout.amount)
                put("bank_name", payout.bankName)
                put("account_no", payout.accountNo)
                put("branch_code", payout.branchCode)
                put("status", payout.status.name.lowercase())
                payout.processedBy?.let { put("processed_by", it) }
                payout.processedAt?.let { put("processed_at", it) }
                payout.payoutReference?.let { put("payout_reference", it) }
            }
            val created = if (payout.id.isNullOrBlank()) {
                supabase.postgrest["payouts"].insert(insertData) {
                    select(columns = Columns.raw(PAYOUT_COLUMNS_SAFE))
                }.decodeSingle<PayoutRequest>()
            } else {
                supabase.postgrest["payouts"].upsert(insertData) {
                    onConflict = "id"
                    select(columns = Columns.raw(PAYOUT_COLUMNS_SAFE))
                }.decodeSingle<PayoutRequest>()
            }
            db.payoutDao().upsertPayout(created.toEntity())
            created.id ?: ""
        }
    }

    override suspend fun getPendingPayouts(): Result<List<PayoutRequest>> = retryWithExponentialBackoff {
        runCatching {
            supabase.postgrest["payouts"].select(columns = Columns.raw(PAYOUT_COLUMNS_SAFE)) {
                filter { eq("status", "group_approved") }
            }.decodeList<PayoutRequest>()
        }
    }

    override suspend fun getPayoutById(payoutId: String): Result<PayoutRequest> = retryWithExponentialBackoff {
        runCatching {
            supabase.postgrest["payouts"].select(columns = Columns.raw(PAYOUT_COLUMNS_SAFE)) {
                filter { eq("id", payoutId) }
            }.decodeSingle<PayoutRequest>()
        }
    }

    override suspend fun updatePayoutStatus(payoutId: String, status: PayoutStatus, payoutReference: String?): Result<Unit> = retryWithExponentialBackoff {
        runCatching {
            val existing = supabase.postgrest["payouts"].select(columns = Columns.raw(PAYOUT_COLUMNS_SAFE)) {
                filter { eq("id", payoutId) }
            }.decodeSingleOrNull<PayoutRequest>()

            if (existing != null && existing.status == status && (payoutReference == null || existing.payoutReference == payoutReference)) {
                db.payoutDao().upsertPayout(existing.toEntity())
                return@runCatching
            }

            supabase.postgrest["payouts"].update(buildJsonObject {
                put("status", status.name.lowercase())
                payoutReference?.let { put("payout_reference", it) }
                put("processed_at", OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
            }) {
                filter { eq("id", payoutId) }
            }
            
            // Sync local
            supabase.postgrest["payouts"].select(columns = Columns.raw(PAYOUT_COLUMNS_SAFE)) {
                filter { eq("id", payoutId) }
            }.decodeSingleOrNull<PayoutRequest>()?.let {
                db.payoutDao().upsertPayout(it.toEntity())
            }
            Unit
        }
    }

    override suspend fun completePayoutAtomic(
        payoutId: String,
        adminId: String,
        payoutReference: String?
    ): Result<Unit> = retryWithExponentialBackoff {
        runCatching {
            val rpcParams = buildCompletePayoutRpcParams(
                payoutId = payoutId,
                adminId = adminId,
                fallbackAdminId = supabase.auth.currentUserOrNull()?.id,
                payoutReference = payoutReference
            )
            supabase.postgrest.rpc("complete_payout_v1", rpcParams)

            // Refresh local cache for this payout
            getPayoutById(payoutId).onSuccess { updated ->
                db.payoutDao().upsertPayout(updated.toEntity())
                
                // Refresh group balance locally
                groupRepo.get().getGroupById(updated.groupId)

                // Emit payment event for payout (outflow)
                EventBus.emit(
                    PaymentProcessedEvent(
                        Payment(
                            memberId = updated.processedBy ?: "",
                            groupId = updated.groupId,
                            amount = updated.amount,
                            paymentType = PaymentType.CLAIM, // Use CLAIM for group disbursements
                            paymentMethod = PaymentMethod.BANK,
                            transactionId = updated.payoutReference ?: updated.id,
                            status = PaymentStatus.COMPLETED,
                            processedAt = updated.processedAt
                        )
                    )
                )
            }
            Unit
        }
    }

    override fun observePayouts(groupId: String): Flow<Result<List<PayoutRequest>>> = observeAndSync(
        dbFlow = db.payoutDao().observePayouts(groupId),
        mapper = { it.toModel() },
        toEntity = { it.toEntity() },
        networkFetch = {
            supabase.postgrest["payouts"].select(columns = Columns.raw(PAYOUT_COLUMNS_SAFE)) {
                filter { eq("group_id", groupId) }
            }.decodeList<PayoutRequest>()
        },
        cacheSync = { list -> db.payoutDao().syncPayouts(groupId, list) }
    )

    companion object {
        private val UUID_REGEX = Regex(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
        )

        internal fun buildCompletePayoutRpcParams(
            payoutId: String,
            adminId: String,
            fallbackAdminId: String?,
            payoutReference: String?
        ): JsonObject = buildJsonObject {
            put("p_payout_id", payoutId)
            val safeAdminId = adminId.takeIf { it.isValidUuid() }
                ?: fallbackAdminId?.takeIf { it.isValidUuid() }
            safeAdminId?.let { put("p_admin_id", it) }
            payoutReference?.let { put("p_payout_reference", it) }
        }

        private fun String.isValidUuid(): Boolean = UUID_REGEX.matches(trim())
    }
}
