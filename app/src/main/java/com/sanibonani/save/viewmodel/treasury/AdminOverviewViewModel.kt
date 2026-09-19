package com.sanibonani.save.viewmodel.treasury

import androidx.lifecycle.viewModelScope
import com.sanibonani.save.domain.logging.AppLogger
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.*
import com.sanibonani.save.domain.repository.dynamics.HealthScoreRepository
import com.sanibonani.save.domain.repository.treasury.GroupFinancialRepository
import com.sanibonani.save.domain.repository.treasury.LedgerRepository
import com.sanibonani.save.domain.usecase.*
import com.sanibonani.save.domain.usecase.groups.GetGroupBusinessInsightsUseCase
import com.sanibonani.save.domain.usecase.dynamics.ExecuteRoscaPayoutUseCase
import com.sanibonani.save.domain.usecase.dynamics.CalculateViabilityUseCase
import com.sanibonani.save.service.AdminGroupContextCacheService
import com.sanibonani.save.domain.utils.toUserMessage
import com.sanibonani.save.viewmodel.BaseViewModel
import com.sanibonani.save.viewmodel.state.UIEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AdminOverviewUiState(
    val group: Group? = null,
    val members: List<Member> = emptyList(),
    val managedGroups: List<Group> = emptyList(),
    val metrics: ActuarialMetrics = ActuarialMetrics(),
    val businessInsight: GetGroupBusinessInsightsUseCase.GroupBusinessInsight = GetGroupBusinessInsightsUseCase.GroupBusinessInsight.Empty,
    val healthScore: GroupHealthScore? = null,
    val feeStatus: AdminFeeState = AdminFeeState.DUE,
    val daysOverdue: Int = 0,
    val restoreRequested: Boolean = false,
    val viabilityPlan: ViabilityPlan? = null,
    val isCalculatingViability: Boolean = false,
    val notifications: List<AppNotification> = emptyList(),
    val memberMessages: List<AppNotification> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
    val selectedTab: Int = 0,
    val verificationResult: VerificationResult = VerificationResult.Valid,
    val isSystemOnline: Boolean = true,
    val isAirplaneMode: Boolean = false,

    // Messaging
    val messageText: String = "",
    val isSendingMessage: Boolean = false,
    val messageSentSuccess: Boolean = false,
    val selectedNotifChannel: NotifChannel = NotifChannel.BOTH,
    val syncMetrics: Map<String, com.sanibonani.save.data.sync.RealtimeSyncManager.SyncStatus> = emptyMap(),

    // Support / Impersonation
    val isSupportMode: Boolean = false,
    val isImpersonationBannerDismissed: Boolean = false,
    val currentUserId: String? = null,

    // Audit Report
    val groupAuditReport: GroupAuditReport? = null,
    val isLoadingAuditReport: Boolean = false
)

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
    private val supabaseRepo: com.sanibonani.save.domain.repository.SupabaseRepository,
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

    private var contextObservationJob: Job? = null
    private var integrityJob: Job? = null
    private var refreshMembersJob: Job? = null
    private var selectGroupJob: Job? = null
    private var auditReportJob: Job? = null

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
        viewModelScope.launch {
            syncManager.syncMetrics.collect { metrics ->
                updateState { it.copy(syncMetrics = metrics) }
            }
        }
    }

    private fun observeSettings() {
        viewModelScope.launch {
            devSettings.settingsChanged.collect {
                updateState { it.copy(group = null, members = emptyList(), managedGroups = emptyList()) }
                loadManagedGroups()
            }
        }
    }

    private fun observeNetwork() {
        viewModelScope.launch {
            networkService.isOnline.collect { online ->
                updateState { it.copy(isSystemOnline = online) }
            }
        }
        viewModelScope.launch {
            networkService.isAirplaneModeActive.collect { active ->
                updateState { it.copy(isAirplaneMode = active) }
            }
        }
    }

    private var lastRepairTimeMillis = mutableMapOf<String, Long>()

    private fun observeContext() {
        contextObservationJob?.cancel()
        contextObservationJob = observeGroupContext(
            groupIdFlow = adminContextCacheService.selectedGroupId,
            contextSource = adminContextCacheService.contexts
        ) { context ->
            if (context == null) return@observeGroupContext

            updateState { it.copy(
                group = context.group,
                members = context.members,
                metrics = context.metrics ?: ActuarialMetrics(),
                notifications = context.notifications,
                memberMessages = context.memberMessages,
                feeStatus = context.feeStatus ?: AdminFeeState.DUE,
                isLoading = !context.isFullyHydrated,
                error = if (context.isFullyHydrated && context.group == null) "Could not load group info." else it.error
            ) }

            if (context.isFullyHydrated && context.group != null) {
                startGroupObservation(context.group)
            }
        }
    }

    private fun startGroupObservation(group: Group) {
        val groupId = group.id ?: return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            val members = uiState.value.members
            val insights = getGroupBusinessInsightsUseCase(group, members)
            updateState { it.copy(businessInsight = insights) }

            healthScoreRepo.observeGroupHealthScore(groupId).collect { res ->
                res.onSuccess { score ->
                    updateState { it.copy(healthScore = score) }
                }
            }
        }

        integrityJob?.cancel()
        integrityJob = viewModelScope.launch {
            ledgerRepo.observeGroupLedger(groupId)
                .flowOn(kotlinx.coroutines.Dispatchers.Default)
                .collect { res ->
                    val ledger = res.getOrDefault(emptyList())
                    val verification = verificationService.verifyGroupAdminData(group, uiState.value.members, ledger)
                    updateState { it.copy(verificationResult = verification) }
                }
        }
    }

    private fun loadManagedGroups() {
        val userId = supabaseRepo.currentUserId ?: return
        viewModelScope.launch {
            val role = supabaseRepo.getUserRole()
            val isPlatformAdmin = role == UserRole.PLATFORM_ADMIN
            getManagedGroupsUseCase.observeManagedGroups(userId, adminOnly = true, isPlatformAdmin = isPlatformAdmin).collectLatest { res ->
                res.onSuccess { groups ->
                    updateState { it.copy(managedGroups = groups) }
                    val currentId = adminContextCacheService.selectedGroupId.value
                    if (currentId == null && groups.isNotEmpty()) {
                        groups.first().id?.let { selectGroup(it) }
                    }
                }
            }
        }
    }

    fun selectGroup(groupId: String) {
        if (uiState.value.group?.id != groupId) {
            updateState { it.copy(group = null, members = emptyList(), isLoading = true, error = null) }
        }
        integrityJob?.cancel()
        refreshMembersJob?.cancel()
        adminContextCacheService.selectGroup(groupId)
        adminContextCacheService.warmUpGroup(groupId)
    }

    fun loadGroupAuditReport() {
        val groupId = uiState.value.group?.id ?: return
        auditReportJob?.cancel()
        auditReportJob = auditRepo.observeGroupAuditReport(groupId).onEach { result ->
            result.onSuccess { report -> updateState { it.copy(groupAuditReport = report, isLoadingAuditReport = false) } }
        }.launchIn(viewModelScope)
        updateState { it.copy(isLoadingAuditReport = true) }
    }

    fun consolidateLedger() {
        val groupId = uiState.value.group?.id ?: return
        viewModelScope.launch {
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
        refreshMembersJob?.cancel()
        refreshMembersJob = viewModelScope.launch {
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
        viewModelScope.launch {
            updateState { it.copy(isLoading = true) }
            ledgerRepo.getGroupLedger(group.id!!).onSuccess { entries ->
                exportRepo.exportLedgerToPdf(group, entries).onSuccess { file ->
                    updateState { it.copy(isLoading = false) }
                    sendEvent(UIEvent.OpenFile(file, "application/pdf", "Group Statement"))
                }
            }
        }
    }

    fun executeRoscaPayout() {
        val groupId = uiState.value.group?.id ?: return
        viewModelScope.launch {
            updateState { it.copy(isLoading = true) }
            executeRoscaPayoutUseCase(groupId).onSuccess { msg ->
                updateState { it.copy(isLoading = false, successMessage = "ROSCA Payout Processed: $msg") }
                adminContextCacheService.warmUpGroup(groupId)
            }
        }
    }

    fun setActive(active: Boolean) {
        if (active) loadManagedGroups()
    }
}
