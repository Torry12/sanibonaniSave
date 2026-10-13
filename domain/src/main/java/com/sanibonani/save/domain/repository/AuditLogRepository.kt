package com.sanibonani.save.domain.repository

import com.sanibonani.save.domain.model.LedgerEntry
import com.sanibonani.save.domain.model.Payment

/**
 * Repository for writing audit logs.
 */
interface AuditLogRepository {
    suspend fun logLedgerEntry(entry: LedgerEntry)
    suspend fun logPayment(payment: Payment)
}

