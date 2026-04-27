package com.sanibonani.save.service

import com.sanibonani.save.domain.model.ActuarialMetrics
import com.sanibonani.save.domain.model.AdminFeeState
import com.sanibonani.save.domain.model.AppNotification
import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.Member
import com.sanibonani.save.domain.model.NotifEvent
import com.sanibonani.save.domain.model.PayoutRequest
import com.sanibonani.save.domain.repository.ActuarialRepository
import com.sanibonani.save.domain.repository.GroupRepository
import com.sanibonani.save.domain.repository.MemberRepository
import com.sanibonani.save.domain.repository.NotificationRepository
import com.sanibonani.save.domain.repository.PayoutRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Singleton
class AdminGroupContextCacheService @Inject constructor(
    private val groupRepo: GroupRepository,
    private val memberRepo: MemberRepository,
    private val notificationRepo: NotificationRepository,
    private val payoutRepo: PayoutRepository,
    private val actuarialRepo: ActuarialRepository
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _contexts = MutableStateFlow<Map<String, CachedAdminGroupContext>>(emptyMap())
    val contexts: StateFlow<Map<String, CachedAdminGroupContext>> = _contexts.asStateFlow()

    private var cachedUserId: String? = null
    private val warmupJobs = mutableMapOf<String, Job>()

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
            if (warmupJobs[groupId]?.isActive == true) return@forEach
            if (_contexts.value[groupId]?.isFullyHydrated == true) return@forEach

            warmupJobs[groupId] = scope.launch {
                warmGroupContext(group)
            }
        }
    }

    suspend fun warmUpForUser(userId: String) {
        ensureUserSession(userId)
        val groups = groupRepo.getGroupsByAdmin(userId).getOrElse { emptyList() }
        warmManagedGroupsInBackground(userId, groups)
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

        val members = memberRepo.getGroupMembers(groupId).first().getOrElse { emptyList() }

        val notifications = runCatching {
            notificationRepo.syncNotifications(groupId)
            notificationRepo.observeNotifications(groupId).first().getOrElse { emptyList() }
        }.getOrElse { emptyList() }

        val payouts = payoutRepo.observePayouts(groupId).first().getOrElse { emptyList() }
        val metrics = actuarialRepo.computeMetrics(groupId).getOrNull()

        val (messages, systemNotifs) = notifications
            .filter { it.memberId == null || it.triggerEvent == NotifEvent.MEMBER_MESSAGE }
            .partition { it.triggerEvent == NotifEvent.MEMBER_MESSAGE }

        updateContext(groupId) {
            CachedAdminGroupContext(
                group = group,
                members = members,
                notifications = systemNotifs.sortedByDescending { n -> n.id ?: "" },
                memberMessages = messages.sortedByDescending { m -> m.id ?: "" },
                payouts = payouts,
                metrics = metrics,
                feeStatus = group.feeStatus,
                isFullyHydrated = true
            )
        }
    }

    private fun clearForUser(userId: String?) {
        cachedUserId = userId
        _contexts.value = emptyMap()
        warmupJobs.values.forEach { it.cancel() }
        warmupJobs.clear()
    }

    private fun pruneToGroups(groups: List<Group>) {
        val allowedGroupIds = groups.mapNotNull { it.id }.toSet()
        _contexts.update { cache -> cache.filterKeys { it in allowedGroupIds } }

        val staleJobs = warmupJobs.keys.filter { it !in allowedGroupIds }
        staleJobs.forEach { groupId ->
            warmupJobs.remove(groupId)?.cancel()
        }
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

