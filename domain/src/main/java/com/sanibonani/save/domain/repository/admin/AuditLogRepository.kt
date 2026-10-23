package com.sanibonani.save.domain.repository.admin

import com.sanibonani.save.domain.model.AuditLog
import com.sanibonani.save.domain.model.LedgerEntry
import com.sanibonani.save.domain.model.Payment

/**
 * Writes client-side audit records.
 *
 * These writes are best-effort by contract: they run after (and never inside) a financial
 * mutation, and a failure must not roll back or block that mutation. Implementations must
 * therefore surface every failure to the log rather than propagating it or discarding it.
 * The authoritative audit trail is the server-side one written by the money-moving RPCs.
 */
interface AuditLogRepository {
    suspend fun logLedgerEntry(entry: LedgerEntry)
    suspend fun logPayment(payment: Payment)
    suspend fun logEvent(auditLog: AuditLog)
}

