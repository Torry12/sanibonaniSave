package com.sanibonani.save.data.repository.disbursement

import com.sanibonani.save.data.local.SanibonaniDatabase
import com.sanibonani.save.data.repository.BaseRepository
import com.sanibonani.save.data.repository.mappers.*
import com.sanibonani.save.domain.event.EventBus
import com.sanibonani.save.domain.event.PaymentProcessedEvent
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.AuditLogRepository
import com.sanibonani.save.domain.repository.GroupRepository
import com.sanibonani.save.domain.repository.disbursement.PayoutRepository
import dagger.Lazy
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.*
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

class PayoutRepositoryImpl @Inject constructor(
    private val supabaseProvider: javax.inject.Provider<SupabaseClient>,
    private val dbProvider: javax.inject.Provider<SanibonaniDatabase>,
    private val groupRepo: Lazy<GroupRepository>,
    private val auditLogRepo: AuditLogRepository
) : BaseRepository("PayoutRepository"), PayoutRepository {

    private val supabase: SupabaseClient get() = supabaseProvider.get()
    private val db: SanibonaniDatabase get() = dbProvider.get()

    private val PAYOUT_COLUMNS_SAFE = """
        id, group_id, member_id, amount, bank_name, account_no, branch_code,
        status, payout_type, processed_by, processed_at, payout_reference, created_at
    """.trimIndent().replace("\n", "")

    override suspend fun requestPayout(payout: PayoutRequest): Result<String> = retryWithExponentialBackoff {
        runCatching {
            val insertData = buildJsonObject {
                payout.id?.let { put("id", it) }
                put("group_id", payout.groupId)
                put("amount", payout.amount.amount)
                put("bank_name", payout.bankName)
                put("account_no", payout.accountNo)
                put("branch_code", payout.branchCode)
                put("status", payout.status.name.lowercase())
                put("payout_type", payout.type.name.lowercase())
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

            // 🛡️ Forensic Audit: Log payout request
            auditLogRepo.logEvent(
                AuditLog(
                    actorId = created.memberId ?: created.groupId,
                    targetGroupId = created.groupId,
                    targetMemberId = created.memberId,
                    action = "PAYOUT_REQUESTED",
                    details = Json.encodeToJsonElement(created)
                )
            )

            created.id ?: ""
        }
    }

    override suspend fun getPendingPayouts(): Result<List<PayoutRequest>> = retryWithExponentialBackoff {
        runCatching {
            val remote = supabase.postgrest["payouts"].select(columns = Columns.raw(PAYOUT_COLUMNS_SAFE)) {
                filter { eq("status", "group_approved") }
            }.decodeList<PayoutRequest>()
            db.payoutDao().upsertAllPayouts(remote.map { it.toEntity() })
            remote
        }
    }

    override fun observePendingPayouts(): Flow<Result<List<PayoutRequest>>> = observeAndSync(
        dbFlow = db.payoutDao().observePendingPayouts(),
        mapper = { it.toModel() },
        toEntity = { it.toEntity() },
        networkFetch = {
            supabase.postgrest["payouts"].select(columns = Columns.raw(PAYOUT_COLUMNS_SAFE)) {
                filter { eq("status", "group_approved") }
            }.decodeList<PayoutRequest>()
        },
        cacheSync = { list -> db.payoutDao().upsertAllPayouts(list) }
    )

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
            supabase.postgrest.rpc("complete_payout_v2", rpcParams).decodeAs<kotlinx.serialization.json.JsonElement>()

            // Refresh local cache for this payout
            getPayoutById(payoutId).onSuccess { updated ->
                db.payoutDao().upsertPayout(updated.toEntity())

                // 🛡️ Forensic Audit: Log payout completion
                auditLogRepo.logEvent(
                    AuditLog(
                        actorId = adminId,
                        targetGroupId = updated.groupId,
                        targetMemberId = updated.memberId,
                        action = "PAYOUT_COMPLETED",
                        details = Json.encodeToJsonElement(mapOf(
                            "payoutId" to payoutId,
                            "reference" to payoutReference,
                            "amount" to updated.amount.format()
                        ))
                    )
                )

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

    override fun observePayoutsForMember(memberId: String): Flow<Result<List<PayoutRequest>>> = observeAndSync(
        dbFlow = db.payoutDao().observePayoutsForMember(memberId),
        mapper = { it.toModel() },
        toEntity = { it.toEntity() },
        networkFetch = {
            supabase.postgrest["payouts"].select(columns = Columns.raw(PAYOUT_COLUMNS_SAFE)) {
                filter { eq("member_id", memberId) }
            }.decodeList<PayoutRequest>()
        },
        cacheSync = { list ->
            // Only upsert the ones for this member to avoid deleting other payouts in syncPayouts
            db.payoutDao().upsertPayouts(list)
        }
    )

    override suspend fun processRoscaPayout(
        groupId: String,
        memberId: String,
        totalPot: Money,
        loanDeductions: JsonArray,
        payoutAmount: Money,
        payoutData: JsonObject
    ): Result<String> = runCatching {
        val rpcParams = buildJsonObject {
            put("p_group_id", groupId)
            put("p_member_id", memberId)
            put("p_total_pot", totalPot.amount)
            put("p_loan_deductions", loanDeductions)
            put("p_payout_amount", payoutAmount.amount)
            put("p_payout_data", payoutData)
        }

        val res = supabase.postgrest.rpc("process_rosca_payout_v2", rpcParams)
            .decodeAs<JsonObject>()

        res["payout_id"]?.jsonPrimitive?.content ?: "OK"
    }

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
