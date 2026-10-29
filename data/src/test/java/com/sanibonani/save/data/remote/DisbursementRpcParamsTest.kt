package com.sanibonani.save.data.remote

import com.sanibonani.save.data.remote.supabase.SupabaseGroupDataSourceImpl
import com.sanibonani.save.domain.model.Money
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * `record_disbursement_v1` is the entry point the app has called by name since the disbursement
 * outbox existed, and no schema in this repo declared it, so every queued disbursement died with
 * PGRST202 before reaching a table. SYNC_DELTA_20261029B declares it. PostgREST binds JSON keys to
 * declared parameter names, so these two facts - the name and the exact keys - are the whole
 * contract between the client and that function, and nothing else in the build checks them.
 */
class DisbursementRpcParamsTest {

    @Test
    fun `the disbursement call targets the declared rpc name`() {
        assertEquals("record_disbursement_v1", SupabaseGroupDataSourceImpl.DISBURSEMENT_RPC)
    }

    @Test
    fun `every key is a declared parameter and the amount crosses as a decimal string`() {
        val params = SupabaseGroupDataSourceImpl.buildDisbursementRpcParams(
            groupId = "11111111-1111-1111-1111-111111111111",
            amount = Money("1200.5"),
            description = "Cash for the funeral",
            category = "manual_payout",
            transactionId = "disb-1",
            memberId = "22222222-2222-2222-2222-222222222222"
        )

        assertEquals("11111111-1111-1111-1111-111111111111", params["p_group_id"]?.jsonPrimitive?.content)
        // Fixed scale, never a float: the function checks the sign and the two-decimal scale.
        assertEquals("1200.50", params["p_amount"]?.jsonPrimitive?.content)
        assertEquals("Cash for the funeral", params["p_description"]?.jsonPrimitive?.content)
        assertEquals("manual_payout", params["p_category"]?.jsonPrimitive?.content)
        assertEquals("disb-1", params["p_transaction_id"]?.jsonPrimitive?.content)
        assertEquals("22222222-2222-2222-2222-222222222222", params["p_member_id"]?.jsonPrimitive?.content)
        assertEquals(6, params.size)
    }

    @Test
    fun `the optional keys are absent rather than null`() {
        // Absent, not null: the declared parameters carry DEFAULT NULL, so a key the app does
        // not know is left for the function to default instead of being overwritten.
        val params = SupabaseGroupDataSourceImpl.buildDisbursementRpcParams(
            groupId = "g-1",
            amount = Money("50.00"),
            description = "Petty cash",
            category = "expense",
            transactionId = null,
            memberId = null
        )

        assertFalse("p_transaction_id", params.containsKey("p_transaction_id"))
        assertFalse("p_member_id", params.containsKey("p_member_id"))
        assertEquals(4, params.size)
    }
}
