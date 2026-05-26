package com.sanibonani.save.data.repository

import com.sanibonani.save.domain.model.LedgerEntry
import com.sanibonani.save.domain.model.Payment
import com.sanibonani.save.domain.repository.AuditLogRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuditLogRepositoryImpl @Inject constructor(
    private val supabaseClient: SupabaseClient
) : AuditLogRepository {
    override suspend fun logLedgerEntry(entry: LedgerEntry) {
        // Implement audit logging for ledger entries
        // supabaseClient.postgrest["audit_logs"].insert(entry)
    }

    override suspend fun logPayment(payment: Payment) {
        // Implement audit logging for payments
        // supabaseClient.postgrest["audit_logs"].insert(payment)
    }
}
