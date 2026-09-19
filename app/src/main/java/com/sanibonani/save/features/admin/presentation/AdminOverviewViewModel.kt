package com.sanibonani.save.features.admin.presentation

import androidx.lifecycle.viewModelScope
import com.sanibonani.save.domain.logging.AppLogger
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.dynamics.*
import com.sanibonani.save.domain.repository.treasury.*
import com.sanibonani.save.domain.repository.equity.*
import com.sanibonani.save.domain.repository.system.*
import com.sanibonani.save.domain.usecase.dynamics.GetManagedGroupsUseCase
import com.sanibonani.save.domain.usecase.dynamics.ApplyViabilityPlanUseCase
import com.sanibonani.save.domain.usecase.dynamics.SendNotificationUseCase
import com.sanibonani.save.domain.usecase.groups.GetGroupBusinessInsightsUseCase
import com.sanibonani.save.domain.usecase.dynamics.ExecuteRoscaPayoutUseCase
import com.sanibonani.save.domain.usecase.dynamics.CalculateViabilityUseCase
import com.sanibonani.save.service.AdminGroupContextCacheService
import com.sanibonani.save.domain.utils.toUserMessage
import com.sanibonani.save.viewmodel.BaseViewModel
import com.sanibonani.save.viewmodel.state.UIEvent
import com.sanibonani.save.features.admin.presentation.state.AdminGroupContext
import com.sanibonani.save.features.admin.presentation.state.AdminOverviewUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AdminOverviewViewModel @Inject constructor(
    private val adminContextCacheService: AdminGroupContextCacheService,
    private val getManagedGroupsUseCase: GetManagedGroupsUseCase,
    private val auditRepo: AuditRepository,
    private val getGroupBusinessInsightsUseCase: GetGroupBusinessInsightsUseCase,
    private val healthScoreRepo: HealthScoreRepository,
    private val calculateViabilityUseCase: CalculateViabilityUseCase,
    private val applyViabilityPlanUseCase: ApplyViabilityPlanUseCase,
    private val sendNotificationUseCase: SendNotificationUseCase,
    private val supabaseRepo: SupabaseRepository,
    private val groupRepo: GroupRepository,
    private val memberRepo: MemberRepository,
    private val financialRepo: GroupFinancialRepository,
    private val ledgerRepo: LedgerRepository,
    private val exportRepo: ExportRepository,
    private val networkService: com.sanibonani.save.domain.service.NetworkConnectivityService,
    private val verificationService: com.sanibonani.save.domain.service.DataVerificationService,
    private val executeRoscaPayoutUseCase: ExecuteRoscaPayoutUseCase,
    private val devSettings: com.sanibonani.save.domain.utils.DevSettings,
    private val syncManager: com.sanibonani.save.data.sync.RealtimeSyncManager
) : BaseViewModel<AdminOverviewUiState>(AdminOverviewUiState()) {

    val state: StateFlow<AdminOverviewUiState> = uiState

    init {
        updateState { it.copy(currentUserId = supabaseRepo.currentUserId) }
        observeContext()
        loadManagedGroups()
        observeNetwork()
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

    private fun observeNetwork() {
        launchJob("network_observation") {
            launch {
                networkService.isOnline.collect { online ->
                    updateState { it.copy(isSystemOnline = online) }
                }
            }
            launch {
                networkService.isAirplaneModeActive.collect { active ->
                    updateState { it.copy(isAirplaneMode = active) }
                }
            }
        }
    }

    private fun observeContext() {
        launchJob("context_observation") {
            observeGroupContext(
                groupIdFlow = adminContextCacheService.selectedGroupId,
                contextSource = adminContextCacheService.contexts
            ) { context ->
                if (context == null) {
                    updateState { it.copy(groupContext = AsyncResult.Uninitialized) }
                    return@observeGroupContext
                }

                val pkg = AdminGroupContext(
                    group = context.group ?: return@observeGroupContext,
                    members = context.members,
                    metrics = context.metrics ?: ActuarialMetrics(),
                    notifications = context.notifications,
                    messages = context.memberMessages
                )

                updateState { it.copy(
                    groupContext = if (context.isFullyHydrated) AsyncResult.Success(pkg) else AsyncResult.Loading,
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
                }
            }.join()
        }
    }

    private fun startGroupObservation(group: Group) {
        val groupId = group.id ?: return
        launchJob("group_observation") {
            launch(kotlinx.coroutines.Dispatchers.Default) {
                val members = uiState.value.members
                val insights = getGroupBusinessInsightsUseCase(group, members)
                updateState { it.copy(businessInsight = insights) }
            }

            launch {
                healthScoreRepo.observeGroupHealthScore(groupId).collect { res ->
                    res.onSuccess { score ->
                        updateState { it.copy(healthScore = score) }
                    }
                }
            }

            launch {
                ledgerRepo.observeGroupLedger(groupId)
                    .flowOn(kotlinx.coroutines.Dispatchers.Default)
                    .collect { res ->
                        val ledger = res.getOrDefault(emptyList())
                        val verification = verificationService.verifyGroupAdminData(group, uiState.value.members, ledger)
                        updateState { it.copy(verificationResult = verification) }
                    }
            }
        }
    }

    private fun loadManagedGroups() {
        val userId = supabaseRepo.currentUserId ?: return
        launchJob("managed_groups_loading") {
            updateState { it.copy(managedGroups = AsyncResult.Loading) }
            val role = supabaseRepo.getUserRole()
            val isPlatformAdmin = role == UserRole.PLATFORM_ADMIN
            getManagedGroupsUseCase.observeManagedGroups(userId, adminOnly = true, isPlatformAdmin = isPlatformAdmin).collectLatest { res ->
                res.onSuccess { groups ->
                    updateState { it.copy(managedGroups = AsyncResult.Success(groups)) }
                    val currentId = adminContextCacheService.selectedGroupId.value
                    if (currentId == null && groups.isNotEmpty()) {
                        groups.first().id?.let { selectGroup(it) }
                    }
                }.onFailure { e ->
                    updateState { it.copy(managedGroups = AsyncResult.Error(e.toUserMessage(), e)) }
                }
            }
        }
    }

    fun selectGroup(groupId: String) {
        if (uiState.value.group?.id != groupId) {
            updateState { it.copy(group = null, members = emptyList(), isLoading = true, error = null) }
        }
        cancelJob("group_observation")
        cancelJob("refresh_members")
        adminContextCacheService.selectGroup(groupId)
        adminContextCacheService.warmUpGroup(groupId)
    }

    fun loadGroupAuditReport() {
        val groupId = uiState.value.group?.id ?: return
        launchJob("audit_report_loading") {
            updateState { it.copy(groupAuditReport = AsyncResult.Loading) }
            auditRepo.observeGroupAuditReport(groupId).collect { result ->
                result.onSuccess { report ->
                    updateState { it.copy(groupAuditReport = AsyncResult.Success(report), isLoadingAuditReport = false) }
                }.onFailure { e ->
                    updateState { it.copy(groupAuditReport = AsyncResult.Error(e.toUserMessage(), e), isLoadingAuditReport = false) }
                }
            }
        }
    }

    fun consolidateLedger() {
        val groupId = uiState.value.group?.id ?: return
        launchJob("ledger_consolidation") {
            updateState { it.copy(isLoading = true) }
            withLoading {
                ledgerRepo.consolidateGroupLedger(groupId).onSuccess {
                    refreshMembers()
                    updateState { it.copy(isLoading = false, successMessage = "Audit trail repaired.") }
                }
            }
        }
    }

    fun refreshMembers() {
        val groupId = uiState.value.group?.id ?: return
        launchJob("refresh_members") {
            updateState { it.copy(isLoading = true) }
            withLoading {
                memberRepo.syncGroupMembers(groupId, activeOnly = false).onSuccess {
                    adminContextCacheService.warmUpGroup(groupId, force = true)
                }
            }
        }
    }

    fun setTab(index: Int) { updateState { it.copy(selectedTab = index) } }

    fun downloadGroupStatement() {
        val group = uiState.value.group ?: return
        launchJob("download_statement") {
            updateState { it.copy(isLoading = true) }
            ledgerRepo.getGroupLedger(group.id!!).onSuccess { entries ->
                exportRepo.exportLedgerToPdf(group, entries).onSuccess { file ->
                    updateState { it.copy(isLoading = false) }
                    sendEvent(UIEvent.OpenFile(file, "application/pdf", "Group Statement"))
                }
            }
        }
    }

    fun downloadGroupConstitution() {
        val group = uiState.value.group ?: return
        launchJob("download_constitution") {
            updateState { it.copy(isLoading = true) }
            withLoading {
                exportRepo.exportGroupConstitution(group).onSuccess { file ->
                    updateState { it.copy(isLoading = false) }
                    sendEvent(UIEvent.OpenFile(file, "application/pdf", "Group Constitution"))
                }.onFailure { e ->
                    updateState { it.copy(isLoading = false) }
                    handleError(e)
                }
            }
        }
    }

    fun calculateViability(goalAmount: String, periodMonths: String) {
        val groupId = uiState.value.group?.id ?: return
        val months = periodMonths.toIntOrNull() ?: return

        launchJob("calculate_viability") {
            updateState { it.copy(isCalculatingViability = true) }
            withLoading {
                calculateViabilityUseCase(groupId, Money(goalAmount.ifBlank { "0.00" }), months).onSuccess { plan ->
                    updateState { it.copy(isCalculatingViability = false, viabilityPlan = plan) }
                }.onFailure { e ->
                    updateState { it.copy(isCalculatingViability = false) }
                    handleError(e)
                }
            }
        }
    }

    fun applyViabilityPlan() {
        val groupId = uiState.value.group?.id ?: return
        val plan = uiState.value.viabilityPlan ?: return

        launchJob("apply_viability") {
            updateState { it.copy(isLoading = true) }
            withLoading {
                applyViabilityPlanUseCase(groupId, plan).onSuccess {
                    updateState { it.copy(isLoading = false, viabilityPlan = null) }
                }.onFailure { e ->
                    updateState { it.copy(isLoading = false) }
                    handleError(e)
                }
            }
        }
    }

    fun updateMessageText(text: String) { updateState { it.copy(messageText = text) } }

    fun updateNotifChannel(channel: NotifChannel) { updateState { it.copy(selectedNotifChannel = channel) } }

    fun broadcastMessage() {
        val groupId = uiState.value.group?.id ?: return
        val message = uiState.value.messageText
        val channel = uiState.value.selectedNotifChannel
        if (message.isBlank()) return

        launchJob("broadcast_message") {
            updateState { it.copy(isSendingMessage = true, messageSentSuccess = false) }
            withLoading {
                sendNotificationUseCase(groupId, message, memberId = null, channel = channel).onSuccess {
                    updateState { it.copy(isSendingMessage = false, messageSentSuccess = true, messageText = "") }
                }.onFailure { e ->
                    updateState { it.copy(isSendingMessage = false) }
                    handleError(e)
                }
            }
        }
    }

    fun executeRoscaPayout() {
        val groupId = uiState.value.group?.id ?: return
        launchJob("execute_rosca_payout") {
            updateState { it.copy(isLoading = true) }
            executeRoscaPayoutUseCase(groupId).onSuccess { msg ->
                updateState { it.copy(isLoading = false, successMessage = "ROSCA Payout Processed: $msg") }
                adminContextCacheService.warmUpGroup(groupId)
            }.onFailure { handleError(it) }
        }
    }

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
