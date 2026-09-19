package com.sanibonani.save.data.repository.equity

import com.sanibonani.save.data.local.*
import com.sanibonani.save.data.repository.BaseRepository
import com.sanibonani.save.data.repository.mappers.*
import com.sanibonani.save.domain.event.EventBus
import com.sanibonani.save.domain.event.PaymentProcessedEvent
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.equity.ContributionRepository
import com.sanibonani.save.domain.repository.MemberRepository
import com.sanibonani.save.domain.utils.OperationKeys
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.*
import javax.inject.Inject
import javax.inject.Provider

class ContributionRepositoryImpl @Inject constructor(
    private val supabaseProvider: Provider<SupabaseClient>,
    private val memberRepo: Provider<MemberRepository>,
    private val dbProvider: Provider<SanibonaniDatabase>
) : BaseRepository("ContributionRepository"), ContributionRepository {

    private val supabase: SupabaseClient get() = supabaseProvider.get()
    private val db: SanibonaniDatabase get() = dbProvider.get()

    private val PAYMENT_COLUMNS_SAFE = """
        id, member_id, group_id, amount, payment_type,
        payment_method, transaction_id, status, processed_at, created_at
    """.trimIndent().replace("\n", "")

    // ── Individual Contributions ──────────────────────────────────────────────

    override suspend fun recordContribution(contribution: Contribution): Result<Unit> = runCatching {
        val rpcParams = buildJsonObject {
            put("p_member_id", contribution.memberId)
            put("p_group_id", contribution.groupId)
            put("p_amount", contribution.amount.amount)
            put("p_due_date", contribution.dueDate)
            put("p_paid_at", contribution.paidAt ?: java.time.Instant.now().toString())
            put("p_status", contribution.status.name.lowercase())
            put("p_type", contribution.type)
            put("p_tx_id", contribution.transactionId ?: "app_${java.time.Instant.now().toEpochMilli()}")
        }
        supabase.postgrest.rpc("record_contribution_v2", rpcParams).decodeAs<JsonElement>()

        // Refresh local cache
        memberRepo.get().getMemberById(contribution.memberId)
        db.contributionDao().upsertContributions(listOf(contribution.toEntity()))
    }

    override fun observeMemberContributions(memberId: String, groupId: String): Flow<Result<List<Contribution>>> = observeAndSync(
        dbFlow = db.contributionDao().observeContributions(memberId),
        mapper = { it.toModel() },
        toEntity = { it.toEntity() },
        networkFetch = {
            supabase.postgrest["contributions"].select {
                filter {
                    eq("member_id", memberId)
                    eq("group_id", groupId)
                }
            }.decodeList<Contribution>()
        },
        cacheSync = { list -> db.contributionDao().syncMemberContributions(memberId, list) }
    )

    override fun observeGroupContributions(groupId: String): Flow<Result<List<Contribution>>> = observeAndSync(
        dbFlow = db.contributionDao().observeGroupContributions(groupId),
        mapper = { it.toModel() },
        toEntity = { it.toEntity() },
        networkFetch = {
            supabase.postgrest["contributions"].select {
                filter { eq("group_id", groupId) }
            }.decodeList<Contribution>()
        },
        cacheSync = { list -> db.contributionDao().syncGroupContributions(groupId, list) }
    )

    // ── Payment Transactions ──────────────────────────────────────────────────

    override suspend fun recordPayment(payment: Payment): Result<String> = runCatching {
        retryWithExponentialBackoff {
            val paymentId = payment.id ?: OperationKeys.stableUuid(
                "payment_record",
                payment.transactionId ?: "",
                payment.groupId,
                payment.memberId,
                payment.paymentType.name,
                payment.amount.toDouble(),
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
                put("amount", payment.amount.amount)
                put("payment_type", payment.paymentType.name.lowercase())
                put("payment_method", payment.paymentMethod.name.lowercase())
                payment.transactionId?.let { put("transaction_id", it) }
                put("status", payment.status.name.lowercase())
                put("processed_at", payment.processedAt ?: "")
            }
            val created = supabase.postgrest["payments"].upsert(insertData) {
                onConflict = "id"
                select(columns = Columns.raw(PAYMENT_COLUMNS_SAFE))
            }.decodeSingle<Payment>()
            db.paymentDao().upsertPayment(created.toEntity())

            EventBus.emit(PaymentProcessedEvent(created))

            created.id ?: paymentId
        }
    }

    override fun observePayments(groupId: String): Flow<Result<List<Payment>>> = this.observeAndSync(
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

    override suspend fun processComplexPayment(
        groupId: String,
        memberId: String,
        totalAmount: Money,
        txId: String,
        allocations: JsonArray,
        method: PaymentMethod,
        paymentType: PaymentType,
        destination: PaymentDestination
    ): Result<Money> = runCatching {
        val rpcParams = buildJsonObject {
            put("p_group_id", groupId)
            put("p_member_id", memberId)
            put("p_total_amount", totalAmount.amount)
            put("p_tx_id", txId)
            put("p_allocations", allocations)
            put("p_method", method.name.lowercase())
            put("p_payment_type", paymentType.name.lowercase())
            put("p_destination", destination.name.lowercase())
        }

        val res = supabase.postgrest.rpc("process_complex_payment_v4", rpcParams)
            .decodeAs<JsonObject>()

        Money(res["new_balance"]?.jsonPrimitive?.content ?: "0.00")
    }

    override suspend fun getTotalContributed(memberId: String, groupId: String): Money =
        observeMemberContributions(memberId, groupId).map { it.getOrDefault(emptyList()) }.first()
            .sumOf { it.amount }
}
