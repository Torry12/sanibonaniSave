package com.sanibonani.save.features.admin.presentation

import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.equity.GroupFinancialRepository
import com.sanibonani.save.domain.repository.treasury.LedgerRepository
import com.sanibonani.save.domain.repository.admin.*
import com.sanibonani.save.domain.repository.system.*
import com.sanibonani.save.domain.repository.auth.SupabaseRepository
import com.sanibonani.save.domain.repository.member.MemberRepository
import com.sanibonani.save.domain.usecase.group.GetManagedGroupsUseCase
import com.sanibonani.save.domain.usecase.actuarial.ApplyViabilityPlanUseCase
import com.sanibonani.save.domain.usecase.system.SendNotificationUseCase
import com.sanibonani.save.domain.usecase.group.GetGroupBusinessInsightsUseCase
import com.sanibonani.save.domain.usecase.disbursement.ExecuteRoscaPayoutUseCase
import com.sanibonani.save.domain.usecase.payment.ProcessGroupPlatformFeePaymentUseCase
import com.sanibonani.save.domain.usecase.actuarial.CalculateViabilityUseCase
import com.sanibonani.save.domain.usecase.actuarial.ViabilityUtils
import com.sanibonani.save.domain.service.*
import com.sanibonani.save.data.utils.DevSettings
import com.sanibonani.save.domain.logging.AppLogger
import com.sanibonani.save.data.sync.RealtimeSyncManager
import com.sanibonani.save.service.AdminGroupContextCacheService
import com.sanibonani.save.domain.utils.toUserMessage
import com.sanibonani.save.features.common.presentation.BaseViewModel
import com.sanibonani.save.features.common.presentation.UIEvent
import com.sanibonani.save.features.admin.presentation.state.AdminGroupContext
import com.sanibonani.save.features.admin.presentation.state.AdminOverviewUiState
import dagger.hilt.android.lifecycle.HiltViewModel
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
    private val auditRepo: AuditRepository,
    private val getGroupBusinessInsightsUseCase: GetGroupBusinessInsightsUseCase,
    private val healthScoreRepo: HealthScoreRepository,
    private val calculateViabilityUseCase: CalculateViabilityUseCase,
    private val applyViabilityPlanUseCase: ApplyViabilityPlanUseCase,
    private val sendNotificationUseCase: SendNotificationUseCase,
    private val supabaseRepo: SupabaseRepository,
    private val memberRepo: MemberRepository,
    private val ledgerRepo: LedgerRepository,
    private val exportRepo: ExportRepository,
    private val verificationService: DataVerificationService,
    private val executeRoscaPayoutUseCase: ExecuteRoscaPayoutUseCase,
    private val processPlatformFeePaymentUseCase: ProcessGroupPlatformFeePaymentUseCase,
    private val platformRepo: PlatformRepository,
    private val financialRepo: GroupFinancialRepository,
    private val devSettings: DevSettings,
    private val syncManager: RealtimeSyncManager,
    private val feeService: PlatformFeeService,
    networkService: NetworkConnectivityService,
) : BaseViewModel<AdminOverviewUiState>(AdminOverviewUiState()) {

    val state: StateFlow<AdminOverviewUiState> = uiState

    init {
        updateState { it.copy(
            currentUserId = supabaseRepo.currentUserId,
            platformRegistrationFee = feeService.getRegistrationFee()
        ) }
        observeContext()
        loadManagedGroups()
        loadPlatformBankDetails()
        observeNetwork(networkService) { s, online, airplane ->
            s.copy(isSystemOnline = online, isAirplaneMode = airplane)
        }
        observeSettings()
        observeSyncMetrics()
    }

    private fun loadPlatformBankDetails() {
        launchSafeJob("platform_bank_loading", "Failed to load platform settings") {
            platformRepo.getPlatformSettings().onSuccess { config ->
                updateState { it.copy(platformBankDetails = config) }
            }
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
                    } else {
                        updateState { it.copy(groupContext = AsyncResult.Uninitialized) }
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
                    val groupId = pkg.group.id
            if (groupId != null) {
                loadOutstandingPlatformFees(groupId)
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
                        val verification = verificationService.verifyGroupAdminData(group, uiState.value.members, ledger)
                        updateState { it.copy(verificationResult = verification) }
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

    fun loadGroupAuditReport() {
        val groupId = uiState.value.group?.id ?: return
        AppLogger.i("AdminOverview", "Loading Group Audit Report for ID: $groupId")
        launchSafeJob("audit_report_loading", "Failed to load audit report") {
            updateState { it.copy(groupAuditReport = AsyncResult.Loading, isLoadingAuditReport = true) }
            auditRepo.triggerGroupAudit(groupId)
                .onSuccess { report ->
                    updateState { it.copy(groupAuditReport = AsyncResult.Success(report), isLoadingAuditReport = false) }
                }
                .onFailure { e ->
                    updateState { it.copy(groupAuditReport = AsyncResult.Error(e.toUserMessage(), e.javaClass.simpleName), isLoadingAuditReport = false) }
                }
        }
    }

    fun consolidateLedger() {
        val groupId = uiState.value.group?.id ?: return
        launchSafeJob("ledger_consolidation", "Failed to consolidate ledger") {
            updateState { it.copy(isLoading = true) }
            withLoading {
                ledgerRepo.consolidateGroupLedger(groupId)
                    .onSuccess {
                        refreshMembers()
                        updateState { it.copy(isLoading = false, successMessage = "Audit trail repaired.") }
                    }
                    .onFailure { e ->
                        updateState { it.copy(isLoading = false) }
                        handleError(e)
                    }
            }
        }
    }

    fun refreshMembers() {
        val groupId = uiState.value.group?.id ?: return
        launchSafeJob("refresh_members", "Failed to sync members") {
            updateState { it.copy(isLoading = true) }
            withLoading {
                memberRepo.syncGroupMembers(groupId)
                    .onSuccess {
                        adminContextCacheService.warmUpGroup(groupId, force = true)
                        updateState { it.copy(isLoading = false) }
                    }
                    .onFailure { e ->
                        updateState { it.copy(isLoading = false) }
                        handleError(e)
                    }
            }
        }
    }

    fun setTab(index: Int) { updateState { it.copy(selectedTab = index) } }

    fun downloadGroupStatement() {
        val group = uiState.value.group ?: return
        val groupId = group.id ?: return
        launchSafeJob("download_statement", "Failed to download statement") {
            updateState { it.copy(isLoading = true) }
            ledgerRepo.getGroupLedger(groupId).onSuccess { entries ->
                exportRepo.exportLedgerToPdf(group, entries).onSuccess { file ->
                    updateState { it.copy(isLoading = false) }
                    sendEvent(UIEvent.OpenFile(file, "application/pdf", "Group Statement"))
                }.onFailure { e ->
                    updateState { it.copy(isLoading = false) }
                    handleError(e)
                }
            }.onFailure { e ->
                updateState { it.copy(isLoading = false) }
                handleError(e)
            }
        }
    }

    fun downloadGroupConstitution() {
        val group = uiState.value.group ?: return
        launchSafeJob("download_constitution", "Failed to download constitution") {
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

    fun calculateViability(
        goalAmount: String,
        periodMonths: String,
        actuarialSettings: ActuarialSettings = ActuarialSettings()
    ) {
        val group = uiState.value.group ?: return
        val groupId = group.id ?: return
        val months = periodMonths.toIntOrNull()?.takeIf { it > 0 } ?: ViabilityUtils.computeDefaultPeriod(group)

        val parsedAmount = goalAmount.takeIf { it.isNotBlank() && it.toBigDecimalOrNull() != null }?.let { Money(it) }
        val effectiveGoal = if (parsedAmount != null && parsedAmount.isPositive) {
            parsedAmount
        } else {
            ViabilityUtils.computeDefaultGoal(group)
        }

        launchSafeJob("calculate_viability", "Failed to calculate viability") {
            updateState { it.copy(isCalculatingViability = true) }
            withLoading {
                calculateViabilityUseCase(groupId, effectiveGoal, months, actuarialSettings).onSuccess { plan ->
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

        launchSafeJob("apply_viability", "Failed to apply viability plan") {
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

        launchSafeJob("broadcast_message", "Failed to send message") {
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

    private fun loadOutstandingPlatformFees(groupId: String) {
        launchJob("platform_fees_loading") {
            financialRepo.getOutstandingPlatformFees(groupId)
                .onSuccess { fees -> updateState { it.copy(outstandingPlatformFees = fees) } }
        }
    }

    fun payPlatformFees() {
        val groupId = uiState.value.group?.id ?: return
        launchSafeJob("pay_platform_fees", "Failed to pay platform fees") {
            updateState { it.copy(isPayingPlatformFee = true) }
            processPlatformFeePaymentUseCase(groupId)
                .onSuccess {
                    updateState { it.copy(isPayingPlatformFee = false, successMessage = "Platform fees paid successfully.") }
                    loadOutstandingPlatformFees(groupId)
                    adminContextCacheService.warmUpGroup(groupId, force = true)
                }
                .onFailure { e ->
                    updateState { it.copy(isPayingPlatformFee = false) }
                    handleError(e)
                }
        }
    }

    fun executeRoscaPayout() {
        val groupId = uiState.value.group?.id ?: return
        launchSafeJob("execute_rosca_payout", "Failed to process ROSCA payout") {
            updateState { it.copy(isLoading = true) }
            executeRoscaPayoutUseCase(groupId).onSuccess { msg ->
                updateState { it.copy(isLoading = false, successMessage = "ROSCA Payout Processed: $msg") }
                adminContextCacheService.warmUpGroup(groupId)
            }.onFailure { e ->
                updateState { it.copy(isLoading = false) }
                handleError(e)
            }
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
