package com.sanibonani.save.domain.usecase.payment
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.payment.BankRepository
import com.sanibonani.save.domain.repository.payment.FinancialProviderRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

class SyncBankTransactionsUseCaseTest {

    private lateinit var useCase: SyncBankTransactionsUseCase
    private val financialProviderRepo: FinancialProviderRepository = mockk()
    private val bankRepo: BankRepository = mockk()

    @Before
    fun setUp() {
        useCase = SyncBankTransactionsUseCase(financialProviderRepo, bankRepo)
    }

    @Test
    fun `invoke should sync balance and transactions successfully`() = runBlocking {
        val groupId = "group_1"
        val providerId = "prov_1"

        val connection = BankConnection(
            id = "conn_1",
            groupId = groupId,
            providerId = providerId,
            status = BankConnectionStatus.CONNECTED,
            linkedAt = "2024-01-01T00:00:00Z"
        )

        coEvery { bankRepo.observeBankConnection(groupId) } returns flowOf(Result.success(connection))

        val balanceResponse = FinancialOperationResult(
            success = true,
            data = mapOf("balance" to "1000.00")
        )
        val txResponse = FinancialOperationResult(
            success = true,
            data = mapOf("raw_statement" to "test_statement_data", "format" to "csv")
        )

        coEvery { financialProviderRepo.executeOperation(match { it.operation == FinancialOperationType.GET_BALANCE }) } returns Result.success(balanceResponse)
        coEvery { financialProviderRepo.executeOperation(match { it.operation == FinancialOperationType.GET_TRANSACTIONS }) } returns Result.success(txResponse)
        coEvery { bankRepo.importRawStatement("test_statement_data", "csv", groupId) } returns Result.success(Unit)

        val result = useCase(groupId)

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { bankRepo.importRawStatement("test_statement_data", "csv", groupId) }
    }

    @Test
    fun `invoke should fail if not connected`() = runBlocking {
        val groupId = "group_1"

        val connection = BankConnection(
            id = "conn_1",
            groupId = groupId,
            providerId = "prov_1",
            status = BankConnectionStatus.PENDING,
            linkedAt = "2024-01-01T00:00:00Z"
        )

        coEvery { bankRepo.observeBankConnection(groupId) } returns flowOf(Result.success(connection))

        val result = useCase(groupId)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("not currently connected") == true)
    }

    private fun connectedConnection() = BankConnection(
        id = "conn_1",
        groupId = "group_1",
        providerId = "prov_1",
        status = BankConnectionStatus.CONNECTED,
        linkedAt = "2024-01-01T00:00:00Z"
    )

    @Test
    fun `a failed balance lookup aborts before importing transactions`() = runBlocking {
        val groupId = "group_1"
        coEvery { bankRepo.observeBankConnection(groupId) } returns flowOf(Result.success(connectedConnection()))
        coEvery {
            financialProviderRepo.executeOperation(match { it.operation == FinancialOperationType.GET_BALANCE })
        } returns Result.success(FinancialOperationResult(success = false, errorCode = "E_BAL"))
        coEvery {
            financialProviderRepo.executeOperation(match { it.operation == FinancialOperationType.GET_TRANSACTIONS })
        } returns Result.success(FinancialOperationResult(success = true, data = mapOf("raw_statement" to "RAW")))

        val result = useCase(groupId)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Balance Sync Failed: E_BAL") == true)
        coVerify(exactly = 0) { bankRepo.importRawStatement(any(), any(), any()) }
    }

    @Test
    fun `a failed transaction lookup is reported`() = runBlocking {
        val groupId = "group_1"
        coEvery { bankRepo.observeBankConnection(groupId) } returns flowOf(Result.success(connectedConnection()))
        coEvery {
            financialProviderRepo.executeOperation(match { it.operation == FinancialOperationType.GET_BALANCE })
        } returns Result.success(FinancialOperationResult(success = true, data = mapOf("balance" to "10.00")))
        coEvery {
            financialProviderRepo.executeOperation(match { it.operation == FinancialOperationType.GET_TRANSACTIONS })
        } returns Result.success(FinancialOperationResult(success = false, errorCode = "E_TX"))

        val result = useCase(groupId)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Transaction Sync Failed: E_TX") == true)
    }

    @Test
    fun `a raw statement wins over a transaction list and defaults to the json format`() = runBlocking {
        val groupId = "group_1"
        coEvery { bankRepo.observeBankConnection(groupId) } returns flowOf(Result.success(connectedConnection()))
        coEvery {
            financialProviderRepo.executeOperation(match { it.operation == FinancialOperationType.GET_BALANCE })
        } returns Result.success(FinancialOperationResult(success = true, data = mapOf("balance" to "10.00")))
        coEvery {
            financialProviderRepo.executeOperation(match { it.operation == FinancialOperationType.GET_TRANSACTIONS })
        } returns Result.success(
            FinancialOperationResult(
                success = true,
                data = mapOf("raw_statement" to "RAW", "transactions" to "TX")
            )
        )
        coEvery { bankRepo.importRawStatement("RAW", "json", groupId) } returns Result.success(Unit)

        val result = useCase(groupId)

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { bankRepo.importRawStatement("RAW", "json", groupId) }
    }

    @Test
    fun `a response without statement data is a successful no-op`() = runBlocking {
        val groupId = "group_1"
        coEvery { bankRepo.observeBankConnection(groupId) } returns flowOf(Result.success(connectedConnection()))
        coEvery {
            financialProviderRepo.executeOperation(match { it.operation == FinancialOperationType.GET_BALANCE })
        } returns Result.success(FinancialOperationResult(success = true, data = mapOf("balance" to "10.00")))
        coEvery {
            financialProviderRepo.executeOperation(match { it.operation == FinancialOperationType.GET_TRANSACTIONS })
        } returns Result.success(FinancialOperationResult(success = true))

        val result = useCase(groupId)

        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { bankRepo.importRawStatement(any(), any(), any()) }
    }

    @Test
    fun `a failed connection lookup is reported as no connection`() = runBlocking {
        val groupId = "group_1"
        coEvery { bankRepo.observeBankConnection(groupId) } returns flowOf(Result.failure(IOException("offline")))

        val result = useCase(groupId)

        assertTrue(result.isFailure)
        assertEquals("No active bank connection found for group.", result.exceptionOrNull()?.message)
        coVerify(exactly = 0) { financialProviderRepo.executeOperation(any()) }
    }
}
