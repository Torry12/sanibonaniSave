package com.sanibonani.save.data.repository.system

import com.sanibonani.save.data.repository.BaseRepository
import com.sanibonani.save.data.local.*
import com.sanibonani.save.data.local.toEntity
import com.sanibonani.save.data.local.toLedgerEntity
import com.sanibonani.save.data.remote.supabase.fetchAllLedgerPages
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
import io.github.jan.supabase.postgrest.query.Order
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout
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
            "beneficiary_claim" to beneficiaryClaimHandler
        )
    }

    private val _syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    override val syncStatus = _syncStatus.asStateFlow()

    private val _staleGroups = MutableStateFlow<Map<String, Set<String>>>(emptyMap())
    override val staleGroups = _staleGroups.asStateFlow()

    private val globalSyncMutex = Mutex()
    private val globalSyncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeSync: Deferred<Result<Unit>>? = null

    private val groupSyncMutex = Mutex()
    private val activeGroupSyncs = ConcurrentHashMap<String, Deferred<Result<Unit>>>()

    /**
     * Reads the background sweep may hold at once, for every group together.
     *
     * The pass used to fire a lane per group and an [async] per pillar inside it, so a platform
     * admin with eight groups put up to 56 requests in front of the transport at once. Its per-host
     * dispatch cap is smaller than that and every request past the cap waits in a queue that no
     * timeout in the app can see, which is how a sweep stalled mid-banner while the phone stayed
     * silent.
     */
    private val sweepReadBudget = Semaphore(SWEEP_CONCURRENT_READS)

    /**
     * Reads a hydration a screen is waiting for may hold at once.
     *
     * Its own slots, not the sweep's: sharing that queue would make an open group profile wait for
     * a platform pass the user never asked for. The two budgets together stay under the transport's
     * per-host cap (`NetworkModule.provideOkHttpClient`), so neither of them queues there either.
     */
    private val interactiveReadBudget = Semaphore(INTERACTIVE_CONCURRENT_READS)

    /** A sweep spans the whole platform; test seam for the deadline it is given. */
    internal var sweepTimeoutMillis = GLOBAL_SWEEP_TIMEOUT_MS

    override val outboxCount: kotlinx.coroutines.flow.Flow<Int> = db.pendingOperationDao().observeCount()

    /**
     * Single-flight global sync. Concurrent triggers (cold start, pull-to-refresh, Support Mode
     * entry) join the in-flight sync and share its Result rather than overlapping.
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
            // A sweep covers every group the user has, so it starts from a clean slate: what the
            // previous session could not verify is not this session's answer.
            _staleGroups.value = emptyMap()
            val stillReading = ConcurrentHashMap.newKeySet<String>()
            try {
                withTimeout(sweepTimeoutMillis) { sweepPass(stillReading) }
            } catch (timeout: TimeoutCancellationException) {
                // [safeRunCatching] rethrows cancellation, so a timeout released here reached the
                // caller as its own cancellation: the sweep never wrote a terminal status and the
                // banner kept showing the last progress it stamped, with nothing left to clear it.
                // Turned into a refusal, the pass ends, every awaiter gets a verdict, and the names
                // of the groups still unread are worth more than the deadline itself.
                AppLogger.w(
                    tag,
                    "Platform hydration stopped after ${sweepTimeoutMillis}ms with " +
                        "${stillReading.size} group(s) still reading: ${stillReading.joinToString()}",
                    timeout
                )
                throw sweepStalled(timeout)
            }
    }.onFailure { e ->
        if (e is CancellationException) throw e
        val userMessage = e.toUserMessage()
        _syncStatus.value = SyncStatus.Error(userMessage)
    }

    /**
     * One platform-wide pass: read every group the user has, then drain the offline queue.
     *
     * [stillReading] holds the groups whose lane has not returned, so a pass that gives up can name
     * what it never finished instead of leaving the log at the last line it managed to write.
     */
    private suspend fun sweepPass(stillReading: MutableSet<String>) {
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
            AppLogger.i(
                tag,
                "Platform hydration started for ${groupIds.size} group(s), " +
                    "${GroupHydrationPlan.startupPillars(userRole).size} pillar(s) each"
            )
            _syncStatus.value = SyncStatus.Progress("Hydrating ${groupIds.size} group(s)...", 0.10f)

            val incomplete = coroutineScope {
                val totalGroups = groupIds.size
                val completedGroups = AtomicInteger(0)
                stillReading.addAll(groupIds)

                groupIds.map { groupId ->
                    async {
                        try {
                            val missed = syncGroupDataInternal(
                                groupId = groupId,
                                pillars = GroupHydrationPlan.startupPillars(userRole),
                                includeGroupMetadata = userRole == UserRole.PLATFORM_ADMIN,
                                readBudget = sweepReadBudget
                            )

                            val completed = completedGroups.incrementAndGet()
                            AppLogger.i(tag, "Hydrated group $groupId ($completed of $totalGroups)")
                            val progress = 0.10f + (completed.toFloat() / totalGroups) * 0.85f
                            _syncStatus.value = SyncStatus.Progress(
                                "Synced $completed of $totalGroups groups",
                                progress
                            )

                            if (missed.isEmpty()) null else groupId to missed
                        } finally {
                            stillReading.remove(groupId)
                        }
                    }
                }.awaitAll().filterNotNull()
            }

            // Startup stays best-effort: an offline device is meant to open on the Room
            // cache, and one group that will not answer must not strand the user on an
            // error. The pass still names the books it could not bring current.
            if (incomplete.isNotEmpty()) {
                AppLogger.w(
                    tag,
                    "Startup hydration left ${incomplete.size} group(s) partly stale: " +
                        incomplete.joinToString { "${it.first} (${it.second.joinToString()})" }
                )
            }
        } else {
            AppLogger.d(tag, "No groups found for hydration.")
        }

        _syncStatus.value = SyncStatus.Progress("Processing offline queue...", 0.95f)
        processOutbox().getOrNull()

        AppLogger.i(tag, "Platform hydration finished")
        _syncStatus.value = SyncStatus.Completed
    }

    private fun sweepStalled(timeout: TimeoutCancellationException) = IllegalStateException(
        "The background refresh ran out of time (${timeout.message}). " +
            "What the app is showing is the last data it could verify."
    )

    /**
     * Pulls a group's relations and returns the ones that did not land.
     *
     * The caller decides what a miss means: startup logs it and opens on the cache, a
     * user-initiated refresh turns it into a failure. The results used to be awaited and thrown
     * away here, so a refresh that pulled nothing still reported a clean group.
     *
     * Each miss is also recorded against the group in [staleGroups], so the app can still tell
     * which books it is showing unverified after the best-effort pass has finished.
     *
     * [readBudget] is spent per leg, not per group: the legs of one group are the requests that
     * used to leave together for the server, and a lane that held its permit across all of them
     * would only move the queueing somewhere else.
     */
    private suspend fun syncGroupDataInternal(
        groupId: String,
        pillars: List<DomainPillar>,
        includeGroupMetadata: Boolean,
        readBudget: Semaphore
    ): List<String> = coroutineScope {
        val reads = mutableListOf<Deferred<List<String>>>()

        if (includeGroupMetadata) {
            reads += async {
                readBudget.withPermit {
                    val pulls = Pulls()
                    pulls.pull("$tag.groupMetadata", "group settings") {
                        groupRepo.getGroupById(groupId).getOrThrow()
                    }
                    pulls.recordAgainst(groupId)
                }
            }
        }
        reads += async {
            readBudget.withPermit {
                val pulls = Pulls()
                pulls.pull("$tag.members", "members") { memberRepo.syncGroupMembers(groupId).getOrThrow() }
                pulls.pull("$tag.notifications", "notifications") {
                    notificationRepo.syncNotifications(groupId).getOrThrow()
                }
                pulls.recordAgainst(groupId)
            }
        }
        pillars.forEach { pillar ->
            // The pillar records its own relations inside syncRefresh; this only names the pillar
            // for the caller's message.
            reads += async {
                readBudget.withPermit {
                    val outcome = syncRefresh(pillar, groupId)
                    if (outcome.isSuccess) emptyList() else listOf(pillar.name.lowercase())
                }
            }
        }

        reads.awaitAll().flatten()
    }

    /**
     * Single-flight per group, the way [syncAllData] joins one platform pass. The admin and member
     * caches each keep their own warm-up coordinator, so a Support Mode session can ask for the same
     * group twice at once; overlapping hydrations doubled every pull and then raced each other
     * writing that group's stale-sections verdict.
     *
     * The pass runs on [globalSyncScope] so the caller that started it can leave without cancelling
     * the refresh the other callers are awaiting.
     */
    override suspend fun syncGroupData(groupId: String): Result<Unit> {
        val flight = groupSyncMutex.withLock {
            activeGroupSyncs[groupId]?.takeIf { it.isActive }
                ?: globalSyncScope.async { runGroupSync(groupId) }.also { pending ->
                    activeGroupSyncs[groupId] = pending
                    pending.invokeOnCompletion { activeGroupSyncs.remove(groupId, pending) }
                }
        }
        return flight.await()
    }

    private suspend fun runGroupSync(groupId: String): Result<Unit> = safeRunCatching {
        AppLogger.i(tag, "Full refresh for group $groupId")
        val missed = syncGroupDataInternal(
            groupId = groupId,
            pillars = GroupHydrationPlan.fullRefreshPillars,
            includeGroupMetadata = true,
            readBudget = interactiveReadBudget
        )
        if (missed.isNotEmpty()) throw hydrationFailure(missed)
    }

    override suspend fun syncRefresh(pillar: DomainPillar, groupId: String): Result<Unit> {
        AppLogger.d(tag, "Refreshing pillar ${pillar.name} for group $groupId")
        val pulls = pullPillar(pillar, groupId)
        pulls.recordAgainst(groupId)
        return if (pulls.missed.isEmpty()) Result.success(Unit) else Result.failure(hydrationFailure(pulls.missed))
    }

    private fun hydrationFailure(missed: List<String>) = IllegalStateException(
        "This group could not be fully refreshed (${missed.joinToString()}). " +
            "What the app is showing is the last data it could verify."
    )

    /**
     * Records what one hydration run read against its group.
     *
     * A relation is only dropped when a later run actually read it, so a treasury refresh cannot
     * wipe out a ledger miss it never looked at.
     */
    private fun Pulls.recordAgainst(groupId: String): List<String> {
        _staleGroups.update { current ->
            val outstanding = current[groupId].orEmpty() - covered.toSet() + missed.toSet()
            if (outstanding.isEmpty()) current - groupId else current + (groupId to outstanding)
        }
        return missed
    }

    /** What one hydration run read, and which of those relations did not land. */
    private class Pulls {
        val covered = mutableListOf<String>()
        val missed = mutableListOf<String>()
    }

    /**
     * Reads every relation the pillar owns and names the misses.
     *
     * Each read is judged on its own outcome. The branch used to end at its first failure, so a
     * group whose bank connection could not be read also never hydrated its transactions, and
     * every result was discarded so the pass reported success whatever happened.
     */
    private suspend fun pullPillar(pillar: DomainPillar, groupId: String): Pulls {
        val pulls = Pulls()

        when (pillar) {
            DomainPillar.EQUITY -> {
                pulls.pull("$tag.equityContributions", "contributions") {
                    val data = supabase.postgrest["contributions"].select { filter { eq("group_id", groupId) } }.decodeList<Contribution>()
                    db.contributionDao().syncGroupContributions(groupId, data.map { it.toEntity() })
                }

                pulls.pull("$tag.equityPayments", "payments") {
                    val payments = supabase.postgrest["payments"].select { filter { eq("group_id", groupId) } }.decodeList<Payment>()
                    db.paymentDao().syncPayments(groupId, payments.map { it.toEntity() })
                }
            }
            DomainPillar.GLOBAL -> {
                pulls.pull("$tag.members", "members") { memberRepo.syncGroupMembers(groupId).getOrThrow() }
            }
            DomainPillar.CREDIT -> {
                pulls.pull("$tag.loans", "loans") {
                    val data = supabase.postgrest["loans"].select { filter { eq("group_id", groupId) } }.decodeList<Loan>()
                    db.loanDao().syncGroupLoans(groupId, data.map { it.toEntity() })
                }
            }
            DomainPillar.DISBURSEMENT -> {
                pulls.pull("$tag.payouts", "payout requests") {
                    val payouts = supabase.postgrest["payouts"].select { filter { eq("group_id", groupId) } }.decodeList<PayoutRequest>()
                    db.payoutDao().syncPayouts(groupId, payouts.map { it.toEntity() })
                }

                pulls.pull("$tag.claims", "burial claims") {
                    // One read, one outcome. The old fallback re-issued the same rows against
                    // `burial_claims`, a table no schema defines, so it could only ever 404 too -
                    // and the second failure is what reached the log instead of the real cause.
                    val claims = supabase.postgrest["beneficiary_payout_claims"].select(columns = Columns.raw("*")) {
                        filter { eq("group_id", groupId) }
                    }.decodeList<BeneficiaryPayoutClaim>()
                    db.beneficiaryClaimDao().syncForGroup(groupId, claims.map { it.toEntity() })
                }
            }
            DomainPillar.TREASURY -> {
                pulls.pull("$tag.treasuryLedger", "the ledger") {
                    val data = fetchAllLedgerPages { from, to ->
                        supabase.postgrest["group_ledger"].select(columns = Columns.raw("*")) {
                            filter { eq("group_id", groupId) }
                            order("created_at", order = Order.DESCENDING)
                            order("id", order = Order.DESCENDING)
                            range(from, to)
                        }.decodeList<LedgerEntry>()
                    }
                    db.ledgerDao().syncLedger(groupId, data.map { it.toLedgerEntity() })
                }
            }
            DomainPillar.BANK -> {
                pulls.pull("$tag.bankConnection", "the bank connection") {
                    val connection = supabase.postgrest["bank_connections"].select(columns = Columns.raw("*")) {
                        filter { eq("group_id", groupId) }
                    }.decodeSingleOrNull<BankConnection>()
                    connection?.let { db.bankTransactionDao().upsertConnection(it.toEntity()) }
                }

                pulls.pull("$tag.bankTransactions", "bank transactions") {
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
                pulls.pull("$tag.memberBehaviorTracks", "member behaviour") {
                    val tracks = supabase.postgrest["member_behavior_track"].select(columns = Columns.raw("*")) {
                        filter { eq("group_id", groupId) }
                    }.decodeList<MemberBehaviorTrack>()
                    db.memberBehaviorTrackDao().syncForGroup(groupId, tracks.map { it.toEntity() })
                }
            }
        }

        return pulls
    }

    /**
     * Runs one hydration read, naming it whether or not it landed.
     * [safeRunCatching] logs the cause and lets cancellation through, so a miss is recorded here
     * rather than swallowed.
     */
    private inline fun Pulls.pull(logKey: String, label: String, block: () -> Any?) {
        covered += label
        safeRunCatching(logKey, block).exceptionOrNull()?.let { missed += label }
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
                val handler = handlers[op.type.lowercase()]
                if (handler == null) {
                    // A type this build has no handler for is a row written by a version that did.
                    // Replaying it can never succeed, so it parks on first sight: the row is kept
                    // for investigation without halting the group's lane for the full ceiling while
                    // real money movements queue up behind an operation that will never apply.
                    val reason = "No handler for operation type: ${op.type}"
                    AppLogger.e(tag, "OUTBOX DEAD-LETTER: ${op.type} ${op.id} has no handler in this build; parked.")
                    db.pendingOperationDao().upsert(
                        op.copy(
                            retryCount = OutboxPolicy.MAX_PERMANENT_ATTEMPTS,
                            lastError = reason
                        )
                    )
                    break
                }
                try {
                    AppLogger.d(tag, "Attempting to process ${op.type} for group ${op.groupId}")

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

    companion object {
        /**
         * How long a platform-wide pass may take before it reports what it could not read.
         *
         * The sweep spans every group the user has, so a deadline is a budget rather than a guess
         * about the server; what it must never do is expire silently and leave the progress it
         * already published as the last thing on screen.
         */
        private const val GLOBAL_SWEEP_TIMEOUT_MS = 120_000L

        /**
         * The reads a background pass may hold at once, across all its groups.
         *
         * Kept below the transport's per-host dispatch cap together with [INTERACTIVE_CONCURRENT_READS]
         * so a hydration request is never queued behind a sweep at the HTTP layer, where no timeout
         * in this app can see it.
         */
        private const val SWEEP_CONCURRENT_READS = 3

        /** The reads a hydration a screen is waiting on may hold at once, in its own slots. */
        private const val INTERACTIVE_CONCURRENT_READS = 4
    }
}
