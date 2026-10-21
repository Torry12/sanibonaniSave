package com.sanibonani.save.data.remote

import com.sanibonani.save.data.remote.supabase.SupabasePayoutDataSourceImpl
import com.sanibonani.save.domain.model.Money
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The rotation's duplicate protection lives in the database, and the database can only key a period
 * if the call carries it: v2 has no period parameter and inserts a payouts row that says nothing about
 * which rotation it settles.
 */
class RoscaPayoutRpcParamsTest {

    @Test
    fun `the rotation call targets the period-keyed rpc version`() {
        assertEquals("process_rosca_payout_v3", SupabasePayoutDataSourceImpl.ROSCA_PAYOUT_RPC)
    }

    @Test
    fun `the rotation call carries the period and decimal amounts`() {
        val params = SupabasePayoutDataSourceImpl.buildRoscaPayoutRpcParams(
            groupId = "g-1",
            memberId = "m-1",
            totalPot = Money("300.0"),
            loanDeductions = JsonArray(
                listOf(buildJsonObject {
                    put("loan_id", "l-1")
                    put("amount", "100.00")
                })
            ),
            payoutAmount = Money("200.0"),
            payoutData = buildJsonObject { put("bank_name", "FNB") },
            period = "2026-03"
        )

        assertEquals("2026-03", params["p_period"]?.jsonPrimitive?.content)
        assertEquals("g-1", params["p_group_id"]?.jsonPrimitive?.content)
        assertEquals("m-1", params["p_member_id"]?.jsonPrimitive?.content)
        // Amounts cross the wire as fixed-scale decimal strings, never as floating point.
        assertEquals("300.00", params["p_total_pot"]?.jsonPrimitive?.content)
        assertEquals("200.00", params["p_payout_amount"]?.jsonPrimitive?.content)
    }
}
