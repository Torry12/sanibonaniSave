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
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LinkBankAccountUseCaseTest {

    private lateinit var useCase: LinkBankAccountUseCase
    private val financialProviderRepo: FinancialProviderRepository = mockk()
    private val bankRepo: BankRepository = mockk(relaxed = true)

    @Before
    fun setUp() {
        useCase = LinkBankAccountUseCase(financialProviderRepo, bankRepo)
    }

    @Test
    fun `a successful link stores a pending connection with its metadata`() = runBlocking {
        coEvery {
            financialProviderRepo.executeOperation(match { it.operation == FinancialOperationType.LINK_ACCOUNT && it.providerId == "prov-1" })
        } returns Result.success(
            FinancialOperationResult(
                success = true,
                data = mapOf("auth_url" to "https://auth.example/start", "connection_id" to "conn-9")
            )
        )
        val saved = slot<BankConnection>()
        coEvery { bankRepo.saveBankConnection(capture(saved)) } returns Result.success(Unit)

        val outcome = useCase("group-1", "prov-1", mapOf("flow" to "redirect"))

        val link = outcome.getOrThrow()
        assertEquals("https://auth.example/start", link.authUrl)
        assertEquals("conn-9", link.connectionId)
        assertEquals("conn-9", saved.captured.id)
        assertEquals("group-1", saved.captured.groupId)
        assertEquals("prov-1", saved.captured.providerId)
        assertEquals(BankConnectionStatus.PENDING, saved.captured.status)
        assertEquals(mapOf("flow" to "redirect"), saved.captured.metadata)
        assertTrue(saved.captured.linkedAt.isNotBlank())
    }

    @Test
    fun `a provider without a connection id gets a pending identifier`() = runBlocking {
        coEvery { financialProviderRepo.executeOperation(any()) } returns Result.success(
            FinancialOperationResult(success = true, data = mapOf("auth_url" to "https://auth.example/start"))
        )
        val saved = slot<BankConnection>()
        coEvery { bankRepo.saveBankConnection(capture(saved)) } returns Result.success(Unit)

        val link = useCase("group-1", "prov-1").getOrThrow()

        assertTrue(link.connectionId.startsWith("pending_"))
        assertEquals(link.connectionId, saved.captured.id)
    }

    @Test
    fun `fallback pending identifiers stay unique across rapid links`() = runBlocking {
        coEvery { financialProviderRepo.executeOperation(any()) } returns Result.success(
            FinancialOperationResult(success = true, data = mapOf("auth_url" to "https://auth.example/start"))
        )
        coEvery { bankRepo.saveBankConnection(any()) } returns Result.success(Unit)

        val first = useCase("group-1", "prov-1").getOrThrow().connectionId
        val second = useCase("group-2", "prov-1").getOrThrow().connectionId

        assertTrue(first.startsWith("pending_"))
        assertTrue(second.startsWith("pending_"))
        assertNotEquals(
            "Two links started in the same millisecond must not share a primary key.",
            first,
            second
        )
    }

    @Test
    fun `a response without an authorization url fails without saving`() = runBlocking {
        coEvery { financialProviderRepo.executeOperation(any()) } returns Result.success(
            FinancialOperationResult(success = true, data = mapOf("connection_id" to "conn-9"))
        )

        val outcome = useCase("group-1", "prov-1")

        assertTrue(outcome.isFailure)
        assertEquals("Missing authorization URL from provider", outcome.exceptionOrNull()?.message)
        coVerify(exactly = 0) { bankRepo.saveBankConnection(any()) }
    }

    @Test
    fun `a rejected provider request surfaces its message without saving`() = runBlocking {
        coEvery { financialProviderRepo.executeOperation(any()) } returns Result.success(
            FinancialOperationResult(success = false, message = "Provider offline")
        )

        val outcome = useCase("group-1", "prov-1")

        assertTrue(outcome.isFailure)
        assertEquals("Provider offline", outcome.exceptionOrNull()?.message)
        coVerify(exactly = 0) { bankRepo.saveBankConnection(any()) }
    }
}
