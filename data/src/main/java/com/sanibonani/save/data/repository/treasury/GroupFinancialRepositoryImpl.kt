package com.sanibonani.save.data.repository.treasury

import com.sanibonani.save.data.local.SanibonaniDatabase
import com.sanibonani.save.data.repository.BaseRepository
import com.sanibonani.save.data.repository.mappers.*
import com.sanibonani.save.domain.event.EventBus
import com.sanibonani.save.domain.event.LedgerEntryCreatedEvent
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.treasury.GroupFinancialRepository
import com.sanibonani.save.domain.repository.GroupRepository
import com.sanibonani.save.domain.utils.logAndGetMessage
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.json.*
import javax.inject.Inject
import javax.inject.Provider

class GroupFinancialRepositoryImpl @Inject constructor(
    private val supabaseProvider: Provider<SupabaseClient>,
    private val groupRepo: Provider<GroupRepository>,
    private val dbProvider: Provider<SanibonaniDatabase>
) : BaseRepository("GroupFinancialRepository"), GroupFinancialRepository {

    private val supabase: SupabaseClient get() = supabaseProvider.get()
    private val db: SanibonaniDatabase get() = dbProvider.get()

    override suspend fun incrementGroupBalance(
        groupId: String,
        amount: Money,
        memberId: String?
    ): Result<Money> = authoritativeCall(
        networkCall = {
            val rpcParams = buildJsonObject {
                put("p_group_id", groupId)
                put("p_amount", amount.amount)
                put("p_member_id", memberId)
            }
            val res = supabase.postgrest.rpc("increment_group_balance", rpcParams)
                .decodeAs<JsonObject>()
            Money(res["balance_after"]?.jsonPrimitive?.doubleOrNull ?: 0.0)
        },
        toEntity = { it }, // We don't have a single entity for this, we update the group
        cacheSync = { newBalance ->
            db.groupDao().getGroupById(groupId)?.let { local ->
                db.groupDao().upsertGroup(local.copy(balance = newBalance))
            }

            EventBus.emit(
                LedgerEntryCreatedEvent(
                    LedgerEntry(
                        groupId = groupId,
                        amount = amount,
                        balanceAfter = newBalance,
                        description = "Atomic balance update",
                        category = "adjustment",
                        transactionId = null
                    )
                )
            )
        }
    )

    override suspend fun recordDisbursement(
        groupId: String,
        amount: Money,
        description: String,
        category: String,
        transactionId: String?,
        memberId: String?
    ): Result<Money> = authoritativeCall(
        networkCall = {
            val rpcParams = buildJsonObject {
                put("p_group_id", groupId)
                put("p_amount", (-amount).amount)
                put("p_description", description)
                put("p_category", category)
                put("p_transaction_id", transactionId)
                put("p_member_id", memberId)
            }
            val res = supabase.postgrest.rpc("increment_group_balance", rpcParams)
                .decodeAs<JsonObject>()
            Money(res["balance_after"]?.jsonPrimitive?.doubleOrNull ?: 0.0)
        },
        toEntity = { it },
        cacheSync = { newBalance ->
            db.groupDao().getGroupById(groupId)?.let { local ->
                db.groupDao().upsertGroup(local.copy(balance = newBalance))
            }

            EventBus.emit(
                LedgerEntryCreatedEvent(
                    LedgerEntry(
                        groupId = groupId,
                        amount = -amount,
                        balanceAfter = newBalance,
                        description = description,
                        category = category,
                        transactionId = transactionId
                    )
                )
            )
        }
    )

    override suspend fun payPlatformFee(
        groupId: String,
        amount: Money,
        feeType: String,
        txId: String?
    ): Result<Unit> = retryWithExponentialBackoff {
        runCatching {
            val rpcParams = buildJsonObject {
                put("p_group_id", groupId)
                put("p_amount", amount.amount)
                put("p_fee_type", feeType)
                txId?.let { put("p_tx_id", it) }
                // Monthly fees usually come from group fund, registration might be direct
                put("p_from_group_fund", feeType == "monthly")
            }
            supabase.postgrest.rpc("pay_platform_fee_v2", rpcParams).decodeAs<kotlinx.serialization.json.JsonElement>()

            // Refresh local group state since balance might have decreased
            groupRepo.get().getGroupById(groupId)
            Unit
        }
    }

    override suspend fun getVerifiedBalance(groupId: String): Result<Money> = runCatching {
        val res = supabase.postgrest.rpc(
            function = "audit_and_fix_group_balance_v1",
            parameters = buildJsonObject {
                put("p_group_id", groupId)
                put("p_fix", false)
            }
        ).decodeAs<kotlinx.serialization.json.JsonObject>()

        Money(res["ledger_sum"]?.jsonPrimitive?.doubleOrNull ?: 0.0)
    }

    override suspend fun repairGroupData(groupId: String): Result<Unit> = runCatching {
        supabase.postgrest.rpc(
            function = "recalculate_all_group_financials_v3",
            parameters = buildJsonObject {
                put("p_group_id", groupId)
            }
        ).decodeAs<kotlinx.serialization.json.JsonElement>()
        // Refresh local cache
        groupRepo.get().getGroupById(groupId)
        Unit
    }
}
