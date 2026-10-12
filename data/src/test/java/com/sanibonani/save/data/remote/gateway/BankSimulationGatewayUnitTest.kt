package com.sanibonani.save.data.remote.gateway

import com.sanibonani.save.data.local.BankTransactionDao
import com.sanibonani.save.data.local.SanibonaniDatabase
import com.sanibonani.save.domain.gateway.SimulatedTransaction
import com.sanibonani.save.domain.model.Money
import io.github.jan.supabase.SupabaseClient
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class BankSimulationGatewayUnitTest {

    private val supabase: SupabaseClient = mockk(relaxed = true)
    private val db: SanibonaniDatabase = mockk(relaxed = true)
    private val dao: BankTransactionDao = mockk(relaxed = true)

    private lateinit var gateway: BankSimulationGatewayImpl

    @Before
    fun setUp() {
        every { db.bankTransactionDao() } returns dao
        gateway = BankSimulationGatewayImpl(supabase, db)
    }

    @Test
    fun `simulateIncomingTransfer caches locally and returns transaction id`() = runBlocking {
        val groupId = "group_1"
        val amount = Money(500.0)
        val reference = "TEST-REF-99"

        coEvery { dao.upsert(any()) } returns Unit

        val result = gateway.simulateIncomingTransfer(groupId, amount, reference, "Test Transfer")

        assertTrue(result.isSuccess)
        assertNotNull(result.getOrNull())
        coVerify(exactly = 1) { dao.upsert(any()) }
    }

    @Test
    fun `simulateBatchTransfers caches locally and returns transaction ids`() = runBlocking {
        val groupId = "group_1"
        val txs = listOf(
            SimulatedTransaction(Money(100.0), "REF-1", "Desc 1"),
            SimulatedTransaction(Money(200.0), "REF-2", "Desc 2")
        )

        coEvery { dao.upsert(any()) } returns Unit

        val result = gateway.simulateBatchTransfers(groupId, txs)

        assertTrue(result.isSuccess)
        val ids = result.getOrNull()
        assertNotNull(ids)
        assertEquals(2, ids?.size)
        coVerify(exactly = 1) { dao.upsert(any()) }
    }
}
