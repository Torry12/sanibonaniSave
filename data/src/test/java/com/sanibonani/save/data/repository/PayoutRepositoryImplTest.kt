package com.sanibonani.save.data.repository

import com.sanibonani.save.data.repository.disbursement.PayoutRepositoryImpl
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PayoutRepositoryImplTest {

    @Test
    fun `complete payout rpc params use fallback nil uuid for platform admin`() {
        val params = PayoutRepositoryImpl.buildCompletePayoutRpcParams(
            payoutId = "11111111-1111-1111-1111-111111111111",
            adminId = "PLATFORM_ADMIN",
            fallbackAdminId = null,
            payoutReference = "ref-123"
        )

        assertEquals("11111111-1111-1111-1111-111111111111", params["p_payout_id"]?.jsonPrimitive?.content)
        assertEquals("ref-123", params["p_payout_reference"]?.jsonPrimitive?.content)
        assertEquals("00000000-0000-0000-0000-000000000000", params["p_admin_id"]?.jsonPrimitive?.content)
    }

    @Test
    fun `complete payout rpc params use valid fallback admin id`() {
        val params = PayoutRepositoryImpl.buildCompletePayoutRpcParams(
            payoutId = "11111111-1111-1111-1111-111111111111",
            adminId = "PLATFORM_ADMIN",
            fallbackAdminId = "22222222-2222-2222-2222-222222222222",
            payoutReference = null
        )

        assertEquals("22222222-2222-2222-2222-222222222222", params["p_admin_id"]?.jsonPrimitive?.content)
        assertFalse(params.containsKey("p_payout_reference"))
    }
}
