package com.sanibonani.save.data.repository.system

import com.sanibonani.save.data.datasource.LocalPayoutDataSource
import com.sanibonani.save.data.datasource.RemotePayoutDataSource
import com.sanibonani.save.domain.model.AuditLog
import com.sanibonani.save.domain.model.Contribution
import com.sanibonani.save.domain.model.LoanRepayment
import com.sanibonani.save.domain.model.PayoutRequest
import com.sanibonani.save.domain.model.PayoutStatus
import com.sanibonani.save.domain.model.PendingOperation
import com.sanibonani.save.domain.model.Money
import com.sanibonani.save.domain.model.BeneficiaryPayoutClaim
import com.sanibonani.save.domain.model.canTransitionTo
import com.sanibonani.save.domain.repository.admin.AuditLogRepository
import com.sanibonani.save.domain.repository.member.BeneficiaryClaimRepository
import com.sanibonani.save.domain.repository.equity.ContributionRepository
import com.sanibonani.save.domain.repository.credit.LoanRepository
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import javax.inject.Inject
import com.sanibonani.save.domain.utils.safeRunCatching

// Failures here stop the whole outbox queue, so name the missing key instead of throwing a bare NPE.
private fun Map<String, String?>.requireValue(key: String): String =
    this[key] ?: error("Outbox payload is missing '$key'")

class ContributionOutboxHandler @Inject constructor(
    private val repository: ContributionRepository,
    private val json: Json
) : OutboxOperationHandler {
    override suspend fun handle(operation: PendingOperation): Result<Unit> = safeRunCatching {
        val contribution = json.decodeFromString<Contribution>(operation.payload)
        repository.recordContribution(contribution, idempotencyKey = operation.id).getOrThrow()
    }
}

class LoanRepaymentOutboxHandler @Inject constructor(
    private val repository: LoanRepository,
    private val json: Json
) : OutboxOperationHandler {
    override suspend fun handle(operation: PendingOperation): Result<Unit> = safeRunCatching {
        val repayment = json.decodeFromString<LoanRepayment>(operation.payload)
        repository.recordRepayment(repayment, idempotencyKey = operation.id).getOrThrow()
    }
}

class PayoutRequestOutboxHandler @Inject constructor(
    private val repository: com.sanibonani.save.domain.repository.disbursement.PayoutRepository,
    private val json: Json
) : OutboxOperationHandler {
    override suspend fun handle(operation: PendingOperation): Result<Unit> = safeRunCatching {
        val payout = json.decodeFromString<PayoutRequest>(operation.payload)
        repository.requestPayout(payout, idempotencyKey = operation.id).getOrThrow()
    }
}

class DisbursementOutboxHandler @Inject constructor(
    private val repository: com.sanibonani.save.domain.repository.group.GroupRepository,
    private val json: Json
) : OutboxOperationHandler {
    override suspend fun handle(operation: PendingOperation): Result<Unit> = safeRunCatching {
        val data = json.decodeFromString<Map<String, String?>>(operation.payload)
        repository.recordDisbursement(
            groupId = data.requireValue("groupId"),
            amount = Money(data.requireValue("amount")),
            description = data.requireValue("description"),
            category = data.requireValue("category"),
            transactionId = data["transactionId"]
        ).getOrThrow()
    }
}

class BalanceAdjustmentOutboxHandler @Inject constructor(
    private val repository: com.sanibonani.save.domain.repository.group.GroupRepository,
    private val json: Json
) : OutboxOperationHandler {
    override suspend fun handle(operation: PendingOperation): Result<Unit> = safeRunCatching {
        val data = json.decodeFromString<Map<String, String>>(operation.payload)
        repository.incrementGroupBalance(
            groupId = data.requireValue("groupId"),
            amount = Money(data.requireValue("amount")),
            transactionId = operation.id
        ).getOrThrow()
    }
}

class BeneficiaryClaimOutboxHandler @Inject constructor(
    private val repository: BeneficiaryClaimRepository,
    private val json: Json
) : OutboxOperationHandler {
    override suspend fun handle(operation: PendingOperation): Result<Unit> = safeRunCatching {
        val claim = json.decodeFromString<BeneficiaryPayoutClaim>(operation.payload)
        repository.submitClaim(claim, idempotencyKey = operation.id).getOrThrow()
    }
}

/**
 * Replays an admin payout transition that was queued while the device was offline.
 *
 * This deliberately calls the data source rather than PayoutRepository: the repository wraps the
 * same write in the outbox again, so replaying through it would append a fresh queue row every
 * time the server stayed unreachable.
 */
class PayoutStatusUpdateOutboxHandler @Inject constructor(
    private val remoteSource: RemotePayoutDataSource,
    private val localSource: LocalPayoutDataSource,
    private val auditLogRepo: AuditLogRepository,
    private val json: Json
) : OutboxOperationHandler {
    override suspend fun handle(operation: PendingOperation): Result<Unit> = safeRunCatching {
        val request = json.parseToJsonElement(operation.payload).jsonObject
        val payoutId = request["payout_id"]?.jsonPrimitive?.contentOrNull
            ?: error("Outbox payload is missing 'payout_id'")
        val statusName = request["status"]?.jsonPrimitive?.contentOrNull
            ?: error("Outbox payload is missing 'status'")
        val status = PayoutStatus.entries.firstOrNull { it.name == statusName }
            ?: error("Outbox payload carries an unknown payout status '$statusName'")
        val reference = request["reference"]?.jsonPrimitive?.contentOrNull
        val metadata = request["metadata"] as? JsonObject
        val actorId = request["actor_id"]?.jsonPrimitive?.contentOrNull

        // The payout may have moved while this decision sat in the outbox. Replaying it against a
        // settlement that already happened would reopen settled money, so the lane ends here.
        val current = remoteSource.getPayoutById(payoutId).getOrThrow()
        if (current.status == status) return@safeRunCatching
        if (!current.status.canTransitionTo(status)) {
            auditLogRepo.logEvent(
                AuditLog(
                    actorId = actorId ?: current.processedBy ?: current.groupId,
                    targetGroupId = current.groupId,
                    targetMemberId = current.memberId,
                    action = "PAYOUT_STATUS_SUPERSEDED",
                    details = buildJsonObject {
                        put("payout_id", payoutId)
                        put("from_status", current.status.name)
                        put("requested_status", status.name)
                    }
                )
            )
            return@safeRunCatching
        }

        remoteSource.updatePayoutStatus(payoutId, status, reference, processedBy = actorId, metadata = metadata).getOrThrow()
        val updated = remoteSource.getPayoutById(payoutId).getOrThrow()
        localSource.savePayout(updated)

        // The transition was audited nowhere else: the queued attempt failed before its audit line.
        auditLogRepo.logEvent(
            AuditLog(
                actorId = updated.processedBy ?: updated.groupId,
                targetGroupId = updated.groupId,
                targetMemberId = updated.memberId,
                action = "PAYOUT_STATUS_CHANGED",
                details = json.parseToJsonElement(operation.payload).jsonObject
            )
        )
    }
}
