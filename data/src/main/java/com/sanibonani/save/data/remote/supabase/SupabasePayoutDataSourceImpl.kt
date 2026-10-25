package com.sanibonani.save.data.remote.supabase

import com.sanibonani.save.data.datasource.RemotePayoutDataSource
import com.sanibonani.save.data.repository.disbursement.PayoutRepositoryImpl
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.logging.AppLogger
import com.sanibonani.save.domain.repository.system.DataConnectionProtocol
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.json.*
import javax.inject.Inject
import javax.inject.Singleton
import com.sanibonani.save.domain.utils.safeRunCatching

@Singleton
class SupabasePayoutDataSourceImpl @Inject constructor(
    protocol: DataConnectionProtocol
) : BaseSupabaseDataSource(protocol), RemotePayoutDataSource {

    private val tag = "SupabasePayout"

    override suspend fun requestPayout(payout: PayoutRequest): Result<PayoutRequest> = safeRunCatching {
        AppLogger.d(tag, "Requesting payout for group ${payout.groupId}")
        client.postgrest["payouts"].upsert(payout) { onConflict = "id"; select() }.decodeSingle<PayoutRequest>()
    }

    override suspend fun getPayouts(groupId: String): Result<List<PayoutRequest>> = safeRunCatching {
        AppLogger.d(tag, "Fetching payouts for group $groupId")
        client.postgrest["payouts"].select { filter { eq("group_id", groupId) } }.decodeList<PayoutRequest>()
    }

    override suspend fun getPendingPayouts(): Result<List<PayoutRequest>> = safeRunCatching {
        AppLogger.d(tag, "Fetching all actionable payouts")
        val statuses = listOf(
            PayoutStatus.PENDING.name.lowercase(),
            PayoutStatus.GROUP_APPROVED.name.lowercase(),
            PayoutStatus.PROCESSING.name.lowercase(),
            PayoutStatus.FAILED.name.lowercase()
        )
        client.postgrest["payouts"].select {
            filter { or { statuses.forEach { eq("status", it) } } }
        }.decodeList<PayoutRequest>()
    }

    override suspend fun getPayoutsForMember(memberId: String): Result<List<PayoutRequest>> = safeRunCatching {
        AppLogger.d(tag, "Fetching payouts for member $memberId")
        client.postgrest["payouts"].select { filter { eq("member_id", memberId) } }.decodeList<PayoutRequest>()
    }

    override suspend fun getPayoutById(id: String): Result<PayoutRequest> = safeRunCatching {
        AppLogger.d(tag, "Fetching payout $id")
        client.postgrest["payouts"].select { filter { eq("id", id) } }.decodeSingle<PayoutRequest>()
    }

    override suspend fun updatePayoutStatus(
        payoutId: String,
        status: PayoutStatus,
        reference: String?,
        processedBy: String?,
        processedAt: String?,
        metadata: JsonObject?
    ): Result<Unit> = safeRunCatching {
        AppLogger.d(tag, "Updating payout $payoutId status to ${status.name}")
        client.postgrest["payouts"].update(buildJsonObject {
            put("status", status.name.lowercase())
            reference?.let { put("payout_reference", it) }
            processedBy?.let { put("processed_by", it) }
            processedAt?.let { put("processed_at", it) }
            metadata?.let { put("metadata", it) }
        }) { filter { eq("id", payoutId) } }
    }

    override suspend fun completePayoutAtomic(payoutId: String, adminId: String, reference: String?): Result<Unit> = safeRunCatching {
        AppLogger.d(tag, "Completing payout $payoutId atomically by admin $adminId")
        val rpcParams = PayoutRepositoryImpl.buildCompletePayoutRpcParams(
            payoutId = payoutId,
            adminId = adminId,
            fallbackAdminId = null,
            payoutReference = reference
        )
        // A timeout can happen after the database committed. Never issue a second
        // accounting path here: the RPC is atomic and owns both the state change
        // and ledger movement. Surface the error so callers can reconcile by ID.
        client.postgrest.rpc("complete_payout_v2", rpcParams)
    }

    override suspend fun updatePayoutDocumentStatus(payoutId: String, status: DocumentStatus): Result<Unit> = safeRunCatching {
        AppLogger.d(tag, "Updating payout $payoutId document status to ${status.name}")
        client.postgrest["payouts"].update(buildJsonObject {
            put("supporting_document_status", status.name.lowercase())
        }) { filter { eq("id", payoutId) } }
    }

    override suspend fun processRoscaPayout(groupId: String, memberId: String, totalPot: Money, loanDeductions: JsonArray, payoutAmount: Money, payoutData: JsonObject, period: String): Result<String?> = safeRunCatching {
        AppLogger.d(tag, "Processing ROSCA payout for member $memberId in group $groupId for period $period")
        val element = client.postgrest.rpc(ROSCA_PAYOUT_RPC, buildRoscaPayoutRpcParams(groupId, memberId, totalPot, loanDeductions, payoutAmount, payoutData, period)).decodeAs<JsonElement>()
        parseRoscaPayoutId(element)
    }

    companion object {
        const val ROSCA_PAYOUT_RPC = "process_rosca_payout_v3"

        /**
         * The id of the payout this call created, or null when it created none. A rotation whose
         * offsets absorb the whole pot creates no row. A period the server already executed comes
         * back with the earlier payout's id and `duplicate: true`; reporting that id would announce
         * and audit a second initiation for money that is not new.
         */
        internal fun parseRoscaPayoutId(element: JsonElement): String? {
            val obj = when (element) {
                is JsonObject -> element
                is JsonArray -> element.firstOrNull() as? JsonObject
                else -> null
            } ?: return null
            if (obj["duplicate"]?.jsonPrimitive?.booleanOrNull == true) return null
            return obj["payout_id"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        }

        fun buildRoscaPayoutRpcParams(
            groupId: String,
            memberId: String,
            totalPot: Money,
            loanDeductions: JsonArray,
            payoutAmount: Money,
            payoutData: JsonObject,
            period: String
        ): JsonObject = buildJsonObject {
            put("p_group_id", groupId)
            put("p_member_id", memberId)
            put("p_total_pot", totalPot.amount.toPlainString())
            put("p_loan_deductions", loanDeductions)
            put("p_payout_amount", payoutAmount.amount.toPlainString())
            put("p_payout_data", payoutData)
            put("p_period", period)
        }
    }
}

