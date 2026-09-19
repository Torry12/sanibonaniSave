package com.sanibonani.save.data.remote.supabase

import com.sanibonani.save.data.datasource.RemoteFinancialDataSource
import com.sanibonani.save.domain.model.Money
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.json.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseFinancialDataSourceImpl @Inject constructor(
    private val client: SupabaseClient
) : RemoteFinancialDataSource {

    override suspend fun getVerifiedBalance(groupId: String): Result<Money> = runCatching {
        client.postgrest.rpc(
            function = "audit_and_fix_group_balance_v1",
            parameters = buildJsonObject {
                put("p_group_id", groupId)
                put("p_fix_balance", false)
            }
        ).decodeAs<Money>()
    }.recoverCatching {
        val res = client.postgrest.rpc(
            function = "audit_and_fix_group_balance_v1",
            parameters = buildJsonObject {
                put("p_group_id", groupId)
                put("p_fix_balance", false)
            }
        ).decodeAs<JsonObject>()
        Money(res["ledger_sum"]?.jsonPrimitive?.doubleOrNull ?: 0.0)
    }

    override suspend fun repairGroupData(groupId: String): Result<Unit> = runCatching {
        client.postgrest.rpc("recalculate_all_group_financials_v1", buildJsonObject { put("p_group_id", groupId) })
    }

    override suspend fun payPlatformFee(groupId: String, amount: Money, feeType: String, txId: String?, fromGroupFund: Boolean): Result<Unit> = runCatching {
        client.postgrest.rpc("pay_platform_fee_v2", buildJsonObject {
            put("p_group_id", groupId)
            put("p_amount", amount.amount.toPlainString())
            put("p_fee_type", feeType)
            txId?.let { put("p_tx_id", it) }
            put("p_from_group_fund", fromGroupFund)
        })
    }
}
