package com.sanibonani.save.features.admin.presentation

import com.sanibonani.save.domain.logging.AppLogger
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.service.DevSettingsService
import com.sanibonani.save.domain.service.NetworkConnectivityService
import com.sanibonani.save.domain.service.RealtimeSyncService
import com.sanibonani.save.domain.usecase.admin.ObserveGroupHealthScoreUseCase
import com.sanibonani.save.domain.usecase.auth.GetAttributableActorIdUseCase
import com.sanibonani.save.domain.usecase.auth.GetCurrentUserRoleUseCase
import com.sanibonani.save.domain.usecase.group.GetManagedGroupsUseCase
import com.sanibonani.save.domain.usecase.group.GetGroupBusinessInsightsUseCase
import com.sanibonani.save.domain.usecase.treasury.ObserveGroupLedgerUseCase
import com.sanibonani.save.domain.utils.toUserMessage
import com.sanibonani.save.service.AdminGroupContextCacheService
import com.sanibonani.save.features.common.presentation.BaseViewModel
import com.sanibonani.save.features.common.presentation.HydrationWatchdog
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
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AdminOverviewViewModel @Inject constructor(
    private val adminContextCacheService: AdminGroupContextCacheService,
    private val getManagedGroups: GetManagedGroupsUseCase,
    private val getGroupBusinessInsights: GetGroupBusinessInsightsUseCase,
    private val observeGroupHealthScore: ObserveGroupHealthScoreUseCase,
    private val getAttributableActorId: GetAttributableActorIdUseCase,
    private val getCurrentUserRole: GetCurrentUserRoleUseCase,
    private val observeGroupLedger: ObserveGroupLedgerUseCase,
    private val devSettings: DevSettingsService,
    private val syncManager: RealtimeSyncService,
    private val viabilityCoordinator: AdminViabilityCoordinator,
    private val messagingCoordinator: AdminMessagingCoordinator,
    private val platformFeeCoordinator: AdminPlatformFeeCoordinator,
    private val reportingCoordinator: AdminReportingCoordinator,
    private val groupOperationsCoordinator: AdminGroupOperationsCoordinator,
    networkService: NetworkConnectivityService,
) : BaseViewModel<AdminOverviewUiState>(AdminOverviewUiState()) {

    val state: StateFlow<AdminOverviewUiState> = uiState

    /**
     * Gives a hydration that never answers a verdict, so the dashboard stops waiting on a read the
     * server refused instead of spinning until the admin leaves the screen. AdminDialogs renders the
     * verdict as a modal over the dashboard, and the snapshot that lands afterwards disproves it.
     */
    private val hydrationWatchdog = HydrationWatchdog(
        key = "hydration_safety_timer",
        launch = { jobKey, block -> launchJob(jobKey) { block() } },
        cancelJob = ::cancelJob,
        shouldReport = { uiState.value.group == null },
        onVerdict = { message ->
            updateState { it.copy(isLoading = false, error = message) }
        }
    )

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
        updateState { it.copy(currentUserId = getAttributableActorId()) }
        platformFeeCoordinator.applyRegistrationFee(host)
        observeContext()
        loadManagedGroups()
        platformFeeCoordinator.loadBankDetails(host)
        observeNetwork(networkService) { s, online, airplane ->
            s.copy(isSystemOnline = online, isAirplaneMode = airplane)
        }
        observeSettings()
        observeSyncMetrics()
        observeUnverifiedSections()
    }

    /**
     * Keeps the dashboard honest about which sections of the selected group the sync never
     * delivered. Driven by the sync layer's own verdict rather than by the snapshot, because a
     * pillar that arrives on a later pass clears the flag without rebuilding it.
     */
    private fun observeUnverifiedSections() {
        launchJob("unverified_sections") {
            combine(
                adminContextCacheService.selectedGroupId,
                adminContextCacheService.unverifiedSections
            ) { groupId, stale -> groupId?.let { stale[it] }.orEmpty() }
                .collect { sections -> updateState { it.copy(unverifiedSections = sections) } }
        }
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
                // The tier behind every row on screen has just been replaced, so those rows go with
                // it - and the cache drops first, because a warm-up that found the snapshot it
                // already held unchanged would not re-emit, leaving this dashboard blank for the
                // whole session. Re-warming the account's groups is what refills it.
                adminContextCacheService.invalidateForDatabaseChange()
                updateState { it.clearGroupContext() }
                loadManagedGroups()
                getAttributableActorId()?.let { adminContextCacheService.warmUpForUser(it) }
            }
        }
    }

    private fun observeContext() {
        observeGroupContext(
            key = "context_observation",
            groupIdFlow = adminContextCacheService.selectedGroupId,
            contextSource = adminContextCacheService.contexts,
        ) { context ->
            val selectedId = adminContextCacheService.selectedGroupId.value

            if (context == null) {
                if (selectedId != null) {
                    // We have a selection but no data yet. This is the same request [selectGroup]
                    // armed, not a new one, so it must not restart the bound the wait is under.
                    hydrationWatchdog.armIfIdle()
                }
                return@observeGroupContext
            }

            // A snapshot with no group row is not a dashboard, so the watchdog stays armed for it.
            val pkg = AdminGroupContext(
                group = context.group ?: return@observeGroupContext,
                members = context.members,
                notifications = context.notifications,
                messages = context.memberMessages
            )

            // The book has arrived, so the wait the watchdog was armed for is over and its verdict
            // goes with it. AdminDialogs renders this error as a modal over the dashboard, so a
            // timeout left behind blocks data that has since arrived - the read was slow, not
            // refused.
            val timeoutReported = hydrationWatchdog.settle()

            updateState { s -> s.copy(
                group = pkg.group,
                members = pkg.members,
                notifications = pkg.notifications,
                memberMessages = pkg.messages,
                feeStatus = context.feeStatus ?: AdminFeeState.DUE,
                // The wait was for the group row, and it is on screen. A pillar that stayed cached
                // keeps isFullyHydrated false for the rest of the session, so mirroring that flag
                // here pinned the "Refreshing group data" curtain over a dashboard that had an
                // answer on it. What the sync could not bring current is said by unverifiedSections.
                isLoading = false,
                error = if (timeoutReported) null else s.error
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

    private fun startGroupObservation(group: Group) {
        val groupId = group.id ?: return
        launchJob("group_observation") {
            // AI Performance optimization: Parallel warmup pattern
            // The insights run off the main dispatcher so the dashboard paints before the
            // calculation finishes.
            val members = uiState.value.members
            val insights = kotlinx.coroutines.coroutineScope {
                async(kotlinx.coroutines.Dispatchers.Default) {
                    getGroupBusinessInsights(group, members)
                }.await()
            }
            // The insight runs off the main dispatcher, so a group switch can land before it
            // returns. The new dashboard must not inherit the group this calculation was for.
            if (uiState.value.group?.id == groupId) {
                updateState { it.copy(businessInsight = insights) }
            }

            launch {
                observeGroupLedger(groupId)
                    .flowOn(kotlinx.coroutines.Dispatchers.Default)
                    .collect { res ->
                        // A failed read is not an empty book. This used to flatten through
                        // getOrDefault(emptyList()), so the verifier compared groups.balance
                        // against no rows at all and the dashboard rendered an integrity verdict
                        // for a ledger that was never read.
                        res.onSuccess { ledger ->
                            if (uiState.value.group?.id == groupId) {
                                reportingCoordinator.verify(host, group, uiState.value.members, ledger)
                            }
                        }.onFailure { e ->
                            // Keep the last verdict: the screen has no slot for a transient
                            // observation failure, and a modal on every retry would be noise.
                            AppLogger.w(
                                "AdminOverview",
                                "Ledger observation failed for $groupId; integrity verdict left as-is",
                                e
                            )
                        }
                    }
            }
        }
    }

    private fun loadManagedGroups() {
        val userId = getAttributableActorId() ?: return
        launchSafeJob("managed_groups_loading", "Failed to load managed groups") {
            updateState { it.copy(managedGroups = AsyncResult.Loading) }
            val role = getCurrentUserRole()
            val isPlatformAdmin = role == UserRole.PLATFORM_ADMIN
            getManagedGroups.observeManagedGroups(userId, adminOnly = true, isPlatformAdmin = isPlatformAdmin).collectLatest { res: Result<List<Group>> ->
                res.onSuccess { groups ->
                    updateState { it.copy(managedGroups = AsyncResult.Success(groups)) }
                    val currentId = adminContextCacheService.selectedGroupId.value
                    if ((currentId == null) && groups.isNotEmpty()) {
                        groups.first().id?.let { selectGroup(it) }
                    }
                }.onFailure { e ->
                    updateState { it.copy(managedGroups = AsyncResult.Error(e.toUserMessage(), e.javaClass.simpleName)) }
                    // No composable renders that verdict, so the refusal has to reach the surface
                    // the dashboard already listens to: otherwise the group switcher simply never
                    // appears and an offline admin cannot tell it from having one group.
                    handleError(e, "Could not load the groups you administer.")
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
        // A refresh, fee read, or audit started for the group being left. Their results are keyed
        // by that group, and letting them finish writes its loader, its dues, or its report onto
        // the group this selection is opening.
        cancelJob("refresh_data")
        cancelJob("platform_fees_loading")
        cancelJob("audit_report_loading")
        // Selecting is what warms the group, and it warms it fresh: a second, non-forced warmup
        // here used to race the forced one and re-hydrate the same group twice.
        adminContextCacheService.selectGroup(groupId)
        // This is the one place that knows a book was asked for and is not on screen yet, so the
        // bound on the wait is set here rather than left to the collector.
        hydrationWatchdog.arm()
        loadGroupAuditReport()
    }

    fun refreshData() {
        val groupId = uiState.value.group?.id
            ?: adminContextCacheService.selectedGroupId.value
            ?: return
        updateState { it.copy(isLoading = true, error = null) }
        launchJob("refresh_data") {
            val result = adminContextCacheService.refreshGroup(groupId)
            if (adminContextCacheService.selectedGroupId.value != groupId) return@launchJob
            result.onFailure { e -> updateState { state -> state.copy(error = e.toUserMessage()) } }
            // A refresh that returns before the snapshot is on screen must not drop the loader
            // for a group that has not rendered yet.
            if (uiState.value.group?.id == groupId) {
                updateState { it.copy(isLoading = false) }
            }
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
            // A dashboard left within the bound is no longer waiting on the book, so the wait must
            // not report a timeout to a screen nobody is looking at.
            hydrationWatchdog.cancel()
            listOf(
                "context_observation",
                "group_observation",
                "managed_groups_loading",
                "audit_report_loading",
                "refresh_data",
                "platform_fees_loading"
            ).forEach { cancelJob(it) }
        }
    }

    fun clearError() { updateState { it.copy(error = null) } }
}
