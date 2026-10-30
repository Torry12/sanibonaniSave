package com.sanibonani.save.data.repository.admin

import com.sanibonani.save.data.datasource.RemoteAuditDataSource
import com.sanibonani.save.data.repository.mappers.DataPayloadFactory
import com.sanibonani.save.domain.model.AuditLog
import com.sanibonani.save.domain.model.LedgerEntry
import com.sanibonani.save.domain.model.Money
import com.sanibonani.save.domain.model.Payment
import io.mockk.CapturingSlot
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * `audit_logs.actor_id` is the answer to "who did this". A system-generated entry with a blank
 * actor reads like an unattributed human action, which is the one thing a compliance trail cannot
 * be. The reviewer's own id must still survive untouched.
 */
class AuditLogAttributionTest {

    private val remoteSource: RemoteAuditDataSource = mockk()
    private val payloadFactory = mockk<DataPayloadFactory>(relaxed = true)

    private fun repository() = AuditLogRepositoryImpl(remoteSource, payloadFactory)

    private fun insertCapturing(captured: CapturingSlot<AuditLog>) {
        coEvery { remoteSource.insertAuditLog(capture(captured)) } returns Result.success(Unit)
    }

    @Test
    fun `an event bus ledger entry is stamped with the system actor`() = runBlocking {
        val captured = slot<AuditLog>()
        insertCapturing(captured)

        repository().logLedgerEntry(
            LedgerEntry(groupId = "g1", amount = Money("250.00"), description = "Contribution")
        )

        assertEquals("SYSTEM", captured.captured.actorId)
        assertEquals("LEDGER_ENTRY_CREATED", captured.captured.action)
    }

    @Test
    fun `a reviewer acting through logEvent keeps their own id`() = runBlocking {
        val captured = slot<AuditLog>()
        insertCapturing(captured)

        repository().logEvent(AuditLog(actorId = "admin-1", action = "PAYOUT_APPROVED"))

        assertEquals("admin-1", captured.captured.actorId)
    }

    @Test
    fun `a blank actor is stamped as the system instead of logged as nobody`() = runBlocking {
        val captured = slot<AuditLog>()
        insertCapturing(captured)

        repository().logEvent(AuditLog(actorId = "  ", action = "LOAN_REPAID"))

        assertEquals("SYSTEM", captured.captured.actorId)
    }

    @Test
    fun `a payment audit entry names the member it belongs to`() = runBlocking {
        val captured = slot<AuditLog>()
        insertCapturing(captured)

        repository().logPayment(
            Payment(id = "pay-1", groupId = "g1", memberId = "m1", amount = Money("100.00"))
        )

        assertEquals("SYSTEM", captured.captured.actorId)
        assertEquals("m1", captured.captured.targetMemberId)
    }
}
