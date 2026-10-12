package com.sanibonani.save.data.repository.admin

import com.sanibonani.save.data.datasource.RemoteAuditDataSource
import com.sanibonani.save.data.repository.BaseRepository
import com.sanibonani.save.domain.model.AuditLog
import com.sanibonani.save.domain.model.LedgerEntry
import com.sanibonani.save.domain.model.Payment
import com.sanibonani.save.domain.repository.admin.AuditLogRepository
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuditLogRepositoryImpl @Inject constructor(
    private val remoteSource: RemoteAuditDataSource,
    private val payloadFactory: com.sanibonani.save.data.repository.mappers.DataPayloadFactory
) : BaseRepository("AuditLogRepository"), AuditLogRepository {

    override suspend fun logLedgerEntry(entry: LedgerEntry) {
        val auditLog = AuditLog(
            action = "LEDGER_ENTRY_CREATED",
            targetGroupId = entry.groupId,
            details = payloadFactory.createLedgerPayload(entry)
        )
        remoteSource.insertAuditLog(auditLog)
    }

    override suspend fun logPayment(payment: Payment) {
        // NOTE: Standard audit log table expects JsonElement details.
        // We'll use the recordPayment stableUuid pattern or direct insert.
        val auditLog = AuditLog(
            action = "PAYMENT_RECORDED",
            targetGroupId = payment.groupId,
            targetMemberId = payment.memberId,
            details = buildJsonObject {
                put("amount", payment.amount.amount.toPlainString())
                put("payment_type", payment.paymentType.name)
                put("payment_method", payment.paymentMethod.name)
                put("transaction_id", payment.transactionId ?: "")
                put("status", payment.status.name)
            }
        )
        remoteSource.insertAuditLog(auditLog)
    }

    override suspend fun logEvent(auditLog: AuditLog) {
        remoteSource.insertAuditLog(auditLog)
    }
}

