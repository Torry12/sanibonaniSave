package com.sanibonani.save.data.repository

import com.sanibonani.save.data.remote.supabase.SupabaseClaimDataSourceImpl
import com.sanibonani.save.data.repository.member.BeneficiaryClaimRepositoryImpl
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * A burial claim could be approved but never paid: `pay_burial_claim_v1` is what
 * [SupabaseClaimDataSourceImpl.payClaimAtomic] calls, and no schema declared it until
 * SYNC_DELTA_20261029B. That function writes the group's money, so the payload it accepts is the
 * contract worth pinning - in particular the reviewer, because the server now records the session
 * and treats the nil UUID the console sends as "no reviewer I can name".
 */
class BeneficiaryClaimRepositoryImplTest {

    @Test
    fun `the payment call targets the rpc the delta declares`() {
        assertEquals("pay_burial_claim_v1", SupabaseClaimDataSourceImpl.PAY_CLAIM_RPC)
    }

    @Test
    fun `pay claim rpc params use fallback nil uuid for platform admin`() {
        val params = BeneficiaryClaimRepositoryImpl.buildPayClaimRpcParams(
            claimId = "11111111-1111-1111-1111-111111111111",
            adminId = "PLATFORM_ADMIN",
            fallbackAdminId = null,
            notes = "paid"
        )

        assertEquals("11111111-1111-1111-1111-111111111111", params["p_claim_id"]?.jsonPrimitive?.content)
        assertEquals("paid", params["p_notes"]?.jsonPrimitive?.content)
        assertEquals("00000000-0000-0000-0000-000000000000", params["p_admin_id"]?.jsonPrimitive?.content)
    }

    @Test
    fun `a reviewer the app can name crosses as their own id`() {
        val params = BeneficiaryClaimRepositoryImpl.buildPayClaimRpcParams(
            claimId = "11111111-1111-1111-1111-111111111111",
            adminId = "33333333-3333-3333-3333-333333333333",
            fallbackAdminId = null,
            notes = null
        )

        assertEquals("33333333-3333-3333-3333-333333333333", params["p_admin_id"]?.jsonPrimitive?.content)
        // Absent, not empty: the function keeps the note already on the claim when this call
        // brings none.
        assertFalse("p_notes", params.containsKey("p_notes"))
    }
}
