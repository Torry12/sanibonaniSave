package com.sanibonani.save.service

import com.sanibonani.save.domain.utils.safeRunCatching
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

    private var cachedUserId: String? = null
    private val warmupCoordinator = GroupContextWarmupCoordinator()

    fun getContext(groupId: String): CachedAdminGroupContext? = _contexts.value[groupId]

    fun ensureUserSession(userId: String) {
        if (cachedUserId == userId) return
        clearForUser(userId)
    }

    fun clearForSignOut() {
        clearForUser(userId = null)
    }

    fun warmManagedGroupsInBackground(userId: String, groups: List<Group>) {
        ensureUserSession(userId)
        pruneToGroups(groups)

        groups.forEach { group ->
            val groupId = group.id
            if (groupId.isNullOrBlank()) return@forEach
            warmupCoordinator.launch(groupId, scope) {
                warmGroupContext(group)
            }
        }
    }

    suspend fun warmUpForUser(userId: String) {
        ensureUserSession(userId)
        val groups = groupRepo.getGroupsByAdmin(userId).getOrElse {
            // An offline warm-up must keep the cached groups instead of pruning every context
            // to an empty admin set.
            groupRepo.observeGroupsByAdmin(userId).first().getOrElse { emptyList() }
        }
        warmManagedGroupsInBackground(userId, groups)
    }

    /** Selects the active admin group context. */
    fun selectGroup(groupId: String) {
        if (groupId.isBlank()) return
        _selectedGroupId.value = groupId
        warmUpGroup(groupId)
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
            val cachedGroup = if (force) null else _contexts.value[groupId]?.group
            val group = cachedGroup
                ?: safeRunCatching { groupRepo.getGroupById(groupId).getOrNull() }.getOrNull()
                ?: return@launch
            warmGroupContext(group)
        }
    }

    /**
     * Pulls every group pillar from the network, then rebuilds the cached snapshot from the
     * freshly synced data. Awaitable, so a pull-to-refresh only reports completion once the
     * dashboard's source state has actually been replaced.
     */
    suspend fun refreshGroup(groupId: String): Result<Unit> = safeRunCatching {
        if (groupId.isBlank()) return@safeRunCatching
        // Hold the group's warmup slot for the whole refresh so a background sweep cannot start a
        // second hydration that finishes after this one. The slot is a child job, never the
        // caller's, so a session change cancels the refresh instead of the caller's scope.
        warmupCoordinator.runExclusive(groupId) {
            syncRepo.syncGroupData(groupId)
            warmGroupContext(groupRepo.getGroupById(groupId).getOrThrow())
        }
    }

    fun updateContext(
        groupId: String,
        reducer: (CachedAdminGroupContext) -> CachedAdminGroupContext
    ) {
        _contexts.update { cache ->
            val current = cache[groupId] ?: CachedAdminGroupContext()
            cache + (groupId to reducer(current).copy(lastUpdatedMillis = System.currentTimeMillis()))
        }
    }

    private suspend fun warmGroupContext(group: Group) {
        val groupId = group.id ?: return

        // A failed section read must keep what the snapshot already holds: replacing it
        // with an empty list made an offline refresh look like an empty group, and the
        // hydrated flag then suppressed every later retry.
        val membersOutcome = memberRepo.getGroupMembers(groupId).first()

        val notificationsOutcome = safeRunCatching {
            notificationRepo.syncNotifications(groupId)
            notificationRepo.observeNotifications(groupId).first().getOrThrow()
        }
        val payoutsOutcome = payoutRepo.observePayouts(groupId).first()
        val metrics = actuarialRepo.computeMetrics(groupId).getOrNull()

        updateContext(groupId) { prev ->
            val partitioned = notificationsOutcome.getOrNull()
                ?.filter { it.memberId == null || it.triggerEvent == NotifEvent.MEMBER_MESSAGE }
                ?.partition { it.triggerEvent == NotifEvent.MEMBER_MESSAGE }
            val messages = (partitioned?.first ?: prev.memberMessages)
                .sortedByDescending { it.id ?: "" }
            val systemNotifs = (partitioned?.second ?: prev.notifications)
                .sortedByDescending { it.id ?: "" }
            prev.copy(
                group = group,
                members = membersOutcome.getOrNull() ?: prev.members,
                notifications = systemNotifs,
                memberMessages = messages,
                payouts = payoutsOutcome.getOrNull() ?: prev.payouts,
                metrics = metrics ?: prev.metrics,
                feeStatus = group.feeStatus,
                isFullyHydrated = membersOutcome.isSuccess &&
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
    val isFullyHydrated: Boolean = false,
    val lastUpdatedMillis: Long = 0L
)
