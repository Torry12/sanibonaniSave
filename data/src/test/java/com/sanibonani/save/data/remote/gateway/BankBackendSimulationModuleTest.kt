package com.sanibonani.save.data.remote.gateway

import com.sanibonani.save.domain.gateway.BankBackendSimulationModule
import com.sanibonani.save.domain.gateway.BankSimulationGateway
import com.sanibonani.save.domain.model.Money
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BankBackendSimulationModuleTest {

    private lateinit var module: BankBackendSimulationModule
    private val gateway: BankSimulationGateway = mockk()

    @Before
    fun setUp() {
        module = BankBackendSimulationModule(gateway)
    }

    @Test
    fun `simulateMonthOfActivity delegates to gateway successfully`() = runBlocking {
        val groupId = "group_1"
        val memberNames = listOf("Alice", "Bob", "Charlie", "David", "Eve")
        val amount = Money("500.0")

        coEvery { gateway.simulateBatchTransfers(any(), any()) } returns Result.success(listOf("tx1", "tx2"))

        val result = module.simulateMonthOfActivity(groupId, memberNames, amount)

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { gateway.simulateBatchTransfers(groupId, any()) }
    }

    @Test
    fun `simulateSinglePayment delegates to gateway successfully`() = runBlocking {
        val groupId = "group_1"
        val amount = Money("250.0")
        val reference = "REF-XYZ"

        coEvery { gateway.simulateIncomingTransfer(any(), any(), any(), any()) } returns Result.success("tx_123")

        val result = module.simulateSinglePayment(groupId, amount, reference)

        assertTrue(result.isSuccess)
        assertEquals("tx_123", result.getOrNull())
        coVerify(exactly = 1) { gateway.simulateIncomingTransfer(groupId, amount, reference, "Simulated Direct Deposit") }
    }
}
