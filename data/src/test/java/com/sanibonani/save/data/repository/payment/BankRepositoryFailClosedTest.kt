package com.sanibonani.save.data.repository.payment

import com.sanibonani.save.data.local.BankTransactionDao
import com.sanibonani.save.data.local.SanibonaniDatabase
import com.sanibonani.save.data.repository.mappers.DataPayloadFactory
import com.sanibonani.save.domain.model.BankTransaction
import com.sanibonani.save.domain.model.BankTransactionStatus
import com.sanibonani.save.domain.model.Money
import com.sanibonani.save.domain.service.BankParserFactory
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.result.PostgrestResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * A bank reconciliation is an accounting fact: it only exists once the server has accepted it.
 * These tests pin that a rejected server call surfaces as a failure and leaves the local row
 * alone, instead of the transaction quietly flipping to RECONCILED on the device.
 */
class BankRepositoryFailClosedTest {

    private val supabase = mockk<SupabaseClient>()
    private val postgrest = mockk<Postgrest>(relaxed = true)
    private val db = mockk<SanibonaniDatabase>(relaxed = true)
    private val bankTxDao = mockk<BankTransactionDao>(relaxed = true)
    private val rpcResult = mockk<PostgrestResult>(relaxed = true)

    private lateinit var repository: BankRepositoryImpl

    @Before
    fun setUp() {
        // `SupabaseClient.postgrest` is an extension property; on a mock the real plugin
        // resolution would crash, so the generated holder is stubbed instead.
        mockkStatic("io.github.jan.supabase.postgrest.PostgrestKt")
        every { supabase.postgrest } returns postgrest
        every { db.bankTransactionDao() } returns bankTxDao

        repository = BankRepositoryImpl(
            supabaseProvider = javax.inject.Provider { supabase },
            dbProvider = javax.inject.Provider { db },
            payloadFactory = DataPayloadFactory(),
            parserFactory = mockk<BankParserFactory>(relaxed = true)
        )
    }

    @After
    fun tearDown() {
        unmockkStatic("io.github.jan.supabase.postgrest.PostgrestKt")
    }

    private fun bankTransaction() = BankTransaction(
        id = "bank-tx-1",
        groupId = "group-1",
        bankReference = "REF-0001",
        amount = Money("250.00"),
        transactionDate = "2026-10-18",
        status = BankTransactionStatus.UNRECONCILED
    )

    /**
     * Stands in for a write the server refused. The request builders are inline reified
     * functions, so the failure is injected where the table is resolved instead.
     */
    private fun failServerWrite(throwable: Throwable) {
        every { postgrest["bank_transactions"] } throws throwable
    }

    @Test
    fun `rejected reconcile rpc must not mark the local row reconciled`() = runBlocking {
        coEvery {
            postgrest.rpc(any<String>(), any<JsonObject>(), any())
        } throws IOException("HTTP 500 Internal Server Error")

        val outcome = repository.reconcileManual("bank-tx-1", "ledger-1")

        assertTrue("server rejection must surface to the caller", outcome.isFailure)
        coVerify(exactly = 0) {
            bankTxDao.updateReconciliationStatus(any(), any(), any())
        }
    }

    @Test
    fun `accepted reconcile rpc writes the local row`() = runBlocking {
        coEvery { postgrest.rpc(any<String>(), any<JsonObject>(), any()) } returns rpcResult

        val outcome = repository.reconcileManual("bank-tx-1", "ledger-1")

        assertTrue(outcome.isSuccess)
        coVerify(exactly = 1) {
            bankTxDao.updateReconciliationStatus("bank-tx-1", BankTransactionStatus.RECONCILED, "ledger-1")
        }
    }

    @Test
    fun `a cancelled reconcile must not be recorded as a local reconciliation`() = runBlocking {
        coEvery {
            postgrest.rpc(any<String>(), any<JsonObject>(), any())
        } throws CancellationException("support mode torn down")

        try {
            repository.reconcileManual("bank-tx-1", "ledger-1")
            fail("cancellation must propagate, never be swallowed into a Result")
        } catch (expected: CancellationException) {
            // expected
        }
        coVerify(exactly = 0) {
            bankTxDao.updateReconciliationStatus(any(), any(), any())
        }
    }

    @Test
    fun `rejected ignore write must not mark the local row ignored`() = runBlocking {
        failServerWrite(IOException("HTTP 500 Internal Server Error"))

        val outcome = repository.ignoreTransaction("bank-tx-1", "duplicate statement line")

        assertTrue(outcome.isFailure)
        coVerify(exactly = 0) {
            bankTxDao.updateReconciliationStatus(any(), any(), any())
        }
    }

    @Test
    fun `rejected statement import must not cache rows locally`() = runBlocking {
        failServerWrite(IOException("HTTP 500 Internal Server Error"))

        val outcome = repository.importBankTransactions(listOf(bankTransaction()))

        assertTrue(outcome.isFailure)
        coVerify(exactly = 0) { bankTxDao.upsert(any()) }
    }

    @Test
    fun `a timeout during import is reported, not swallowed`() = runBlocking {
        failServerWrite(java.net.SocketTimeoutException("timeout"))

        val outcome = repository.importBankTransactions(listOf(bankTransaction()))

        assertTrue(outcome.isFailure)
        coVerify(exactly = 0) { bankTxDao.upsert(any()) }
    }

    @Test
    fun `importing nothing never reaches the network`() = runBlocking {
        failServerWrite(AssertionError("an empty import must not contact the server"))

        val outcome = repository.importBankTransactions(emptyList())

        assertTrue(outcome.isSuccess)
        coVerify(exactly = 0) { bankTxDao.upsert(any()) }
    }
}
