package com.sanibonani.save.data.repository

import com.sanibonani.save.domain.model.AuditLog
import com.sanibonani.save.domain.model.LedgerEntry
import com.sanibonani.save.domain.model.Payment
import com.sanibonani.save.domain.repository.AuditLogRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuditLogRepositoryImpl @Inject constructor(
    private val supabase: SupabaseClient
) : AuditLogRepository {

    override suspend fun logLedgerEntry(entry: LedgerEntry) {
        val auditLog = AuditLog(
            action = "LEDGER_ENTRY_CREATED",
            targetGroupId = entry.groupId,
            details = mapOf(
                "amount" to entry.amount.toString(),
                "balance_after" to entry.balanceAfter.toString(),
                "category" to entry.category,
                "description" to entry.description,
                "transaction_id" to (entry.transactionId ?: "")
            )
        )
        runCatching {
            supabase.postgrest["audit_logs"].insert(auditLog)
        }
    }

    override suspend fun logPayment(payment: Payment) {
        val auditLog = AuditLog(
            action = "PAYMENT_RECORDED",
            targetGroupId = payment.groupId,
            targetMemberId = payment.memberId,
            details = mapOf(
                "amount" to payment.amount.toString(),
                "payment_type" to payment.paymentType.name,
                "payment_method" to payment.paymentMethod.name,
                "transaction_id" to (payment.transactionId ?: ""),
                "status" to payment.status.name
            )
        )
        runCatching {
            supabase.postgrest["audit_logs"].insert(auditLog)
        }
    }
}
