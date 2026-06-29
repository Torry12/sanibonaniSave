package com.sanibonani.save.domain.event

import com.sanibonani.save.domain.repository.AuditLogRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles domain events by recording them in the audit log.
 */
@Singleton
class AuditLogEventHandler @Inject constructor(
    private val auditRepo: AuditLogRepository
) : EventHandler<DomainEvent> {
    override suspend fun handle(event: DomainEvent) {
        when (event) {
            is LedgerEntryCreatedEvent -> {
                auditRepo.logLedgerEntry(event.entry)
            }
            is PaymentProcessedEvent -> {
                auditRepo.logPayment(event.payment)
            }
            // Add other event types as needed
        }
    }
}
