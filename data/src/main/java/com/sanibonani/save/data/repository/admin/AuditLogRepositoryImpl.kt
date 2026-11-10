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
            val consequence = if (actionDescribesMoney(action))
                "the money movement it describes is already booked"
            else
                "the action it describes has already run"
            AppLogger.w("AuditLogRepository", "Audit write '$action' failed; $consequence, only the trail row is missing. ${e.message}")
        }
    }

    /**
     * Whether a refused trail row hides money or only accountability. A new money-moving action has to
     * join [MONEY_ACTION_PREFIXES] or its refusal reads as the lesser of the two.
     */
    internal fun actionDescribesMoney(action: String): Boolean =
        MONEY_ACTION_PREFIXES.any { action.startsWith(it) }

    private companion object {
        private const val SYSTEM_ACTOR = "SYSTEM"
        private val MONEY_ACTION_PREFIXES = listOf(
            "LEDGER", "PAYMENT", "PAYOUT", "ROSCA", "LOAN", "AUTO_REPAIR"
        )
    }
}

