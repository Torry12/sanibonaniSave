package com.sanibonani.save.data.repository.disbursement
import com.sanibonani.save.data.datasource.LocalPayoutDataSource
import com.sanibonani.save.data.datasource.RemotePayoutDataSource
import com.sanibonani.save.data.repository.BaseRepository
import com.sanibonani.save.domain.event.EventBus
import com.sanibonani.save.domain.event.PaymentProcessedEvent
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.logging.AppLogger
import com.sanibonani.save.domain.repository.admin.AuditLogRepository
import com.sanibonani.save.domain.repository.disbursement.PayoutRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.*
import javax.inject.Inject
import com.sanibonani.save.domain.utils.safeRunCatching

class PayoutRepositoryImpl @Inject constructor(
    private val remoteSource: RemotePayoutDataSource,
    private val localSource: LocalPayoutDataSource,
    private val auditLogRepo: AuditLogRepository,
    private val json: Json,
    private val pendingOpDao: com.sanibonani.save.data.local.PendingOperationDao
) : BaseRepository("PayoutRepository"), PayoutRepository {

    override suspend fun requestPayout(payout: PayoutRequest, idempotencyKey: String?): Result<String> {
        val stablePayout = payout.copy(
            id = payout.id ?: idempotencyKey ?: java.util.UUID.randomUUID().toString()
        )
        return safeApiCall(
            operationType = "payout",
            groupId = stablePayout.groupId,
            payload = json.encodeToString(stablePayout),
            dao = pendingOpDao,
            idempotencyKey = idempotencyKey
        ) {
            val entityId = stablePayout.memberId ?: stablePayout.groupId
            AppLogger.d(tag, "Requesting payout for entity: $entityId, amount: ${stablePayout.amount.format()}")

            if (idempotencyKey == null) {
                val outbox = pendingOpDao.getAll()
                val isQueued = outbox.any { op ->
                    op.type == "payout" && safeRunCatching {
                        json.decodeFromString<PayoutRequest>(op.payload).let { queued ->
                            queued.groupId == stablePayout.groupId &&
                                queued.memberId == stablePayout.memberId &&
                                queued.amount == stablePayout.amount &&
                                queued.type == stablePayout.type
                        }
                    }.getOrDefault(false)
                }
                if (isQueued) throw DuplicatePayoutRequestException(entityId, isLocal = true)

                val pending = remoteSource.getPendingPayouts().getOrThrow()
                val isDuplicate = pending.any {
                    it.groupId == stablePayout.groupId &&
                        it.memberId == stablePayout.memberId &&
                        it.amount == stablePayout.amount &&
                        it.type == stablePayout.type
                }
                if (isDuplicate) throw DuplicatePayoutRequestException(entityId, isLocal = false)
            }

            val saved = if (idempotencyKey != null) {
                remoteSource.getPayoutById(stablePayout.id!!).getOrElse {
                    remoteSource.requestPayout(stablePayout).getOrThrow()
                }
            } else {
                remoteSource.requestPayout(stablePayout).getOrThrow()
            }
            localSource.savePayout(saved)
            auditLogRepo.logEvent(
                AuditLog(
                    actorId = saved.memberId ?: saved.groupId,
                    targetGroupId = saved.groupId,
                    targetMemberId = saved.memberId,
                    action = "PAYOUT_REQUESTED",
                    details = json.encodeToJsonElement(saved)
                )
            )
            saved.id ?: ""
        }
    }

    override suspend fun getPendingPayouts(): Result<List<PayoutRequest>> = safeRunCatching {
        val remote = remoteSource.getPendingPayouts().getOrThrow()
        localSource.savePayouts(remote)
        remote
    }

    override fun observePendingPayouts(): Flow<Result<List<PayoutRequest>>> = observeAndSync(
        dbFlow = localSource.observePendingPayouts(),
        mapper = { it },
        toEntity = { it },
        networkFetch = { remoteSource.getPendingPayouts().getOrThrow() },
        cacheSync = { localSource.savePayouts(it) }
    )

    override suspend fun getPayoutById(payoutId: String): Result<PayoutRequest> = safeRunCatching {
        val remote = remoteSource.getPayoutById(payoutId).getOrThrow()
        localSource.savePayout(remote)
        remote
    }

    override suspend fun updatePayoutStatus(
        payoutId: String,
        status: PayoutStatus,
        payoutReference: String?,
        metadata: JsonObject?,
        actorId: String?
    ): Result<Unit> = safeApiCall(
        operationType = "payout_status_update",
        groupId = null, // Will be fetched if needed, but not critical for queuing
        payload = json.encodeToString(buildJsonObject {
            put("payout_id", payoutId)
            put("status", status.name)
            payoutReference?.let { put("reference", it) }
            metadata?.let { put("metadata", it) }
            actorId?.let { put("actor_id", it) }
        }),
        dao = pendingOpDao
    ) {
        remoteSource.updatePayoutStatus(payoutId, status, payoutReference, processedBy = actorId, metadata = metadata).getOrThrow()
        val updated = remoteSource.getPayoutById(payoutId).getOrThrow()
        localSource.savePayout(updated)

        auditLogRepo.logEvent(
            AuditLog(
                // The admin who asked, not whatever the server row ended up carrying: an audit
                // line must name the actor even if the response was stale or stripped.
                actorId = actorId ?: updated.processedBy ?: updated.groupId,
                targetGroupId = updated.groupId,
                targetMemberId = updated.memberId,
                action = "PAYOUT_STATUS_CHANGED",
                details = buildJsonObject {
                    put("payout_id", payoutId)
                    put("old_status", "UNKNOWN") // Ideally fetch current first
                    put("new_status", status.name)
                    payoutReference?.let { put("reference", it) }
                    metadata?.let { put("metadata", it) }
                }
            )
        )
    }

    override suspend fun completePayoutAtomic(
        payoutId: String,
        adminId: String,
        payoutReference: String?
    ): Result<Unit> = authoritativeCall("completePayoutAtomic") {
        remoteSource.completePayoutAtomic(payoutId, adminId, payoutReference).getOrThrow()
        val updated = remoteSource.getPayoutById(payoutId).getOrThrow()
        localSource.savePayout(updated)

        auditLogRepo.logEvent(
            AuditLog(
                actorId = adminId,
                targetGroupId = updated.groupId,
                targetMemberId = updated.memberId,
                action = "PAYOUT_COMPLETED",
                details = buildJsonObject {
                    put("payout_id", payoutId)
                    put("reference", payoutReference ?: "")
                    put("amount", updated.amount.amount.toPlainString())
                    updated.metadata?.let { put("metadata", it) }
                }
            )
        )

        EventBus.emit(PaymentProcessedEvent(Payment(
            memberId = updated.processedBy ?: "",
            groupId = updated.groupId,
            amount = updated.amount,
            paymentType = PaymentType.CLAIM,
            paymentMethod = PaymentMethod.BANK,
            transactionId = updated.payoutReference ?: updated.id,
            status = PaymentStatus.COMPLETED,
            processedAt = updated.processedAt
        )))
    }

    override suspend fun verifyPayoutDocument(payoutId: String, status: DocumentStatus): Result<Unit> =
        authoritativeCall("verifyPayoutDocument") {
            remoteSource.updatePayoutDocumentStatus(payoutId, status).getOrThrow()
            val updated = remoteSource.getPayoutById(payoutId).getOrThrow()
            localSource.savePayout(updated)
        }

    override fun observePayouts(groupId: String): Flow<Result<List<PayoutRequest>>> = observeAndSync(
        dbFlow = localSource.observePayouts(groupId),
        mapper = { it },
        toEntity = { it },
        networkFetch = { remoteSource.getPayouts(groupId).getOrThrow() },
        cacheSync = { localSource.syncPayouts(groupId, it) }
    )

    override fun observePayoutsForMember(memberId: String): Flow<Result<List<PayoutRequest>>> = observeAndSync(
        dbFlow = localSource.observePayoutsForMember(memberId),
        mapper = { it },
        toEntity = { it },
        networkFetch = { remoteSource.getPayoutsForMember(memberId).getOrThrow() },
        cacheSync = { localSource.savePayouts(it) }
    )

    override suspend fun processRoscaPayout(
        groupId: String,
        memberId: String,
        totalPot: Money,
        loanDeductions: JsonArray,
        payoutAmount: Money,
        payoutData: JsonObject,
        period: String
    ): Result<String?> = authoritativeCall("processRoscaPayout") {
        remoteSource.processRoscaPayout(
            groupId,
            memberId,
            totalPot,
            loanDeductions,
            payoutAmount,
            payoutData,
            period
        ).getOrThrow()
    }

    companion object {
        fun buildCompletePayoutRpcParams(
            payoutId: String,
            adminId: String,
            fallbackAdminId: String?,
            payoutReference: String?
        ): JsonObject = buildJsonObject {
            put("p_payout_id", payoutId)

            val resolvedAdminId = when {
                adminId.isNotBlank() && adminId != "PLATFORM_ADMIN" -> adminId
                !fallbackAdminId.isNullOrBlank() -> fallbackAdminId
                else -> "00000000-0000-0000-0000-000000000000"
            }
            put("p_admin_id", resolvedAdminId)

            payoutReference?.let { put("p_payout_reference", it) }
        }
    }
}
