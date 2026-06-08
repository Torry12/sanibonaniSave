package com.sanibonani.save.data.repository

import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class BeneficiaryClaimRepositoryImplTest {

    @Test
    fun `pay claim rpc params omit non uuid admin id`() {
        val params = BeneficiaryClaimRepositoryImpl.buildPayClaimRpcParams(
            claimId = "11111111-1111-1111-1111-111111111111",
            adminId = "PLATFORM_ADMIN",
            fallbackAdminId = null,
            notes = "paid"
        )

        assertEquals("11111111-1111-1111-1111-111111111111", params["p_claim_id"]?.jsonPrimitive?.content)
        assertEquals("paid", params["p_notes"]?.jsonPrimitive?.content)
        assertFalse(params.containsKey("p_admin_id"))
    }
}
