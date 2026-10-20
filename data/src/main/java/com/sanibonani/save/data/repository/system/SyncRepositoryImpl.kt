package com.sanibonani.save.data.repository.system

import com.sanibonani.save.data.repository.BaseRepository
import com.sanibonani.save.data.local.*
import com.sanibonani.save.data.local.toEntity
import com.sanibonani.save.data.local.toLedgerEntity
import com.sanibonani.save.domain.logging.AppLogger
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.utils.toUserMessage
import com.sanibonani.save.domain.repository.auth.*
import com.sanibonani.save.domain.repository.group.*
import com.sanibonani.save.domain.repository.member.*
import com.sanibonani.save.domain.repository.system.*
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import com.sanibonani.save.domain.utils.safeRunCatching

class SyncRepositoryImpl @Inject constructor(
    private val supabase: SupabaseClient,
    private val supabaseRepo: SupabaseRepository,
    private val memberRepo: MemberRepository,
    private val groupRepo: GroupRepository,
    private val notificationRepo: NotificationRepository,
    private val db: SanibonaniDatabase,
    private val contributionHandler: ContributionOutboxHandler,
    private val loanRepaymentHandler: LoanRepaymentOutboxHandler,
    private val payoutHandler: PayoutRequestOutboxHandler,
    private val disbursementHandler: DisbursementOutboxHandler,
    private val balanceAdjustmentHandler: BalanceAdjustmentOutboxHandler,
    private val beneficiaryClaimHandler: BeneficiaryClaimOutboxHandler,
    private val payoutStatusUpdateHandler: PayoutStatusUpdateOutboxHandler
) : BaseRepository("SyncRepository"), SyncRepository {

    private val handlers by lazy {
        mapOf(
            "contribution" to contributionHandler,
            "loan_repayment" to loanRepaymentHandler,
            "payout" to payoutHandler,
            "payout_status_update" to payoutStatusUpdateHandler,
            "disbursement" to disbursementHandler,
            "balance_adjustment" to balanceAdjustmentHandler,
            "beneficiary_claim" to beneficiaryClaimHandler
        )
    }

    private val _syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    override val syncStatus = _syncStatus.asStateFlow()

    private val globalSyncMutex = Mutex()
    private val globalSyncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeSync: Deferred<Result<Unit>>? = null

    override val outboxCount: kotlinx.coroutines.flow.Flow<Int> = db.pendingOperationDao().observeCount()

    /**
     * Single-flight global sync. Concurrent triggers (cold start, pull-to-refresh, Support Mode entry,
     * SyncTriggerService) join the in-flight sync and share its Result rather than overlapping.
     * The Mutex only guards join-or-start; the sync itself runs on [globalSyncScope] so that one
     * caller cancelling does not tear down the sync other callers are awaiting.
     */
    override suspend fun syncAllData(): Result<Unit> {
        val flight = globalSyncMutex.withLock {
            activeSync?.takeIf { it.isActive }
                ?: globalSyncScope.async { runGlobalSync() }.also { activeSync = it }
        }
        return flight.await()
    }

    private suspend fun runGlobalSync(): Result<Unit> = safeRunCatching("$tag.fullSync") {
            kotlinx.coroutines.withTimeout(120_000) {
                val userId = supabaseRepo.currentUserId ?: run {
                    val message = "Please sign in to sync your data."
                    _syncStatus.value = SyncStatus.Error(message)
                    throw IllegalStateException(message)
                }

                val userRole = supabaseRepo.getUserRole()

                _syncStatus.value = SyncStatus.Progress("Identifying memberships...", 0.05f)
                val memberships = memberRepo.getMemberships(userId).getOrElse {
                    AppLogger.w(tag, "Network fetch failed for memberships, falling back to cache")
                    db.memberDao().getAllMemberships(userId).map { it.toModel() }
                }
                val adminGroups = groupRepo.getGroupsByAdmin(userId).getOrElse {
                    AppLogger.w(tag, "Network fetch failed for admin groups, falling back to cache")
                    db.groupDao().getGroupsByAdminSync(userId).map { it.toModel() }
                }

                val groupIds = buildSet {
                    memberships.mapTo(this) { it.groupId }
                    adminGroups.mapNotNullTo(this) { it.id }
                }.filter { it.isNotBlank() }

                if (groupIds.isNotEmpty()) {
                    _syncStatus.value = SyncStatus.Progress("Hydrating ${groupIds.size} group(s)...", 0.10f)

                    coroutineScope {
                        val totalGroups = groupIds.size
                        val completedGroups = AtomicInteger(0)
                        val semaphore = Semaphore(3)

                        groupIds.map { groupId ->
                            async {
                                semaphore.acquire()
                                try {
                                    syncGroupDataInternal(groupId, userRole)
                                } finally {
                                    semaphore.release()
                                }
                                val completed = completedGroups.incrementAndGet()
                                val progress = 0.10f + (completed.toFloat() / totalGroups) * 0.85f
                                _syncStatus.value = SyncStatus.Progress(
                                    "Synced $completed of $totalGroups groups",
                                    progress
                                )
                            }
                        }.awaitAll()
                    }
                } else {
                    AppLogger.d(tag, "No groups found for hydration.")
                }

                _syncStatus.value = SyncStatus.Progress("Processing offline queue...", 0.95f)
                processOutbox().getOrNull()

                _syncStatus.value = SyncStatus.Completed
            }
    }.onFailure { e ->
        if (e is CancellationException && e !is TimeoutCancellationException) throw e
        val userMessage = e.toUserMessage()
        _syncStatus.value = SyncStatus.Error(userMessage)
    }

    private suspend fun syncGroupDataInternal(groupId: String, role: UserRole) = coroutineScope {
        if (role == UserRole.PLATFORM_ADMIN) {
            // Step 1: Core Group Metadata & Permissions
            val metadataJob = async { groupRepo.getGroupById(groupId) }
            val membersJob = async { memberRepo.syncGroupMembers(groupId) }
            awaitAll(metadataJob, membersJob)

            // Step 2: Parallel sync of all financial pillars for Platform Admin (Complete Hydration)
            val notifsJob = async { notificationRepo.syncNotifications(groupId) }
            val equityJob = async { syncRefresh(DomainPillar.EQUITY, groupId) }
            val creditJob = async { syncRefresh(DomainPillar.CREDIT, groupId) }
            val treasuryJob = async { syncRefresh(DomainPillar.TREASURY, groupId) }
            val disbursementJob = async { syncRefresh(DomainPillar.DISBURSEMENT, groupId) }
            val bankJob = async { syncRefresh(DomainPillar.BANK, groupId) }
            val dynamicsJob = async { syncRefresh(DomainPillar.DYNAMICS, groupId) }

            // Wait for all to complete
            awaitAll(notifsJob, equityJob, creditJob, treasuryJob, disbursementJob, bankJob, dynamicsJob)
        } else {
            // Tailored hydration per group admin or member to drastically speed up app launch.
            // Map and group details, as well as ledger/equity are kept in cache storage.
            // RealtimeSyncManager will handle incremental updates.
            // We only fetch high-priority immediate updates (like notifications and member status).
            val membersJob = async { memberRepo.syncGroupMembers(groupId) }
            val notifsJob = async { notificationRepo.syncNotifications(groupId) }

            val jobs = mutableListOf(membersJob, notifsJob)

            if (role == UserRole.GROUP_ADMIN) {
                // Group admins need to see pending payouts and bank issues immediately upon launch
                jobs.add(async { syncRefresh(DomainPillar.DISBURSEMENT, groupId) })
                jobs.add(async { syncRefresh(DomainPillar.BANK, groupId) })
            } else {
                // Members just need their personal credit info updated
                jobs.add(async { syncRefresh(DomainPillar.CREDIT, groupId) })
            }

            jobs.awaitAll()
        }
    }

    override suspend fun syncGroupData(groupId: String): Result<Unit> = safeRunCatching {
        AppLogger.i(tag, "Targeted refresh for group $groupId")
        val userRole = supabaseRepo.getUserRole()
        syncGroupDataInternal(groupId, userRole)
        Unit
    }

    override suspend fun syncRefresh(pillar: DomainPillar, groupId: String): Result<Unit> = safeRunCatching {
        AppLogger.d(tag, "Refreshing pillar ${pillar.name} for group $groupId")
        when (pillar) {
            DomainPillar.EQUITY -> {
                safeRunCatching("$tag.equityContributions") {
                    val data = supabase.postgrest["contributions"].select { filter { eq("group_id", groupId) } }.decodeList<Contribution>()
                    db.contributionDao().syncGroupContributions(groupId, data.map { it.toEntity() })
                }

                safeRunCatching("$tag.equityPayments") {
                    val payments = supabase.postgrest["payments"].select { filter { eq("group_id", groupId) } }.decodeList<Payment>()
                    db.paymentDao().syncPayments(groupId, payments.map { it.toEntity() })
                }
            }
            DomainPillar.GLOBAL -> memberRepo.syncGroupMembers(groupId).getOrNull()
            DomainPillar.CREDIT -> {
                safeRunCatching("$tag.loans") {
                    val data = supabase.postgrest["loans"].select { filter { eq("group_id", groupId) } }.decodeList<Loan>()
                    db.loanDao().syncGroupLoans(groupId, data.map { it.toEntity() })
                }
            }
            DomainPillar.DISBURSEMENT -> {
                safeRunCatching("$tag.payouts") {
                    val payouts = supabase.postgrest["payouts"].select { filter { eq("group_id", groupId) } }.decodeList<PayoutRequest>()
                    db.payoutDao().syncPayouts(groupId, payouts.map { it.toEntity() })
                }

                safeRunCatching("$tag.claims") {
                    val claims = safeRunCatching("$tag.beneficiaryClaims") {
                        supabase.postgrest["beneficiary_payout_claims"].select(columns = Columns.raw("*")) {
                            filter { eq("group_id", groupId) }
                        }.decodeList<BeneficiaryPayoutClaim>()
                    }.getOrElse {
                        supabase.postgrest["burial_claims"].select(columns = Columns.raw("*")) {
                            filter { eq("group_id", groupId) }
                        }.decodeList<BeneficiaryPayoutClaim>()
                    }
                    db.beneficiaryClaimDao().syncForGroup(groupId, claims.map { it.toEntity() })
                }
            }
            DomainPillar.TREASURY -> {
                safeRunCatching("$tag.treasuryLedger") {
                    val data = supabase.postgrest["group_ledger"].select(columns = Columns.raw("*")) {
                        filter { eq("group_id", groupId) }
                    }.decodeList<LedgerEntry>()
                    db.ledgerDao().syncLedger(groupId, data.map { it.toLedgerEntity() })
                }
            }
            DomainPillar.BANK -> {
                safeRunCatching("$tag.bankTransactions") {
                    // Sync Bank Connection
                    val connection = supabase.postgrest["bank_connections"].select(columns = Columns.raw("*")) {
                        filter { eq("group_id", groupId) }
                    }.decodeSingleOrNull<BankConnection>()
                    connection?.let {
                        db.bankTransactionDao().upsertConnection(it.toEntity())
                    }

                    // Sync Unreconciled Transactions
                    val transactions = supabase.postgrest["bank_transactions"].select(columns = Columns.raw("*")) {
                        filter {
                            eq("group_id", groupId)
                            eq("status", BankTransactionStatus.UNRECONCILED.name.lowercase())
                        }
                    }.decodeList<BankTransaction>()
                    db.bankTransactionDao().upsert(transactions.map { it.toEntity() })
                }
            }
            DomainPillar.DYNAMICS -> {
                safeRunCatching("$tag.memberBehaviorTracks") {
                    val tracks = supabase.postgrest["member_behavior_tracks"].select(columns = Columns.raw("*")) {
                        filter { eq("group_id", groupId) }
                    }.decodeList<MemberBehaviorTrack>()
                    db.memberBehaviorTrackDao().syncForGroup(groupId, tracks.map { it.toEntity() })
                }
            }
            else -> {}
        }
    }

    override suspend fun processOutbox(): Result<Unit> = safeRunCatching {
        val queued = db.pendingOperationDao().getAll()
        if (queued.isEmpty()) return@safeRunCatching

        val parked = queued.count { OutboxPolicy.isParked(it.retryCount) }
        if (parked > 0) {
            AppLogger.w(
                tag,
                "⚠️ $parked outbox operation(s) are parked after ${OutboxPolicy.MAX_PERMANENT_ATTEMPTS} " +
                    "rejected attempts. They stay on the device for investigation but no longer block the queue."
            )
        }

        AppLogger.i(tag, "📦 Processing outbox queue: ${queued.size} operations ($parked parked)")

        // Ordering only binds operations that touch the same ledger, so each group drains as its own
        // lane. A rejected operation halts its lane (preserving sequence) instead of wedging every
        // other member's queued money movement behind it.
        val lanes = queued.filterNot { OutboxPolicy.isParked(it.retryCount) }.groupBy { it.groupId }

        for ((groupId, lane) in lanes) {
            for (op in lane) {
                try {
                    AppLogger.d(tag, "Attempting to process ${op.type} for group ${op.groupId}")
                    val handler = handlers[op.type.lowercase()]
                        ?: throw IllegalArgumentException("No handler for operation type: ${op.type}")

                    handler.handle(op.toModel()).getOrThrow()

                    // If successful, remove from queue
                    db.pendingOperationDao().delete(op)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    val permanent = OutboxPolicy.isPermanentFailure(e)
                    val reason = e.toUserMessage()
                    // A transient failure must not consume an attempt: an outage that outlasts the
                    // ceiling would otherwise dead-letter money that was never rejected.
                    val attempts = if (permanent) op.retryCount + 1 else op.retryCount
                    db.pendingOperationDao().upsert(op.copy(retryCount = attempts, lastError = reason))

                    when {
                        !permanent ->
                            AppLogger.w(tag, "Outbox item ${op.id} is transiently blocked; halting lane ${groupId ?: "platform"}.", e)
                        OutboxPolicy.isParked(attempts) ->
                            AppLogger.e(tag, "OUTBOX DEAD-LETTER: ${op.type} ${op.id} parked after $attempts rejected attempts. $reason", e)
                        else ->
                            AppLogger.w(tag, "Outbox item ${op.type} ${op.id} rejected (attempt $attempts/${OutboxPolicy.MAX_PERMANENT_ATTEMPTS}); halting lane ${groupId ?: "platform"}.", e)
                    }
                    break
                }
            }
        }
    }
}
