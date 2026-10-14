package com.sanibonani.save.data.remote.supabase

import com.sanibonani.save.data.datasource.RemoteFinancialDataSource
import com.sanibonani.save.domain.model.Money
import com.sanibonani.save.domain.model.PlatformFee
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.json.*
import javax.inject.Inject
import javax.inject.Singleton
import com.sanibonani.save.domain.utils.safeRunCatching

@Singleton
class SupabaseFinancialDataSourceImpl @Inject constructor(
    private val client: SupabaseClient
) : RemoteFinancialDataSource {

    override suspend fun getVerifiedBalance(groupId: String): Result<Money> = safeRunCatching {
        val jsonElement = client.postgrest.rpc(
            function = "audit_and_fix_group_balance_v1",
            parameters = buildJsonObject {
                put("p_group_id", groupId)
                put("p_fix", false)
            }
        ).decodeAs<JsonElement>()

        val rawVal = when (jsonElement) {
            is JsonObject -> jsonElement["ledger_sum"]?.jsonPrimitive?.content ?: jsonElement["balance"]?.jsonPrimitive?.content ?: "0.00"
            is JsonPrimitive -> jsonElement.content
            else -> "0.00"
        }
        Money(rawVal)
    }

    override suspend fun repairGroupData(groupId: String): Result<Unit> = safeRunCatching {
        client.postgrest.rpc("recalculate_all_group_financials_v3", buildJsonObject { put("p_group_id", groupId) })
    }.map { }

    override suspend fun payPlatformFee(groupId: String, amount: Money, feeType: String, txId: String?, fromGroupFund: Boolean): Result<Unit> = safeRunCatching {
        client.postgrest.rpc("pay_platform_fee_v2", buildJsonObject {
            put("p_group_id", groupId)
            put("p_amount", amount.amount.toPlainString())
            put("p_fee_type", feeType)
            txId?.let { put("p_tx_id", it) }
            put("p_from_group_fund", fromGroupFund)
        })
    }

    override suspend fun getOutstandingPlatformFees(groupId: String): Result<List<PlatformFee>> = safeRunCatching {
        client.postgrest["platform_fees"].select {
            filter {
                eq("group_id", groupId)
                neq("status", "paid")
            }
        }.decodeList<PlatformFee>()
    }
}
