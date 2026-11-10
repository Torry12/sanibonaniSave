package com.sanibonani.save.data.repository

import com.sanibonani.save.data.datasource.LocalClaimDataSource
import com.sanibonani.save.data.datasource.RemoteClaimDataSource
import com.sanibonani.save.data.local.PendingOperationDao
import com.sanibonani.save.data.remote.supabase.SupabaseClaimDataSourceImpl
import com.sanibonani.save.data.repository.member.BeneficiaryClaimRepositoryImpl
import com.sanibonani.save.domain.model.BeneficiaryClaimStatus
import com.sanibonani.save.domain.model.BeneficiaryPayoutClaim
import com.sanibonani.save.domain.model.Money
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A burial claim could be approved but never paid: `pay_burial_claim_v1` is what
 * [SupabaseClaimDataSourceImpl.payClaimAtomic] calls, and no schema declared it until
 * SYNC_DELTA_20261029B. That function writes the group's money, so the payload it accepts is the
 * contract worth pinning - in particular the reviewer, because the server now records the session
 * and treats the nil UUID the console sends as "no reviewer I can name".
 */
class BeneficiaryClaimRepositoryImplTest {

    private val remoteSource: RemoteClaimDataSource = mockk()
    private val localSource: LocalClaimDataSource = mockk(relaxed = true)
    private val pendingOpDao: PendingOperationDao = mockk(relaxed = true)
    private val db: com.sanibonani.save.data.local.SanibonaniDatabase =
        mockk<com.sanibonani.save.data.local.SanibonaniDatabase>(relaxed = true)
            .apply { every { pendingOperationDao() } returns pendingOpDao }
    private val json = Json { ignoreUnknownKeys = true }

    private fun repository() =
        BeneficiaryClaimRepositoryImpl(remoteSource, localSource, json, javax.inject.Provider { db })

    private fun claim(status: BeneficiaryClaimStatus) = BeneficiaryPayoutClaim(
        id = "c1",
        groupId = "g1",
        memberId = "m1",
        beneficiaryId = "b1",
        beneficiaryName = "Grace",
        claimAmount = Money("5000.00"),
        status = status,
        reviewedBy = if (status == BeneficiaryClaimStatus.SUBMITTED) null else "admin1"
    )

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

    @Test
    fun `a replay whose existence read was refused does not re-open the claim`() = runBlocking {
        coEvery { remoteSource.findClaimById("c1") } returns
            Result.failure(java.io.IOException("read refused"))

        val result = repository().submitClaim(
            claim(BeneficiaryClaimStatus.SUBMITTED),
            idempotencyKey = "k1"
        )

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { remoteSource.submitClaim(any()) }
        coVerify(exactly = 1) { pendingOpDao.upsert(any()) }
    }

    @Test
    fun `a replay of a claim the server already has keeps the decided row`() = runBlocking {
        coEvery { remoteSource.findClaimById("c1") } returns
            Result.success(claim(BeneficiaryClaimStatus.REJECTED).copy(rejectionReason = "no death certificate"))

        val result = repository().submitClaim(
            claim(BeneficiaryClaimStatus.SUBMITTED),
            idempotencyKey = "k1"
        )

        assertEquals(BeneficiaryClaimStatus.REJECTED, result.getOrThrow().status)
        val cached = slot<BeneficiaryPayoutClaim>()
        coVerify { localSource.saveClaim(capture(cached)) }
        assertEquals("no death certificate", cached.captured.rejectionReason)
        coVerify(exactly = 0) { remoteSource.submitClaim(any()) }
    }

    @Test
    fun `a replay creates the claim only when the server reports it absent`() = runBlocking {
        coEvery { remoteSource.findClaimById("c1") } returns Result.success(null)
        coEvery { remoteSource.submitClaim(any()) } returns Result.success(claim(BeneficiaryClaimStatus.SUBMITTED))

        val result = repository().submitClaim(
            claim(BeneficiaryClaimStatus.SUBMITTED),
            idempotencyKey = "k1"
        )

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { remoteSource.submitClaim(any()) }
    }
}
