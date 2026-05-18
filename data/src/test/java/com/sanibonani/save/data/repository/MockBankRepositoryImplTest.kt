package com.sanibonani.save.data.repository

import com.sanibonani.save.domain.model.MockBankDirection
import com.sanibonani.save.domain.model.PaymentStatus
import com.sanibonani.save.domain.model.PaymentType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MockBankRepositoryImplTest {
    private val repository = MockBankRepositoryImpl()

    @Test
    fun `createTransaction stores pending bank transaction`() = runTest {
        val transaction = repository.createTransaction(
            amount = 250.0,
            type = PaymentType.CONTRIBUTION,
            groupId = "group-1",
            memberId = "member-1",
            direction = MockBankDirection.INBOUND
        ).getOrThrow()

        val transactions = repository.observeTransactions().first()

        assertEquals(PaymentStatus.PENDING, transaction.status)
        assertTrue(transaction.reference.startsWith("MOCK-BANK-"))
        assertEquals(listOf(transaction), transactions)
    }

    @Test
    fun `updateTransactionStatus updates tracked transaction`() = runTest {
        val transaction = repository.createTransaction(
            amount = 500.0,
            type = PaymentType.LOAN_DISBURSEMENT,
            groupId = "group-2",
            memberId = "member-2",
            direction = MockBankDirection.OUTBOUND
        ).getOrThrow()

        val updated = repository.updateTransactionStatus(
            transactionId = transaction.id,
            status = PaymentStatus.COMPLETED
        ).getOrThrow()

        assertEquals(PaymentStatus.COMPLETED, updated.status)
        assertEquals(updated, repository.getTransaction(transaction.id).getOrThrow())
    }

    @Test
    fun `createTransaction rejects non-positive amount`() = runTest {
        val result = repository.createTransaction(
            amount = 0.0,
            type = PaymentType.CONTRIBUTION,
            groupId = "group-1",
            memberId = null,
            direction = MockBankDirection.INBOUND
        )

        assertTrue(result.isFailure)
    }
}
