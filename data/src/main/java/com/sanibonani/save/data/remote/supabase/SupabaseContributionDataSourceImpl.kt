package com.sanibonani.save.data.remote.supabase

import com.sanibonani.save.data.datasource.RemoteContributionDataSource
import com.sanibonani.save.domain.model.*
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.json.*
import javax.inject.Inject
import javax.inject.Singleton

import com.sanibonani.save.domain.repository.system.DataConnectionProtocol
import com.sanibonani.save.domain.utils.safeRunCatching
import com.sanibonani.save.domain.utils.requirePersistedId

@Singleton
class SupabaseContributionDataSourceImpl @Inject constructor(
    protocol: DataConnectionProtocol
) : BaseSupabaseDataSource(protocol), RemoteContributionDataSource {

    override suspend fun recordContribution(contribution: Contribution): Result<Unit> = safeRunCatching {
        val rpcParams = buildJsonObject {
            put("p_member_id", contribution.memberId)
            put("p_group_id", contribution.groupId)
            put("p_amount", contribution.amount.amount.toPlainString())
            put("p_due_date", contribution.dueDate)
            put("p_paid_at", contribution.paidAt ?: java.time.Instant.now().toString())
            put("p_status", contribution.status.name.lowercase())
            put("p_type", contribution.type)
            put("p_tx_id", contribution.transactionId ?: "app_${java.time.Instant.now().toEpochMilli()}")
        }
        client.postgrest.rpc("record_contribution_v2", rpcParams)
    }

    override suspend fun getMemberContributions(memberId: String, groupId: String): Result<List<Contribution>> = safeRunCatching {
        client.postgrest["contributions"].select {
            filter {
                eq("member_id", memberId)
                eq("group_id", groupId)
            }
        }.decodeList<Contribution>()
    }

    override suspend fun getGroupContributions(groupId: String): Result<List<Contribution>> = safeRunCatching {
        client.postgrest["contributions"].select { filter { eq("group_id", groupId) } }.decodeList<Contribution>()
    }

    override suspend fun recordPayment(payment: Payment): Result<String> = safeRunCatching {
        val created = client.postgrest["payments"].upsert(payment) {
            onConflict = "id"
            select()
        }.decodeSingle<Payment>()
        created.id.requirePersistedId("payment")
    }

    override suspend fun getPayments(groupId: String): Result<List<Payment>> = safeRunCatching {
        client.postgrest["payments"].select { filter { eq("group_id", groupId) } }.decodeList<Payment>()
    }

    override suspend fun processComplexPayment(
        groupId: String,
        memberId: String,
        totalAmount: Money,
        txId: String,
        allocations: List<PaymentAllocation>,
        method: PaymentMethod,
        paymentType: PaymentType,
        destination: PaymentDestination
    ): Result<Money> = safeRunCatching {
        val rpcParams = buildJsonObject {
            put("p_group_id", groupId)
            put("p_member_id", memberId)
            put("p_total_amount", totalAmount.amount.toPlainString())
            put("p_tx_id", txId)
            put("p_allocations", JsonArray(allocations.map { allocation ->
                buildJsonObject {
                    put("type", allocation.type)
                    put("amount", allocation.amount.amount.toPlainString())
                    put("due_date", allocation.dueDate)
                    put("description", allocation.description)
                    allocation.loanId?.let { put("loan_id", it) }
                }
            }))
            put("p_method", method.name.lowercase())
            put("p_payment_type", paymentType.name.lowercase())
            put("p_destination", destination.name.lowercase())
        }
        val element = client.postgrest.rpc("process_complex_payment_v4", rpcParams).decodeAs<JsonElement>()
        val obj = when (element) {
            is JsonObject -> element
            is JsonArray -> element.firstOrNull()?.jsonObject ?: buildJsonObject {}
            else -> buildJsonObject {}
        }

        val success = obj["success"]?.jsonPrimitive?.booleanOrNull
        check(success != false) { "process_complex_payment_v4 reported failure: $obj" }
        val balStr = obj["new_balance"]?.jsonPrimitive?.content
            ?: obj["balance"]?.jsonPrimitive?.content
            ?: error("process_complex_payment_v4 returned no balance: $element")
        Money(balStr)
    }
}
