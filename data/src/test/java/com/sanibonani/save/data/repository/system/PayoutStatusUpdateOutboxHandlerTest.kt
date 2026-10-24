package com.sanibonani.save.data.repository.system

import com.sanibonani.save.data.datasource.LocalPayoutDataSource
import com.sanibonani.save.data.datasource.RemotePayoutDataSource
import com.sanibonani.save.domain.model.AuditLog
import com.sanibonani.save.domain.model.PayoutRequest
import com.sanibonani.save.domain.model.PayoutStatus
import com.sanibonani.save.domain.model.PendingOperation
import com.sanibonani.save.domain.repository.admin.AuditLogRepository
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * An admin approving a payout with no signal is a decision the server still has to hear.
 * These tests pin that the queued transition replays exactly as the online path applied it:
 * status on the server, fresh row in the local cache, one audit line, and nothing at all when
 * the server refuses.
 */
class PayoutStatusUpdateOutboxHandlerTest {

    private val remoteSource = mockk<RemotePayoutDataSource>()
    private val localSource = mockk<LocalPayoutDataSource>(relaxed = true)
    private val auditLogRepo = mockk<AuditLogRepository>(relaxed = true)
    private val json = Json { ignoreUnknownKeys = true }

    private lateinit var handler: PayoutStatusUpdateOutboxHandler

    @Before
    fun setUp() {
        handler = PayoutStatusUpdateOutboxHandler(remoteSource, localSource, auditLogRepo, json)
    }

    private fun payout(status: PayoutStatus) = PayoutRequest(
        id = "p-1",
        groupId = "group-1",
        memberId = "member-1",
        bankName = "FNB",
        accountNo = "12345678",
        branchCode = "250015",
        status = status,
        processedBy = "admin-1"
    )

    private fun operation(payload: String) = PendingOperation(
        id = "status-1",
        type = "payout_status_update",
        payload = payload,
        groupId = null,
        createdAt = 1L
    )

    @Test
    fun `a queued approval is applied on the server then mirrored locally`() = runBlocking {
        val payload = """{"payout_id":"p-1","status":"GROUP_APPROVED","reference":"REF-77"}"""
        coEvery {
            remoteSource.updatePayoutStatus("p-1", PayoutStatus.GROUP_APPROVED, "REF-77", metadata = null)
        } returns Result.success(Unit)
        coEvery { remoteSource.getPayoutById("p-1") } returns Result.success(payout(PayoutStatus.GROUP_APPROVED))

        val outcome = handler.handle(operation(payload))

        assertTrue(outcome.isSuccess)
        coVerify(exactly = 1) { localSource.savePayout(match { it.status == PayoutStatus.GROUP_APPROVED }) }
        coVerify(exactly = 1) { auditLogRepo.logEvent(match { it.action == "PAYOUT_STATUS_CHANGED" }) }
    }

    @Test
    fun `the queued metadata travels with the replay`() = runBlocking {
        val stored = buildJsonObject {
            put("payout_id", "p-1")
            put("status", "PROCESSING")
            put("metadata", buildJsonObject { put("admin_note", "paid at branch") })
        }
        coEvery {
            remoteSource.updatePayoutStatus(any(), any(), any(), metadata = any())
        } returns Result.success(Unit)
        coEvery { remoteSource.getPayoutById("p-1") } returns Result.success(payout(PayoutStatus.PROCESSING))

        assertTrue(handler.handle(operation(stored.toString())).isSuccess)

        coVerify(exactly = 1) {
            remoteSource.updatePayoutStatus(
                payoutId = "p-1",
                status = PayoutStatus.PROCESSING,
                reference = null,
                processedBy = null,
                processedAt = null,
                metadata = match { it["admin_note"]?.jsonPrimitive?.content == "paid at branch" }
            )
        }
    }

    @Test
    fun `the acting admin replays as the processor`() = runBlocking {
        // The dual-control gate reads processed_by, so a transition queued offline must replay
        // with the same admin stamped, or the offline approval would be invisible to the gate.
        val payload = """{"payout_id":"p-1","status":"PROCESSING","actor_id":"admin-2"}"""
        coEvery {
            remoteSource.updatePayoutStatus("p-1", PayoutStatus.PROCESSING, null, "admin-2", null, null)
        } returns Result.success(Unit)
        coEvery { remoteSource.getPayoutById("p-1") } returns Result.success(payout(PayoutStatus.PROCESSING))

        assertTrue(handler.handle(operation(payload)).isSuccess)

        coVerify(exactly = 1) {
            remoteSource.updatePayoutStatus("p-1", PayoutStatus.PROCESSING, null, "admin-2", null, null)
        }
    }

    @Test
    fun `a server rejection leaves the cache and the audit trail untouched`() = runBlocking {
        coEvery {
            remoteSource.updatePayoutStatus(any(), any(), any(), metadata = any())
        } returns Result.failure(IOException("HTTP 500 Internal Server Error"))

        val outcome = handler.handle(operation("""{"payout_id":"p-1","status":"GROUP_APPROVED"}"""))

        assertTrue("the lane must stay halted so the transition is retried", outcome.isFailure)
        coVerify(exactly = 0) { localSource.savePayout(any()) }
        coVerify(exactly = 0) { auditLogRepo.logEvent(any()) }
    }

    @Test
    fun `an unknown status is rejected before anything is written`() = runBlocking {
        val outcome = handler.handle(operation("""{"payout_id":"p-1","status":"TELEPORTED"}"""))

        assertTrue(outcome.isFailure)
        coVerify(exactly = 0) { remoteSource.updatePayoutStatus(any(), any(), any(), metadata = any()) }
        coVerify(exactly = 0) { localSource.savePayout(any()) }
    }

    @Test
    fun `a payload without a payout id names the missing key`() = runBlocking {
        val outcome = handler.handle(operation("""{"status":"GROUP_APPROVED"}"""))

        assertTrue(outcome.isFailure)
        assertEquals(
            "Outbox payload is missing 'payout_id'",
            outcome.exceptionOrNull()?.message
        )
        coVerify(exactly = 0) { remoteSource.updatePayoutStatus(any(), any(), any(), metadata = any()) }
    }

    @Test
    fun `the audit line carries the transition the admin asked for`() = runBlocking {
        val captured = slot<AuditLog>()
        coEvery {
            remoteSource.updatePayoutStatus(any(), any(), any(), metadata = any())
        } returns Result.success(Unit)
        coEvery { remoteSource.getPayoutById("p-1") } returns Result.success(payout(PayoutStatus.REJECTED))
        coEvery { auditLogRepo.logEvent(capture(captured)) } just Runs

        handler.handle(operation("""{"payout_id":"p-1","status":"REJECTED"}"""))

        assertEquals("PAYOUT_STATUS_CHANGED", captured.captured.action)
        assertEquals("group-1", captured.captured.targetGroupId)
        assertEquals("member-1", captured.captured.targetMemberId)
        val details = captured.captured.details as? JsonObject
        assertEquals("REJECTED", details?.get("status")?.jsonPrimitive?.content)
    }
}
