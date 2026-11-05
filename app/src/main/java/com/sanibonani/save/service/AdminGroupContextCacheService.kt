package com.sanibonani.save.service

import com.sanibonani.save.domain.utils.safeRunCatching
import com.sanibonani.save.domain.logging.AppLogger
import com.sanibonani.save.domain.model.ActuarialMetrics
import com.sanibonani.save.domain.model.AdminFeeState
import com.sanibonani.save.domain.model.AppNotification
import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.Member
import com.sanibonani.save.domain.model.NotifEvent
import com.sanibonani.save.domain.model.PayoutRequest
import com.sanibonani.save.domain.repository.admin.ActuarialRepository
import com.sanibonani.save.domain.repository.disbursement.PayoutRepository
import com.sanibonani.save.domain.repository.group.GroupRepository
import com.sanibonani.save.domain.repository.member.MemberRepository
import com.sanibonani.save.domain.repository.system.NotificationRepository
import com.sanibonani.save.domain.repository.system.SyncRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

@Singleton
class AdminGroupContextCacheService @Inject constructor(
    private val groupRepo: GroupRepository,
    private val memberRepo: MemberRepository,
    private val notificationRepo: NotificationRepository,
    private val payoutRepo: PayoutRepository,
    private val actuarialRepo: ActuarialRepository,
    private val syncRepo: SyncRepository
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _contexts = MutableStateFlow<Map<String, CachedAdminGroupContext>>(emptyMap())
    val contexts: StateFlow<Map<String, CachedAdminGroupContext>> = _contexts.asStateFlow()

    private val _selectedGroupId = MutableStateFlow<String?>(null)
    val selectedGroupId: StateFlow<String?> = _selectedGroupId.asStateFlow()

    /**
     * Per group, the relations the sync layer has not brought current.
     *
     * Read straight from the sync layer rather than copied into the snapshot: a pillar that lands
     * on a later pass clears its group's entry without any warmup rebuilding it, and the dashboard
     * must stop flagging the book the moment that happens.
     */
    val unverifiedSections: StateFlow<Map<String, Set<String>>> get() = syncRepo.staleGroups

    private var cachedUserId: String? = null
    private val warmupCoordinator = GroupContextWarmupCoordinator()

    /**
     * Hydrations this service may run at once, for every group the account administers together.
     *
     * Sized against the transport's per-host dispatch cap rather than against the group count, so a
     * platform admin with thirty groups does not present thirty times the load of one with one.
     */
    private val hydrationReadBudget = Semaphore(HYDRATION_CONCURRENT_RUNS)

    private companion object {
        const val HYDRATION_CONCURRENT_RUNS = 3
    }

    fun getContext(groupId: String): CachedAdminGroupContext? = _contexts.value[groupId]

    fun ensureUserSession(userId: String) {
        if (cachedUserId == userId) return
        clearForUser(userId)
    }

    fun clearForSignOut() {
        clearForUser(userId = null)
    }

    /**
     * Drops every cached snapshot after the developer database changes, keeping the selection.
     *
     * The rows were read from the tier that has just been replaced, so they are not a fallback -
     * and a warm-up that rebuilds a snapshot equal to the one the cache already holds does not
     * re-emit, which would leave a screen cleared for the change with nothing to refill it. The
     * selected group stays selected so the caller's re-warm reads it from the tier now in front.
     */
    fun invalidateForDatabaseChange() {
        _contexts.value = emptyMap()
        warmupCoordinator.cancelAll()
    }

    fun warmManagedGroupsInBackground(userId: String, groups: List<Group>) {
        ensureUserSession(userId)
        pruneToGroups(groups)

        groups.forEach { group ->
            val groupId = group.id
            if (groupId.isNullOrBlank()) return@forEach
            warmupCoordinator.launch(groupId, scope) {
                warmGroupContext(groupId, knownRow = group)
            }
        }
    }

    suspend fun warmUpForUser(userId: String) {
        ensureUserSession(userId)
        val groups = groupRepo.getGroupsByAdmin(userId).getOrElse {
            // An offline warm-up must keep the cached groups instead of pruning every context
            // to an empty admin set.
            groupRepo.observeGroupsByAdmin(userId).first().getOrElse {
                // Both reads failed, so the empty list here is a missing answer rather than an
                // authoritative "administers nothing". Pruning with it would delete the very
                // contexts this fallback protects; keep them and warm again once a read succeeds.
                return
            }
        }
        warmManagedGroupsInBackground(userId, groups)
    }

    /** Selects the active admin group context. */
    fun selectGroup(groupId: String) {
        if (groupId.isBlank()) return
        _selectedGroupId.value = groupId
        // Opening a group is a request for its current money, and the snapshot's group row may
        // come from the listing that filled the switcher. Only a forced warmup re-reads that row;
        // the members pillar hydrates separately, so a stale balance otherwise sits next to a
        // fresh member count.
        warmUpGroup(groupId, force = true)
    }

    /**
     * Warms a single group context on demand.
     *
     * @param force re-hydrates an already-hydrated context and cancels any in-flight
     *   warmup so the freshest data wins.
     */
    fun warmUpGroup(groupId: String, force: Boolean = false) {
        if (groupId.isBlank()) return
        if (!force && _contexts.value[groupId]?.isFullyHydrated == true) return

        warmupCoordinator.launch(groupId, scope, replaceActive = force) {
            // A forced warmup must not reuse the snapshot's group: the row may have changed since
            // it was cached, and re-reading it is the point of the refresh.
            warmGroupContext(groupId, knownRow = if (force) null else _contexts.value[groupId]?.group)
        }
    }

    /**
     * Pulls every group pillar from the network, then rebuilds the cached snapshot from the
     * freshly synced data. Awaitable, so a pull-to-refresh only reports completion once the
     * dashboard's source state has actually been replaced.
     *
     * Fails when the pull did not land. The snapshot is still rebuilt from the cache, because
     * stale data on screen beats a blank dashboard, but the caller hears that the group was never
     * brought current instead of being told the refresh succeeded.
     */
    suspend fun refreshGroup(groupId: String): Result<Unit> = safeRunCatching {
        if (groupId.isBlank()) return@safeRunCatching
        // Hold the group's warmup slot for the whole refresh so a background sweep cannot start a
        // second hydration that finishes after this one; a sweep arriving once this run has read
        // is queued behind it rather than discarded. The slot is a child job, never the
        // caller's, so a session change cancels the refresh instead of the caller's scope.
        warmupCoordinator.runExclusive(groupId, scope) {
            val syncOutcome = syncRepo.syncGroupData(groupId)
            // The caller asked for this group to be brought current, so a row the server refused
            // fails the refresh even though the snapshot keeps the row it already held.
            warmGroupContext(groupId, knownRow = null).getOrThrow()
            syncOutcome.getOrThrow()
        }
    }

    fun updateContext(
        groupId: String,
        reducer: (CachedAdminGroupContext) -> CachedAdminGroupContext
    ) {
        _contexts.update { cache ->
            val current = cache[groupId] ?: CachedAdminGroupContext()
            cache + (groupId to reducer(current))
        }
    }

    /**
     * Reads the group's pillars into its snapshot and reports the group row that snapshot was built
     * from.
     *
     * [knownRow] is a row the caller already holds - the listing that filled the group switcher - and
     * is used without a second read. Otherwise the row is read fresh, and when that read is refused
     * the pillars are still hydrated onto the row the snapshot held. Abandoning the run instead left
     * an admin with no members, no notifications and no payouts because one row did not answer, and
     * the dashboard's only signal was its own guess that the load had timed out.
     */
    private suspend fun warmGroupContext(groupId: String, knownRow: Group?): Result<Group> {
        // From here the snapshot is being built from data read now, so a warmup request that
        // arrives later needs its own run rather than being credited to this one.
        warmupCoordinator.markReadsStarted(groupId)

        val rowOutcome = knownRow?.let { Result.success(it) }
            ?: safeRunCatching { groupRepo.getGroupById(groupId).getOrThrow() }
        if (rowOutcome.isFailure) {
            AppLogger.w(
                "AdminGroupContextCache",
                "Group $groupId's row was refused; hydrating its pillars onto the cached row.",
                rowOutcome.exceptionOrNull()
            )
        }

        // The legs below run one after another, so the reads this service can have in flight are the
        // runs it allows at once. Passing over every group the account administers unbounded put eight
        // payouts reads, eight member reads and eight metric reads to the same host inside two seconds -
        // the burst the device log shows - and the group actually on screen waited behind them in a
        // queue no timeout in the app can see.
        hydrationReadBudget.withPermit {
            readGroupContext(rowOutcome, groupId)
        }
        return rowOutcome
    }

    private suspend fun readGroupContext(groupOutcome: Result<Group>, groupId: String) {
        // A failed section read must keep what the snapshot already holds: replacing it
        // with an empty list made an offline refresh look like an empty group, and the
        // hydrated flag then suppressed every later retry.
        // Contained the same way as the member service's pillars: a section read that throws, rather
        // than answering with a failed Result, used to escape the warm-up and reach the coroutine
        // handler. Now it degrades to "this section did not answer" and the snapshot keeps its rows.
        val membersOutcome = safeRunCatching { memberRepo.getGroupMembers(groupId).first().getOrThrow() }

        val notificationsOutcome = safeRunCatching {
            notificationRepo.syncNotifications(groupId)
            notificationRepo.observeNotifications(groupId).first().getOrThrow()
        }
        val payoutsOutcome = safeRunCatching { payoutRepo.observePayouts(groupId).first().getOrThrow() }
        val metrics = safeRunCatching { actuarialRepo.computeMetrics(groupId).getOrThrow() }.getOrNull()

        updateContext(groupId) { prev ->
            val group = groupOutcome.getOrNull() ?: prev.group
            val partitioned = notificationsOutcome.getOrNull()
                ?.filter { it.memberId == null || it.triggerEvent == NotifEvent.MEMBER_MESSAGE }
                ?.partition { it.triggerEvent == NotifEvent.MEMBER_MESSAGE }
            // createdAt is the ISO-8601 string PostgREST returns, which sorts chronologically as
            // text; id is a uuid, so ordering by it put the feed in a random order.
            val messages = (partitioned?.first ?: prev.memberMessages)
                .sortedByDescending { it.createdAt ?: it.id ?: "" }
            val systemNotifs = (partitioned?.second ?: prev.notifications)
                .sortedByDescending { it.createdAt ?: it.id ?: "" }
            prev.copy(
                group = group,
                members = membersOutcome.getOrNull() ?: prev.members,
                notifications = systemNotifs,
                memberMessages = messages,
                payouts = payoutsOutcome.getOrNull() ?: prev.payouts,
                metrics = metrics ?: prev.metrics,
                feeStatus = group?.feeStatus ?: prev.feeStatus,
                isFullyHydrated = groupOutcome.isSuccess &&
                    membersOutcome.isSuccess &&
                    notificationsOutcome.isSuccess &&
                    payoutsOutcome.isSuccess
            )
        }
    }

    private fun clearForUser(userId: String?) {
        cachedUserId = userId
        _contexts.value = emptyMap()
        _selectedGroupId.value = null
        warmupCoordinator.cancelAll()
    }

    private fun pruneToGroups(groups: List<Group>) {
        val allowedGroupIds = groups.mapNotNull { it.id }.toSet()
        _contexts.update { cache -> cache.filterKeys { it in allowedGroupIds } }

        warmupCoordinator.retainOnly(allowedGroupIds)
    }
}

data class CachedAdminGroupContext(
    val group: Group? = null,
    val members: List<Member> = emptyList(),
    val notifications: List<AppNotification> = emptyList(),
    val memberMessages: List<AppNotification> = emptyList(),
    val payouts: List<PayoutRequest> = emptyList(),
    val metrics: ActuarialMetrics? = null,
    val feeStatus: AdminFeeState? = null,
    /**
     * Every section of the snapshot answered from the cache. It says nothing about whether the
     * server confirmed any of it - cache reads cannot fail, so that question belongs to
     * [com.sanibonani.save.domain.repository.system.SyncRepository.staleGroups].
     */
    val isFullyHydrated: Boolean = false
)
