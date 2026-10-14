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
import com.sanibonani.save.domain.repository.group.GroupRepository
import com.sanibonani.save.domain.repository.member.MemberRepository
import com.sanibonani.save.domain.service.PayoutGuard
import dagger.Lazy
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.*
import javax.inject.Inject
import com.sanibonani.save.domain.utils.safeRunCatching

class PayoutRepositoryImpl @Inject constructor(
    private val remoteSource: RemotePayoutDataSource,
    private val localSource: LocalPayoutDataSource,
    private val groupRepo: Lazy<GroupRepository>,
    private val memberRepo: Lazy<MemberRepository>,
    private val payoutGuard: Lazy<PayoutGuard>,
    private val auditLogRepo: AuditLogRepository,
    private val json: Json,
    private val pendingOpDao: com.sanibonani.save.data.local.PendingOperationDao
) : BaseRepository("PayoutRepository"), PayoutRepository {

    override suspend fun requestPayout(payout: PayoutRequest): Result<String> =
        safeApiCall(
            operationType = "payout",
            groupId = payout.groupId,
            payload = json.encodeToString(payout),
            dao = pendingOpDao
        ) {
            val entityId = payout.memberId ?: payout.groupId
            AppLogger.d(tag, "Requesting payout for entity: $entityId, amount: ${payout.amount.format()}")

            // 1. Check local outbox for duplicate "payout" operations
            val outbox = pendingOpDao.getAll()
            val isQueued = outbox.any { op ->
                op.type == "payout" && op.payload.contains(entityId)
            }
            if (isQueued) {
                throw DuplicatePayoutRequestException(entityId, isLocal = true)
            }

            // 2. Check remote pending payouts
            val pending = remoteSource.getPendingPayouts().getOrThrow()
            val isDuplicate = pending.any {
                it.groupId == payout.groupId &&
                it.memberId == payout.memberId &&
                it.amount == payout.amount &&
                it.type == payout.type
            }
            if (isDuplicate) {
                throw DuplicatePayoutRequestException(entityId, isLocal = false)
            }

            val saved = remoteSource.requestPayout(payout).getOrThrow()
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

    override suspend fun validatePayoutEligibility(
        groupId: String,
        memberId: String?,
        amount: Money,
        type: PayoutType
    ): Result<Unit> = safeRunCatching {
        val group = groupRepo.get().getGroupById(groupId).getOrThrow()
        val member = memberId?.let { memberRepo.get().getMemberById(it).getOrThrow() }
        payoutGuard.get().validateRequest(group, member, amount, type).getOrThrow()
    }

    override suspend fun updatePayoutStatus(
        payoutId: String,
        status: PayoutStatus,
        payoutReference: String?,
        metadata: JsonObject?
    ): Result<Unit> = safeApiCall(
        operationType = "payout_status_update",
        groupId = null, // Will be fetched if needed, but not critical for queuing
        payload = json.encodeToString(buildJsonObject {
            put("payout_id", payoutId)
            put("status", status.name)
            payoutReference?.let { put("reference", it) }
            metadata?.let { put("metadata", it) }
        }),
        dao = pendingOpDao
    ) {
        remoteSource.updatePayoutStatus(payoutId, status, payoutReference, metadata = metadata).getOrThrow()
        val updated = remoteSource.getPayoutById(payoutId).getOrThrow()
        localSource.savePayout(updated)

        auditLogRepo.logEvent(
            AuditLog(
                actorId = updated.processedBy ?: updated.groupId,
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

    override suspend fun completePayoutAtomic(payoutId: String, adminId: String, payoutReference: String?): Result<Unit> = safeRunCatching {
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

        groupRepo.get().getGroupById(updated.groupId)
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

    override suspend fun verifyPayoutDocument(payoutId: String, status: DocumentStatus): Result<Unit> = safeRunCatching {
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

    override suspend fun processRoscaPayout(groupId: String, memberId: String, totalPot: Money, loanDeductions: JsonArray, payoutAmount: Money, payoutData: JsonObject): Result<String> =
        remoteSource.processRoscaPayout(groupId, memberId, totalPot, loanDeductions, payoutAmount, payoutData)

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
