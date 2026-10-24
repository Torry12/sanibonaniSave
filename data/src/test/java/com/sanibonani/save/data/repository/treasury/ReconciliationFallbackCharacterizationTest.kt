package com.sanibonani.save.data.repository.treasury

import com.sanibonani.save.data.datasource.LocalReconciliationDataSource
import com.sanibonani.save.data.datasource.RemoteReconciliationDataSource
import com.sanibonani.save.data.local.GroupDao
import com.sanibonani.save.data.local.GroupEntity
import com.sanibonani.save.data.local.LedgerDao
import com.sanibonani.save.data.local.LedgerEntryEntity
import com.sanibonani.save.data.local.SanibonaniDatabase
import com.sanibonani.save.domain.model.Money
import com.sanibonani.save.domain.model.ReconciliationRecord
import com.sanibonani.save.domain.model.ReconciliationStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * An offline reconciliation is a report, not a receipt: the device cannot settle the books, so it
 * audits what it has cached and says so. These tests pin the honest shape of that fallback - the
 * server's own result is never overwritten, the local audit is always attributed to the local
 * engine, and a cancelled run is not recorded as one at all.
 */
class ReconciliationFallbackCharacterizationTest {

    private val remoteSource = mockk<RemoteReconciliationDataSource>()
    private val localSource = mockk<LocalReconciliationDataSource>(relaxed = true)
    private val db = mockk<SanibonaniDatabase>(relaxed = true)
    private val ledgerDao = mockk<LedgerDao>(relaxed = true)
    private val groupDao = mockk<GroupDao>(relaxed = true)

    private lateinit var repository: ReconciliationRepositoryImpl

    @Before
    fun setUp() {
        every { db.ledgerDao() } returns ledgerDao
        every { db.groupDao() } returns groupDao
        repository = ReconciliationRepositoryImpl(remoteSource, localSource, javax.inject.Provider { db })
    }

    private fun ledgerEntry(amount: String) = LedgerEntryEntity(
        id = "ledger-${amount}",
        groupId = GROUP_ID,
        transactionId = "tx-$amount",
        amount = Money(amount),
        balanceAfter = Money(amount),
        description = "contribution",
        category = "contribution",
        createdAt = "2026-10-18T00:00:00Z"
    )

    private fun cachedBalance(amount: String) =
        mockk<GroupEntity> { every { balance } returns Money(amount) }

    private fun runLocalAudit(entries: List<LedgerEntryEntity>, balance: GroupEntity?) {
        every { ledgerDao.observeLedger(GROUP_ID) } returns flowOf(entries)
        coEvery { groupDao.getGroupById(GROUP_ID) } returns balance
    }

    @Test
    fun `a server reconciliation is cached exactly as the server returned it`() = runBlocking {
        val fromServer = ReconciliationRecord(
            id = "recon-1",
            groupId = GROUP_ID,
            status = ReconciliationStatus.DISCREPANCY,
            ledgerBalance = Money("900.00"),
            actualBalance = Money("1000.00"),
            discrepancy = Money("-100.00"),
            performedBy = "reconcile_group_engine_v2"
        )
        coEvery { remoteSource.performReconciliation(GROUP_ID) } returns Result.success(fromServer)

        val outcome = repository.performReconciliation(GROUP_ID)

        assertEquals(fromServer, outcome.getOrThrow())
        coVerify(exactly = 1) { localSource.saveReconciliations(listOf(fromServer)) }
    }

    @Test
    fun `a refused server reconciliation falls back to an audit attributed to the local engine`() =
        runBlocking {
            coEvery { remoteSource.performReconciliation(GROUP_ID) } throws
                IOException("HTTP 500 Internal Server Error")
            runLocalAudit(
                entries = listOf(ledgerEntry("100.00")),
                balance = cachedBalance("250.00")
            )

            val outcome = repository.performReconciliation(GROUP_ID)

            val record = outcome.getOrThrow()
            assertEquals("LOCAL_RECONCILIATION_ENGINE", record.performedBy)
            assertEquals(ReconciliationStatus.DISCREPANCY, record.status)
            assertEquals(Money("100.00"), record.ledgerBalance)
            assertEquals(Money("250.00"), record.actualBalance)
            assertEquals(Money("150.00"), record.discrepancy)
            coVerify(exactly = 1) { localSource.saveReconciliations(listOf(record)) }
        }

    @Test
    fun `the local audit balances on cents, not on floats`() = runBlocking {
        coEvery { remoteSource.performReconciliation(GROUP_ID) } throws
            IOException("HTTP 503 Service Unavailable")
        runLocalAudit(
            entries = listOf(ledgerEntry("0.10"), ledgerEntry("0.20")),
            balance = cachedBalance("0.30")
        )

        val record = repository.performReconciliation(GROUP_ID).getOrThrow()

        assertEquals(Money("0.30"), record.ledgerBalance)
        assertEquals(Money("0.00"), record.discrepancy)
        assertEquals(ReconciliationStatus.BALANCED, record.status)
    }

    @Test
    fun `a cancelled reconciliation is never recorded as a local audit`() = runBlocking {
        coEvery { remoteSource.performReconciliation(GROUP_ID) } throws
            CancellationException("sync cancelled by user")

        assertThrows(CancellationException::class.java) {
            runBlocking { repository.performReconciliation(GROUP_ID) }
        }

        coVerify(exactly = 0) { localSource.saveReconciliations(any()) }
    }

    @Test
    fun `a resolution the server refuses leaves the local discrepancy open`() = runBlocking {
        coEvery {
            remoteSource.resolveDiscrepancy("recon-1", "verified against statement")
        } throws IllegalStateException("HTTP 403 row-level security rejected the write")

        val outcome = repository.resolveDiscrepancy("recon-1", "verified against statement")

        assertTrue("the admin must hear that the discrepancy is still open", outcome.isFailure)
        coVerify(exactly = 0) {
            localSource.updateReconciliationStatus(any(), any(), any())
        }
    }

    private companion object {
        const val GROUP_ID = "group-1"
    }
}
