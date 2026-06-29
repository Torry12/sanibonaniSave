package com.sanibonani.save.domain.event

import com.sanibonani.save.domain.model.LedgerEntry
import com.sanibonani.save.domain.model.Payment
import com.sanibonani.save.domain.repository.AuditLogRepository
import com.sanibonani.save.domain.repository.NotificationRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test

class EventHandlerTest {
    @Test
    fun `notification handler sends notification on PaymentProcessedEvent`() = runBlocking {
        val notificationRepo = mockk<NotificationRepository>()
        coEvery { notificationRepo.sendNotification(any()) } returns Result.success(Unit)

        val handler = NotificationEventHandler(notificationRepo)
        val payment = Payment(
            memberId = "m1",
            groupId = "g1",
            amount = 100.0,
            paymentType = com.sanibonani.save.domain.model.PaymentType.CONTRIBUTION,
            paymentMethod = com.sanibonani.save.domain.model.PaymentMethod.BANK,
            transactionId = "tx1",
            status = com.sanibonani.save.domain.model.PaymentStatus.COMPLETED,
            processedAt = "2026-05-22T12:00:00Z"
        )
        handler.handle(PaymentProcessedEvent(payment))
        coVerify(exactly = 1) { notificationRepo.sendNotification(any()) }
    }

    @Test
    fun `audit log handler logs ledger entry`() = runBlocking {
        val auditRepo = mockk<AuditLogRepository>()
        coEvery { auditRepo.logLedgerEntry(any()) } returns Unit

        val handler = AuditLogEventHandler(auditRepo)
        val entry = LedgerEntry(
            id = "l1",
            groupId = "g1",
            transactionId = "tx1",
            amount = 50.0,
            balanceAfter = 150.0,
            description = "Test entry",
            category = "test",
            createdAt = "2026-05-22T12:00:00Z"
        )
        handler.handle(LedgerEntryCreatedEvent(entry))
        coVerify(exactly = 1) { auditRepo.logLedgerEntry(any()) }
    }
}
