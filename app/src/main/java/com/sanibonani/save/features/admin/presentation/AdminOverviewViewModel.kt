package com.sanibonani.save.features.admin.presentation

import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.admin.HealthScoreRepository
import com.sanibonani.save.domain.repository.auth.SupabaseRepository
import com.sanibonani.save.domain.repository.treasury.LedgerRepository
import com.sanibonani.save.domain.service.NetworkConnectivityService
import com.sanibonani.save.domain.usecase.group.GetManagedGroupsUseCase
import com.sanibonani.save.domain.usecase.group.GetGroupBusinessInsightsUseCase
import com.sanibonani.save.domain.utils.toUserMessage
import com.sanibonani.save.data.utils.DevSettings
import com.sanibonani.save.domain.logging.AppLogger
import com.sanibonani.save.data.sync.RealtimeSyncManager
import com.sanibonani.save.service.AdminGroupContextCacheService
import com.sanibonani.save.features.common.presentation.BaseViewModel
import com.sanibonani.save.features.common.presentation.UIEvent
import com.sanibonani.save.features.admin.presentation.coordination.AdminActionHost
import com.sanibonani.save.features.admin.presentation.coordination.AdminGroupOperationsCoordinator
import com.sanibonani.save.features.admin.presentation.coordination.AdminMessagingCoordinator
import com.sanibonani.save.features.admin.presentation.coordination.AdminPlatformFeeCoordinator
import com.sanibonani.save.features.admin.presentation.coordination.AdminReportingCoordinator
import com.sanibonani.save.features.admin.presentation.coordination.AdminViabilityCoordinator
import com.sanibonani.save.features.admin.presentation.state.AdminGroupContext
import com.sanibonani.save.features.admin.presentation.state.AdminOverviewUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

import kotlin.time.Duration.Companion.seconds

@HiltViewModel
class AdminOverviewViewModel @Inject constructor(
    private val adminContextCacheService: AdminGroupContextCacheService,
    private val getManagedGroupsUseCase: GetManagedGroupsUseCase,
    private val getGroupBusinessInsightsUseCase: GetGroupBusinessInsightsUseCase,
    private val healthScoreRepo: HealthScoreRepository,
    private val supabaseRepo: SupabaseRepository,
    private val ledgerRepo: LedgerRepository,
    private val devSettings: DevSettings,
    private val syncManager: RealtimeSyncManager,
    private val viabilityCoordinator: AdminViabilityCoordinator,
    private val messagingCoordinator: AdminMessagingCoordinator,
    private val platformFeeCoordinator: AdminPlatformFeeCoordinator,
    private val reportingCoordinator: AdminReportingCoordinator,
    private val groupOperationsCoordinator: AdminGroupOperationsCoordinator,
    networkService: NetworkConnectivityService,
) : BaseViewModel<AdminOverviewUiState>(AdminOverviewUiState()) {

    val state: StateFlow<AdminOverviewUiState> = uiState

    private val host: AdminActionHost = object : AdminActionHost {
        override val adminState: StateFlow<AdminOverviewUiState> get() = state

        override fun updateAdminState(reducer: (AdminOverviewUiState) -> AdminOverviewUiState) {
            updateState(reducer)
        }

        override fun launchAdminJob(key: String, block: suspend CoroutineScope.() -> Unit): Job =
            launchJob(key, block)

        override fun launchAdminSafeJob(
            key: String,
            errorPrefix: String?,
            block: suspend CoroutineScope.() -> Unit
        ): Job = launchSafeJob(key, errorPrefix, block = block)

        override fun cancelAdminJob(key: String) = cancelJob(key)

        override suspend fun <T> runWithLoading(block: suspend () -> T): T = withLoading(block)

        override fun sendUiEvent(event: UIEvent) = sendEvent(event)

        override fun reportError(throwable: Throwable) = handleError(throwable)
    }

    init {
        updateState { it.copy(currentUserId = supabaseRepo.currentUserId) }
        platformFeeCoordinator.applyRegistrationFee(host)
        observeContext()
        loadManagedGroups()
        platformFeeCoordinator.loadBankDetails(host)
        observeNetwork(networkService) { s, online, airplane ->
            s.copy(isSystemOnline = online, isAirplaneMode = airplane)
        }
        observeSettings()
        observeSyncMetrics()
    }

    fun setSupportMode(enabled: Boolean) {
        updateState { it.copy(isSupportMode = enabled) }
        if (enabled) {
            refreshMembers()
        } else {
            loadManagedGroups()
        }
    }

    fun dismissImpersonationBanner() {
        updateState { it.copy(isImpersonationBannerDismissed = true) }
    }

    private fun observeSyncMetrics() {
        launchJob("sync_metrics") {
            syncManager.syncMetrics.collect { metrics ->
                updateState { it.copy(syncMetrics = metrics) }
            }
        }
    }

    private fun observeSettings() {
        launchJob("settings_observation") {
            devSettings.settingsChanged.collect {
                updateState { it.copy(group = null, members = emptyList(), managedGroups = AsyncResult.Uninitialized) }
                loadManagedGroups()
            }
        }
    }

    private fun observeContext() {
        launchJob("context_observation") {
            observeGroupContext(
                groupIdFlow = adminContextCacheService.selectedGroupId,
                contextSource = adminContextCacheService.contexts,
            ) { context ->
                val selectedId = adminContextCacheService.selectedGroupId.value

                if (context == null) {
                    if (selectedId != null) {
                        // We have a selection but no data yet.
                        // Start a safety timer to clear the loading state if hydration is taking too long.
                        launchJob("hydration_safety_timer") {
                            delay(7.seconds)
                            if (uiState.value.group == null) {
                                updateState { it.copy(isLoading = false, error = "Loading timed out. Please try again.") }
                            }
                        }
                    }
                    return@observeGroupContext
                }

                cancelJob("hydration_safety_timer")

                val pkg = AdminGroupContext(
                    group = context.group ?: return@observeGroupContext,
                    members = context.members,
                    metrics = context.metrics ?: ActuarialMetrics(),
                    notifications = context.notifications,
                    messages = context.memberMessages
                )

                updateState { it.copy(
                    group = pkg.group,
                    members = pkg.members,
                    metrics = pkg.metrics,
                    notifications = pkg.notifications,
                    memberMessages = pkg.messages,
                    feeStatus = context.feeStatus ?: AdminFeeState.DUE,
                    isLoading = !context.isFullyHydrated,
                    error = it.error
                ) }

                if (context.isFullyHydrated) {
                    startGroupObservation(pkg.group)
                    val groupId = pkg.group.id
                    if (groupId != null) {
                        platformFeeCoordinator.loadOutstanding(host, groupId)
                    }
                }
            }
        }
    }

    private fun startGroupObservation(group: Group) {
        val groupId = group.id ?: return
        launchJob("group_observation") {
            // AI Performance optimization: Parallel warmup pattern
            // Fetch initial insights and health score concurrently to minimize initial UI lag.
            kotlinx.coroutines.coroutineScope {
                val members = uiState.value.members
                val insightsTask = async(kotlinx.coroutines.Dispatchers.Default) {
                    getGroupBusinessInsightsUseCase(group, members)
                }
                val healthScoreTask = async {
                    healthScoreRepo.observeGroupHealthScore(groupId).firstOrNull()?.getOrNull()
                }

                val insights = insightsTask.await()
                val healthScore = healthScoreTask.await()

                updateState { it.copy(
                    businessInsight = insights,
                    healthScore = healthScore
                ) }
            }

            // Continuous observation for real-time updates
            launch {
                healthScoreRepo.observeGroupHealthScore(groupId).collect { res ->
                    res.onSuccess { score -> updateState { it.copy(healthScore = score) } }
                }
            }

            launch {
                ledgerRepo.observeGroupLedger(groupId)
                    .flowOn(kotlinx.coroutines.Dispatchers.Default)
                    .collect { res ->
                        val ledger = res.getOrDefault(emptyList())
                        reportingCoordinator.verify(host, group, uiState.value.members, ledger)
                    }
            }
        }
    }

    private fun loadManagedGroups() {
        val userId = supabaseRepo.currentUserId ?: return
        launchSafeJob("managed_groups_loading", "Failed to load managed groups") {
            updateState { it.copy(managedGroups = AsyncResult.Loading) }
            val role = supabaseRepo.getUserRole()
            val isPlatformAdmin = role == UserRole.PLATFORM_ADMIN
            getManagedGroupsUseCase.observeManagedGroups(userId, adminOnly = true, isPlatformAdmin = isPlatformAdmin).collectLatest { res: Result<List<Group>> ->
                res.onSuccess { groups ->
                    updateState { it.copy(managedGroups = AsyncResult.Success(groups)) }
                    val currentId = adminContextCacheService.selectedGroupId.value
                    if ((currentId == null) && groups.isNotEmpty()) {
                        groups.first().id?.let { selectGroup(it) }
                    }
                }.onFailure { e ->
                    updateState { it.copy(managedGroups = AsyncResult.Error(e.toUserMessage(), e.javaClass.simpleName)) }
                }
            }
        }
    }

    fun selectGroup(groupId: String) {
        if (uiState.value.group?.id != groupId) {
            updateState { it.clearGroupContext().copy(isLoading = true, error = null) }
        }
        cancelJob("group_observation")
        cancelJob("refresh_members")
        adminContextCacheService.selectGroup(groupId)
        launchJob("select_group") {
            adminContextCacheService.warmUpGroup(groupId)
        }
        loadGroupAuditReport()
    }

    fun refreshData() {
        val groupId = uiState.value.group?.id ?: return
        launchJob("refresh_data") {
            adminContextCacheService.warmUpGroup(groupId, force = true)
        }
        loadGroupAuditReport()
    }

    fun loadGroupAuditReport() = reportingCoordinator.loadAuditReport(host)

    fun consolidateLedger() = groupOperationsCoordinator.consolidateLedger(host)

    fun refreshMembers() = groupOperationsCoordinator.refreshMembers(host)

    fun setTab(index: Int) { updateState { it.copy(selectedTab = index) } }

    fun downloadGroupStatement() = reportingCoordinator.downloadStatement(host)

    fun downloadGroupConstitution() = reportingCoordinator.downloadConstitution(host)

    fun calculateViability(
        goalAmount: String,
        periodMonths: String,
        actuarialSettings: ActuarialSettings = ActuarialSettings()
    ) = viabilityCoordinator.calculate(host, goalAmount, periodMonths, actuarialSettings)

    fun applyViabilityPlan() = viabilityCoordinator.apply(host)

    fun updateMessageText(text: String) = messagingCoordinator.updateText(host, text)

    fun updateNotifChannel(channel: NotifChannel) = messagingCoordinator.selectChannel(host, channel)

    fun broadcastMessage() = messagingCoordinator.broadcast(host)

    fun payPlatformFees() = platformFeeCoordinator.pay(host)

    fun executeRoscaPayout() = groupOperationsCoordinator.executeRoscaPayout(host)

    fun setActive(active: Boolean) {
        if (active) {
            loadManagedGroups()
            observeContext()
        } else {
            listOf("context_observation", "group_observation", "managed_groups_loading", "audit_report_loading").forEach {
                cancelJob(it)
            }
        }
    }

    fun clearError() { updateState { it.copy(error = null) } }
}
