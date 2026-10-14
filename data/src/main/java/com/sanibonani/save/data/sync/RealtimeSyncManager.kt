package com.sanibonani.save.data.sync

import com.sanibonani.save.data.local.*
import com.sanibonani.save.data.local.toEntity
import com.sanibonani.save.data.local.toLedgerEntity
import com.sanibonani.save.domain.logging.AppLogger
import com.sanibonani.save.domain.model.*
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import com.sanibonani.save.domain.utils.safeRunCatching

@Singleton
class RealtimeSyncManager @Inject constructor(
    private val supabase: SupabaseClient,
    private val db: SanibonaniDatabase,
    private val json: Json
) {
    private val tag = "RealtimeSyncManager"
    private var managedScope: CoroutineScope? = null
    private val activeChannels = ConcurrentHashMap<String, RealtimeChannel>()
    private var channelGeneration = 0

    private val _syncMetrics = MutableStateFlow<Map<String, RealtimeSyncStatus>>(emptyMap())
    val syncMetrics: StateFlow<Map<String, RealtimeSyncStatus>> = _syncMetrics.asStateFlow()

    // ── Batch Processors ─────────────────────────────────────────────────────
    private var contributionBatcher: BatchProcessor<Contribution>? = null
    private var paymentBatcher: BatchProcessor<Payment>? = null
    private var ledgerBatcher: BatchProcessor<LedgerEntry>? = null
    private var notificationBatcher: BatchProcessor<AppNotification>? = null

    @Synchronized
    fun start() {
        if (managedScope != null && managedScope!!.isActive) {
            AppLogger.d(tag, "RealtimeSyncManager already running")
            return
        }

        AppLogger.d(tag, "Starting restartable realtime subscriptions...")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        managedScope = scope

        // Initialize batchers within the new scope
        contributionBatcher = BatchProcessor(scope) { list ->
            db.contributionDao().upsertContributions(list.map { it.toEntity() })
        }
        paymentBatcher = BatchProcessor(scope) { list ->
            db.paymentDao().upsertPayments(list.map { it.toEntity() })
        }
        ledgerBatcher = BatchProcessor(scope) { list ->
            db.ledgerDao().upsertLedger(list.map { it.toLedgerEntity() })
        }
        notificationBatcher = BatchProcessor(scope) { list ->
            db.notificationDao().upsertNotifications(list.map { it.toEntity() })
        }

        // A unique id per session guarantees we never re-attach postgresChangeFlow to a channel
        // that a previous session may still have in SUBSCRIBED state (stop() unsubscribes asynchronously).
        val channelId = "rt_sanibonani_public_${channelGeneration++}"
        val channel = supabase.realtime.channel(channelId)
        activeChannels[channelId] = channel

        attachTableFlow<Contribution>(scope, channel, "contributions") { action ->
            when (action) {
                is SyncAction.Upsert -> contributionBatcher?.enqueue(action.data)
                is SyncAction.Delete -> db.contributionDao().deleteContribution(action.id)
            }
        }
        attachTableFlow<Payment>(scope, channel, "payments") { action ->
            when (action) {
                is SyncAction.Upsert -> paymentBatcher?.enqueue(action.data)
                is SyncAction.Delete -> db.paymentDao().deleteById(action.id)
            }
        }
        attachTableFlow<Loan>(scope, channel, "loans") { action ->
            when (action) {
                is SyncAction.Upsert -> db.loanDao().upsertLoan(action.data.toEntity())
                is SyncAction.Delete -> db.loanDao().deleteLoan(action.id)
            }
        }
        attachTableFlow<LoanRepayment>(scope, channel, "loan_repayments") { action ->
            when (action) {
                is SyncAction.Upsert -> db.loanDao().upsertRepayments(listOf(action.data.toEntity()))
                is SyncAction.Delete -> db.loanDao().deleteRepaymentById(action.id)
            }
        }
        attachTableFlow<LedgerEntry>(scope, channel, "group_ledger") { action ->
            when (action) {
                is SyncAction.Upsert -> ledgerBatcher?.enqueue(action.data)
                is SyncAction.Delete -> db.ledgerDao().deleteById(action.id)
            }
        }
        attachTableFlow<AppNotification>(scope, channel, "notifications") { action ->
            when (action) {
                is SyncAction.Upsert -> notificationBatcher?.enqueue(action.data)
                is SyncAction.Delete -> db.notificationDao().deleteById(action.id)
            }
        }
        attachTableFlow<PayoutRequest>(scope, channel, "payouts") { action ->
            when (action) {
                is SyncAction.Upsert -> db.payoutDao().upsertPayout(action.data.toEntity())
                is SyncAction.Delete -> db.payoutDao().deletePayout(action.id)
            }
        }
        attachTableFlow<Beneficiary>(scope, channel, "beneficiaries") { action ->
            when (action) {
                is SyncAction.Upsert -> db.beneficiaryDao().upsertBeneficiary(action.data.toEntity())
                is SyncAction.Delete -> db.beneficiaryDao().deleteById(action.id)
            }
        }
        attachTableFlow<MemberDocument>(scope, channel, "member_documents") { action ->
            when (action) {
                is SyncAction.Upsert -> db.memberDocumentDao().upsertDocument(action.data.toEntity())
                is SyncAction.Delete -> db.memberDocumentDao().deleteById(action.id)
            }
        }
        attachTableFlow<BeneficiaryPayoutClaim>(scope, channel, "beneficiary_payout_claims") { action ->
            when (action) {
                is SyncAction.Upsert -> db.beneficiaryClaimDao().upsertClaim(action.data.toEntity())
                is SyncAction.Delete -> db.beneficiaryClaimDao().deleteClaim(action.id)
            }
        }
        attachTableFlow<Group>(scope, channel, "groups") { action ->
            when (action) {
                is SyncAction.Upsert -> db.groupDao().upsertGroup(action.data.toEntity())
                is SyncAction.Delete -> {}
            }
        }
        attachTableFlow<Member>(scope, channel, "members") { action ->
            when (action) {
                is SyncAction.Upsert -> db.memberDao().upsertMember(action.data.toEntity())
                is SyncAction.Delete -> db.memberDao().deleteMember(action.id)
            }
        }
        attachTableFlow<BankTransaction>(scope, channel, "bank_transactions") { action ->
            when (action) {
                is SyncAction.Upsert -> db.bankTransactionDao().upsert(listOf(action.data.toEntity()))
                is SyncAction.Delete -> {}
            }
        }

        scope.launch {
            try {
                channel.subscribe()
                AppLogger.d(tag, "Successfully subscribed to multiplexed realtime channel")
            } catch (e: Exception) {
                AppLogger.e(tag, "Multiplexed realtime subscription failed: ${e.message}")
            }
        }
    }

    private inline fun <reified T : Any> attachTableFlow(
        scope: CoroutineScope,
        channel: RealtimeChannel,
        tableName: String,
        crossinline onUpdate: suspend (SyncAction<T>) -> Unit
    ) {
        val changes = channel.postgresChangeFlow<PostgresAction>(schema = "public") { table = tableName }
        _syncMetrics.update { it + (tableName to RealtimeSyncStatus(isSubscribed = true, lastUpdate = System.currentTimeMillis())) }

        scope.launch {
            try {
                changes.collect { action ->
                    when (action) {
                        is PostgresAction.Insert, is PostgresAction.Update -> {
                            val record = (action as? PostgresAction.Insert)?.record
                                ?: (action as? PostgresAction.Update)?.record
                                ?: return@collect

                            safeRunCatching("$tag.$tableName") {
                                val entity = json.decodeFromJsonElement<T>(record.jsonObject)
                                onUpdate(SyncAction.Upsert(entity))
                                _syncMetrics.update { it + (tableName to RealtimeSyncStatus(isSubscribed = true, lastUpdate = System.currentTimeMillis())) }
                            }
                        }
                        is PostgresAction.Delete -> {
                            val id = action.oldRecord["id"]?.jsonPrimitive?.content
                            if (id != null) {
                                onUpdate(SyncAction.Delete(id))
                                _syncMetrics.update { it + (tableName to RealtimeSyncStatus(isSubscribed = true, lastUpdate = System.currentTimeMillis())) }
                            }
                        }
                        else -> {}
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                AppLogger.w(tag, "$tableName collection failed: ${e.message}")
                _syncMetrics.update { it + (tableName to RealtimeSyncStatus(isSubscribed = false, lastError = e.message)) }
            }
        }
    }

    @Synchronized
    fun stop() {
        AppLogger.d(tag, "Stopping realtime subscriptions...")
        managedScope?.cancel()
        managedScope = null

        // Explicitly unsubscribe and deregister all channels so a later start() cannot resolve them
        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            activeChannels.values.forEach { active ->
                safeRunCatching { supabase.realtime.removeChannel(active) }
                    .onFailure { safeRunCatching { active.unsubscribe() } }
            }
            activeChannels.clear()
        }

        contributionBatcher = null
        paymentBatcher = null
        ledgerBatcher = null
        notificationBatcher = null
    }

    fun reconnect() {
        stop()
        start()
    }

    sealed interface SyncAction<out T> {
        data class Upsert<T>(val data: T) : SyncAction<T>
        data class Delete(val id: String) : SyncAction<Nothing>
    }
}

/**
 * Throttles multiple individual updates into a single batch operation.
 */
class BatchProcessor<T>(
    private val scope: CoroutineScope,
    private val debounceMillis: Long = 200L,
    private val onBatch: suspend (List<T>) -> Unit
) {
    private val queue = mutableListOf<T>()
    private var batchJob: Job? = null

    fun enqueue(item: T) {
        synchronized(queue) {
            queue.add(item)
        }
        if (batchJob == null || !batchJob!!.isActive) {
            batchJob = scope.launch {
                delay(debounceMillis)
                val toProcess = synchronized(queue) {
                    val list = queue.toList()
                    queue.clear()
                    list
                }
                if (toProcess.isNotEmpty()) {
                    onBatch(toProcess)
                }
            }
        }
    }
}
