package com.sanibonani.save.data.repository.admin

import com.sanibonani.save.data.datasource.RemoteAuditDataSource
import com.sanibonani.save.data.repository.BaseRepository
import com.sanibonani.save.data.repository.mappers.DataPayloadFactory
import com.sanibonani.save.domain.logging.AppLogger
import com.sanibonani.save.domain.model.AuditLog
import com.sanibonani.save.domain.model.LedgerEntry
import com.sanibonani.save.domain.model.Payment
import com.sanibonani.save.domain.repository.admin.AuditLogRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuditLogRepositoryImpl @Inject constructor(
    private val remoteSource: RemoteAuditDataSource,
    private val payloadFactory: DataPayloadFactory
) : BaseRepository("AuditLogRepository"), AuditLogRepository {

    override suspend fun logLedgerEntry(entry: LedgerEntry) {
        val auditLog = AuditLog(
            actorId = SYSTEM_ACTOR,
            action = "LEDGER_ENTRY_CREATED",
            targetGroupId = entry.groupId,
            details = payloadFactory.createLedgerPayload(entry)
        )
        remoteSource.insertAuditLog(auditLog).logFailure("LEDGER_ENTRY_CREATED")
    }

    override suspend fun logPayment(payment: Payment) {
        val auditLog = AuditLog(
            actorId = SYSTEM_ACTOR,
            action = "PAYMENT_RECORDED",
            targetGroupId = payment.groupId,
            targetMemberId = payment.memberId,
            details = payloadFactory.createPaymentPayload(payment)
        )
        remoteSource.insertAuditLog(auditLog).logFailure("PAYMENT_RECORDED")
    }

    override suspend fun logEvent(auditLog: AuditLog) {
        // `actor_id` is the answer to "who did this". A blank one makes a recorded action
        // unattributable, so an automated write is stamped as the system instead.
        val stamped = if (auditLog.actorId.isBlank()) auditLog.copy(actorId = SYSTEM_ACTOR) else auditLog
        remoteSource.insertAuditLog(stamped).logFailure(stamped.action)
    }

    /** Best-effort write: never throw, but never disappear either. */
    private fun Result<Unit>.logFailure(action: String) {
        onFailure { e ->
            AppLogger.w("AuditLogRepository", "Audit write '$action' failed; the financial mutation it describes already succeeded. ${e.message}")
        }
    }

    private companion object {
        private const val SYSTEM_ACTOR = "SYSTEM"
    }
}

