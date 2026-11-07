package com.sanibonani.save.data.repository.admin

import com.sanibonani.save.data.datasource.RemoteAuditDataSource
import com.sanibonani.save.data.repository.mappers.DataPayloadFactory
import com.sanibonani.save.domain.logging.AppLogger
import com.sanibonani.save.domain.model.AuditLog
import com.sanibonani.save.domain.model.Money
import com.sanibonani.save.domain.model.Payment
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A refused `audit_logs` insert is an accountability gap, and what it is a gap *about* depends on the
 * action. The line used to assert "the financial mutation it describes already succeeded" for every
 * write, which is simply untrue for `IMPERSONATION_REQUESTED` - no money moved - and an operator
 * reading it could not tell whether a payment or a support session went unrecorded.
 */
class AuditLogFailureWordingTest {

    private val remoteSource: RemoteAuditDataSource = mockk()
    private val payloadFactory = mockk<DataPayloadFactory>(relaxed = true)

    private fun repository() = AuditLogRepositoryImpl(remoteSource, payloadFactory)

    private fun refuseInsert() {
        coEvery { remoteSource.insertAuditLog(any()) } returns
            Result.failure(IllegalStateException("new row violates row-level security policy for audit_logs"))
    }

    private fun breadcrumbFor(action: String): String =
        AppLogger.getBreadcrumbs().firstOrNull { it.contains("'$action'") }.orEmpty()

    @Test
    fun `a money action is recognised as one and a support session is not`() {
        val repository = repository()

        assertTrue(repository.actionDescribesMoney("LOAN_DISBURSED"))
        assertTrue(repository.actionDescribesMoney("PAYOUT_COMPLETED"))
        assertTrue(repository.actionDescribesMoney("PAYMENT_RECORDED"))
        assertTrue(repository.actionDescribesMoney("LEDGER_ENTRY_CREATED"))
        assertFalse(repository.actionDescribesMoney("IMPERSONATION_REQUESTED"))
    }

    @Test
    fun `a refused impersonation record does not claim a financial mutation succeeded`() = runBlocking {
        // Unique per run: the breadcrumb buffer is process-wide and shared across test classes.
        val action = "IMPERSONATION_REQUESTED-wording-${System.nanoTime()}"
        refuseInsert()

        repository().logEvent(AuditLog(actorId = "admin-1", action = action))

        val line = breadcrumbFor(action)
        assertTrue("the refusal still has to surface", line.isNotEmpty())
        assertFalse("nothing was booked for a support session", line.contains("financial mutation"))
        assertFalse(line.contains("money movement"))
    }

    @Test
    fun `a refused payment record says the money is booked and only the row is missing`() = runBlocking {
        refuseInsert()

        repository().logPayment(
            Payment(id = "pay-wording-1", groupId = "g1", memberId = "m1", amount = Money("100.00"))
        )

        val line = breadcrumbFor("PAYMENT_RECORDED")
        assertTrue("the refusal still has to surface", line.isNotEmpty())
        assertTrue(line.contains("already booked"))
        assertTrue(line.contains("trail row"))
    }
}
