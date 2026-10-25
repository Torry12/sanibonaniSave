package com.sanibonani.save.domain.usecase.payment

import com.sanibonani.save.domain.model.BankConnection
import com.sanibonani.save.domain.model.BankConnectionStatus
import com.sanibonani.save.domain.model.FinancialOperationResult
import com.sanibonani.save.domain.model.FinancialOperationType
import com.sanibonani.save.domain.repository.payment.BankRepository
import com.sanibonani.save.domain.repository.payment.FinancialProviderRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FinalizeBankConnectionUseCaseTest {

    private lateinit var useCase: FinalizeBankConnectionUseCase
    private val bankRepo: BankRepository = mockk(relaxed = true)
    private val financialProviderRepo: FinancialProviderRepository = mockk()

    @Before
    fun setUp() {
        useCase = FinalizeBankConnectionUseCase(bankRepo, financialProviderRepo)
    }

    private fun pendingConnection(metadata: Map<String, String> = mapOf("provider_id" to "prov-1")) = BankConnection(
        id = "conn-1",
        groupId = "group-1",
        providerId = "prov-1",
        status = BankConnectionStatus.PENDING,
        linkedAt = "2024-01-01T00:00:00Z",
        metadata = metadata
    )

    @Test
    fun `a failed authorization deletes the pending connection`() = runBlocking {
        coEvery { bankRepo.deleteBankConnection("group-1") } returns Result.success(Unit)

        val outcome = useCase("group-1", "conn-1", "cancelled")

        assertTrue(outcome.isFailure)
        assertTrue(outcome.exceptionOrNull()?.message?.contains("cancelled") == true)
        coVerify(exactly = 1) { bankRepo.deleteBankConnection("group-1") }
        coVerify(exactly = 0) { financialProviderRepo.executeOperation(any()) }
        coVerify(exactly = 0) { bankRepo.saveBankConnection(any()) }
    }

    @Test
    fun `a successful authorization verifies and connects the stored account`() = runBlocking {
        coEvery { bankRepo.observeBankConnection("group-1") } returns flowOf(Result.success(pendingConnection()))
        val request = slot<com.sanibonani.save.domain.model.FinancialOperationRequest>()
        coEvery { financialProviderRepo.executeOperation(capture(request)) } returns Result.success(
            FinancialOperationResult(
                success = true,
                data = mapOf("account_name" to "Main Account", "account_number_hidden" to "****1234")
            )
        )
        val saved = slot<BankConnection>()
        coEvery { bankRepo.saveBankConnection(capture(saved)) } returns Result.success(Unit)

        val outcome = useCase("group-1", "conn-1", "success", mapOf("provider_id" to "prov-1"))

        assertTrue(outcome.isSuccess)
        assertEquals(FinancialOperationType.VERIFY_ACCOUNT, request.captured.operation)
        assertEquals("prov-1", request.captured.providerId)
        assertEquals("group-1", request.captured.groupId)
        assertEquals("conn-1", request.captured.metadata["connection_id"])
        assertEquals("prov-1", request.captured.metadata["provider_id"])

        assertEquals(BankConnectionStatus.CONNECTED, saved.captured.status)
        assertEquals("Main Account", saved.captured.accountName)
        assertEquals("****1234", saved.captured.accountNumberHidden)
        assertEquals(
            mapOf(
                "provider_id" to "prov-1",
                "account_name" to "Main Account",
                "account_number_hidden" to "****1234"
            ),
            saved.captured.metadata
        )
    }

    @Test
    fun `a rejected verification leaves the pending connection untouched`() = runBlocking {
        coEvery { financialProviderRepo.executeOperation(any()) } returns Result.success(
            FinancialOperationResult(success = false, message = "Verification failed")
        )

        val outcome = useCase("group-1", "conn-1", "success")

        assertTrue(outcome.isFailure)
        assertEquals("Verification failed", outcome.exceptionOrNull()?.message)
        coVerify(exactly = 0) { bankRepo.deleteBankConnection(any()) }
        coVerify(exactly = 0) { bankRepo.saveBankConnection(any()) }
    }

    @Test
    fun `a missing pending connection is reported after verification`() = runBlocking {
        coEvery { financialProviderRepo.executeOperation(any()) } returns Result.success(
            FinancialOperationResult(success = true)
        )
        coEvery { bankRepo.observeBankConnection("group-1") } returns flowOf(Result.success(null))

        val outcome = useCase("group-1", "conn-1", "success")

        assertTrue(outcome.isFailure)
        assertEquals("Pending connection not found for group", outcome.exceptionOrNull()?.message)
        coVerify(exactly = 0) { bankRepo.saveBankConnection(any()) }
    }
}
