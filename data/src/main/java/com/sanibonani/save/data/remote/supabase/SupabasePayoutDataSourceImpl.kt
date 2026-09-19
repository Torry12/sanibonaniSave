package com.sanibonani.save.data.remote.supabase

import com.sanibonani.save.data.datasource.RemotePayoutDataSource
import com.sanibonani.save.domain.model.*
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.json.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabasePayoutDataSourceImpl @Inject constructor(
    private val client: SupabaseClient
) : RemotePayoutDataSource {

    private val PAYOUT_COLUMNS = "id, group_id, member_id, amount, bank_name, account_no, branch_code, status, payout_type, processed_by, processed_at, payout_reference, created_at"

    override suspend fun requestPayout(payout: PayoutRequest): Result<PayoutRequest> = runCatching {
        client.postgrest["payouts"].upsert(payout) { onConflict = "id"; select() }.decodeSingle<PayoutRequest>()
    }

    override suspend fun getPayouts(groupId: String): Result<List<PayoutRequest>> = runCatching {
        client.postgrest["payouts"].select { filter { eq("group_id", groupId) } }.decodeList<PayoutRequest>()
    }

    override suspend fun getPayoutsForMember(memberId: String): Result<List<PayoutRequest>> = runCatching {
        client.postgrest["payouts"].select { filter { eq("member_id", memberId) } }.decodeList<PayoutRequest>()
    }

    override suspend fun getPayoutById(id: String): Result<PayoutRequest> = runCatching {
        client.postgrest["payouts"].select { filter { eq("id", id) } }.decodeSingle<PayoutRequest>()
    }

    override suspend fun updatePayoutStatus(payoutId: String, status: PayoutStatus, reference: String?, processedBy: String?, processedAt: String?): Result<Unit> = runCatching {
        client.postgrest["payouts"].update(buildJsonObject {
            put("status", status.name.lowercase())
            reference?.let { put("payout_reference", it) }
            processedBy?.let { put("processed_by", it) }
            processedAt?.let { put("processed_at", it) }
        }) { filter { eq("id", payoutId) } }
    }

    override suspend fun completePayoutAtomic(payoutId: String, adminId: String, reference: String?): Result<Unit> = runCatching {
        client.postgrest.rpc("complete_payout_v2", buildJsonObject {
            put("p_payout_id", payoutId)
            put("p_admin_id", adminId)
            reference?.let { put("p_payout_reference", it) }
        })
    }

    override suspend fun processRoscaPayout(groupId: String, memberId: String, totalPot: Money, loanDeductions: JsonArray, payoutAmount: Money, payoutData: JsonObject): Result<String> = runCatching {
        val rpcParams = buildJsonObject {
            put("p_group_id", groupId)
            put("p_member_id", memberId)
            put("p_total_pot", totalPot.amount.toPlainString())
            put("p_loan_deductions", loanDeductions)
            put("p_payout_amount", payoutAmount.amount.toPlainString())
            put("p_payout_data", payoutData)
        }
        val res = client.postgrest.rpc("process_rosca_payout_v2", rpcParams).decodeAs<JsonObject>()
        res["payout_id"]?.jsonPrimitive?.content ?: "OK"
    }
}
