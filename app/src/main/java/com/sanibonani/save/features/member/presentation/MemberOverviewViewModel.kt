package com.sanibonani.save.features.member.presentation

import androidx.lifecycle.viewModelScope
import com.sanibonani.save.analytics.AppAnalytics
import com.sanibonani.save.domain.logging.AppLogger
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.utils.PaymentCalculation
import com.sanibonani.save.domain.repository.equity.*
import com.sanibonani.save.domain.repository.credit.*
import com.sanibonani.save.domain.repository.disbursement.*
import com.sanibonani.save.domain.repository.dynamics.*
import com.sanibonani.save.domain.repository.system.*
import com.sanibonani.save.domain.repository.treasury.*
import com.sanibonani.save.domain.service.MemberActuarialService
import com.sanibonani.save.domain.usecase.disbursement.ProcessDisbursementUseCase
import com.sanibonani.save.domain.usecase.groups.GetGroupBusinessInsightsUseCase
import com.sanibonani.save.service.MemberGroupContextCacheService
import com.sanibonani.save.viewmodel.BaseViewModel
import com.sanibonani.save.features.member.presentation.state.MemberContextPackage
import com.sanibonani.save.features.member.presentation.state.MemberUiState
import com.sanibonani.save.features.member.presentation.state.MemberEvent
import com.sanibonani.save.viewmodel.state.UIEvent
import com.sanibonani.save.domain.utils.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class MemberOverviewViewModel @Inject constructor(
    private val memberContextCacheService: MemberGroupContextCacheService,
    private val memberRepo: MemberRepository,
    private val contributionRepo: ContributionRepository,
    private val groupRepo: GroupRepository,
    private val ledgerRepo: LedgerRepository,
    private val exportRepo: ExportRepository,
    private val supabaseRepo: SupabaseRepository,
    private val loanRepo: LoanRepository,
    private val auditRepo: AuditRepository,
    private val notifRepo: NotificationRepository,
    private val getGroupBusinessInsightsUseCase: GetGroupBusinessInsightsUseCase,
    private val networkService: com.sanibonani.save.domain.service.NetworkConnectivityService
) : BaseViewModel<MemberUiState>(MemberUiState()) {

    val state: StateFlow<MemberUiState> = uiState

    init {
        observeNetwork()
        loadMemberships()
    }

    private fun observeNetwork() {
        launchJob("network_observation") {
            launch { networkService.isOnline.collect { online -> updateState { it.copy(isSystemOnline = online) } } }
            launch { networkService.isAirplaneModeActive.collect { active -> updateState { it.copy(isAirplaneMode = active) } } }
        }
    }

    private fun observeContext() {
        launchJob("context_observation") {
            observeGroupContext(
                groupIdFlow = uiState.map { it.selectedGroupId },
                contextSource = memberContextCacheService.contexts
            ) { context ->
                if (context == null) {
                    updateState { it.copy(memberContext = AsyncResult.Uninitialized) }
                    return@observeGroupContext
                }

                val memberId = context.member?.id
                val groupId = context.group?.id

                if (context.isFullyHydrated && context.member != null && context.group != null) {
                    val pkg = MemberContextPackage(
                        member = context.member,
                        group = context.group,
                        contributions = context.contributions,
                        beneficiaries = context.beneficiaries,
                        loans = context.loans,
                        repayments = context.loanRepayments,
                        calculation = context.calculation ?: PaymentCalculation.EMPTY,
                        notifications = context.notifications,
                        messages = context.messages
                    )

                    updateState { it.copy(
                        memberContext = AsyncResult.Success(pkg),
                        member = pkg.member,
                        group = pkg.group,
                        contributions = pkg.contributions,
                        beneficiaries = pkg.beneficiaries,
                        loans = pkg.loans,
                        loanRepayments = pkg.repayments,
                        paymentCalculation = pkg.calculation,
                        notifications = pkg.notifications,
                        messages = pkg.messages,
                        isLoading = false,
                        cacheLastSyncByGroup = it.cacheLastSyncByGroup + (groupId.orEmpty() to context.lastUpdatedMillis),
                    ) }

                    startLiveObservation(memberId!!, groupId!!)
                    loadInsights(pkg.group)
                } else {
                    updateState { it.copy(memberContext = AsyncResult.Loading, isLoading = true) }
                }
            }.join()
        }
    }

    private fun loadInsights(group: Group) {
        launchJob("insights_loading") {
            memberRepo.getGroupMembers(group.id!!).collectLatest { res ->
                res.onSuccess { members ->
                    val insights = withContext(kotlinx.coroutines.Dispatchers.Default) {
                        getGroupBusinessInsightsUseCase(group, members)
                    }
                    updateState { it.copy(businessInsight = insights, allMembers = members) }
                }
            }
        }
    }

    private fun startLiveObservation(memberId: String, groupId: String) {
        launchJob("live_observation") {
            coroutineScope {
                launch {
                    groupRepo.observeGroup(groupId).collect { res ->
                        res.onSuccess { g -> updateState { it.copy(group = g) } }
                    }
                }
                launch {
                    memberRepo.observeMemberById(memberId).collect { res ->
                        res.onSuccess { m -> if (m != null) updateState { it.copy(member = m) } }
                    }
                }
                launch {
                    contributionRepo.observeMemberContributions(memberId, groupId).collect { res ->
                        res.onSuccess { list -> updateState { it.copy(contributions = list) } }
                    }
                }
                launch {
                    loanRepo.getMemberLoans(memberId).collect { res ->
                        res.onSuccess { list -> updateState { it.copy(loans = list) } }
                    }
                }
                launch {
                    ledgerRepo.observeMemberLedger(memberId).collect { res ->
                        res.onSuccess { list -> updateState { it.copy(memberLedger = list) } }
                    }
                }
            }
        }
    }

    private fun loadMemberships() {
        val userId = supabaseRepo.currentUserId ?: return
        launchJob("membership_observation") {
            memberRepo.observeMemberships(userId).collect { res ->
                res.onSuccess { memberships ->
                    updateState { it.copy(memberships = memberships) }
                    if (uiState.value.selectedGroupId == null && memberships.isNotEmpty()) {
                        selectGroup(memberships.first().groupId)
                    }
                }
            }
        }
    }

    fun selectGroup(groupId: String) {
        launchJob("select_group") {
            updateState { it.copy(selectedGroupId = groupId, isLoading = true) }
            memberContextCacheService.warmUpForUser(supabaseRepo.currentUserId!!)
        }
    }

    fun beginImpersonation(memberId: String, groupId: String) {
        launchJob("impersonation") {
            updateState { it.copy(isImpersonating = true, isLoading = true) }
            memberContextCacheService.impersonateMember(memberId, groupId).onSuccess { context ->
                memberContextCacheService.updateContext(groupId) { context }
            }
        }
    }

    fun clearImpersonation(onComplete: () -> Unit = {}) {
        updateState { it.copy(isImpersonating = false, member = null) }
        onComplete()
    }

    fun switchGroup(groupId: String) {
        selectGroup(groupId)
    }

    fun refreshData() {
        val userId = supabaseRepo.currentUserId ?: return
        launchJob("refresh_data") {
            memberContextCacheService.warmUpForUser(userId)
        }
    }

    fun selectTab(index: Int) { updateState { it.copy(selectedTab = index) } }

    fun updateMessageText(text: String) { updateState { it.copy(messageText = text) } }

    fun sendMessageToAdmin() {
        val s = uiState.value
        val msg = s.messageText
        if (msg.isBlank()) return
        launchJob("sending_message") {
            updateState { it.copy(isSendingMessage = true) }
            val n = AppNotification(groupId = s.selectedGroupId!!, memberId = s.member?.id, message = msg, triggerEvent = NotifEvent.MEMBER_MESSAGE)
            notifRepo.sendNotification(n).onSuccess {
                updateState { it.copy(isSendingMessage = false, messageSentSuccess = true, messageText = "") }
                delay(2000)
                updateState { it.copy(messageSentSuccess = false) }
            }.onFailure { handleError(it) }
        }
    }

    fun dismissImpersonationBanner() {
        updateState { it.copy(isImpersonationBannerDismissed = true) }
    }

    fun loadMemberAuditReport() {
        val memberId = uiState.value.member?.id ?: return
        launchJob("audit_report") {
            updateState { it.copy(memberAuditReport = AsyncResult.Loading) }
            auditRepo.observeMemberAuditReport(memberId).collect { res ->
                res.onSuccess { r ->
                    updateState { it.copy(memberAuditReport = AsyncResult.Success(r), isLoadingAuditReport = false) }
                }.onFailure { e ->
                    updateState { it.copy(memberAuditReport = AsyncResult.Error(e.toUserMessage(), e), isLoadingAuditReport = false) }
                }
            }
        }
    }

    fun setActive(active: Boolean) {
        if (active) {
            observeContext()
            refreshData()
        } else {
            // Stop observations when not active to save battery/bandwidth
            listOf("context_observation", "live_observation", "insights_loading", "audit_report").forEach { key ->
                cancelJob(key)
            }
        }
    }

    fun exportMyStatement() {
        val group = uiState.value.group ?: return
        val ledger = uiState.value.memberLedger
        launchJob("export_ledger") {
            updateState { it.copy(isExporting = true) }
            exportRepo.exportLedgerToCsv(group, ledger).onSuccess { file ->
                updateState { it.copy(isExporting = false) }
                sendEvent(UIEvent.OpenFile(file, "text/csv", "Export Statement"))
            }.onFailure {
                updateState { it.copy(isExporting = false) }
                handleError(it)
            }
        }
    }

    fun downloadMembershipContract() {
        val m = uiState.value.member ?: return
        val g = uiState.value.group ?: return
        launchJob("download_contract") {
            updateState { it.copy(isExporting = true) }
            exportRepo.exportMembershipContract(m, g).onSuccess { file ->
                updateState { it.copy(isExporting = false) }
                sendEvent(UIEvent.OpenFile(file, "application/pdf", "Contract"))
            }.onFailure {
                updateState { it.copy(isExporting = false) }
                handleError(it)
            }
        }
    }

    fun downloadPdfStatement() {
        val member = uiState.value.member ?: return
        val group = uiState.value.group ?: return
        val ledger = uiState.value.memberLedger
        launchJob("download_statement") {
            updateState { it.copy(isExporting = true) }
            exportRepo.exportLedgerToPdf(group, ledger, member).onSuccess { file ->
                updateState { it.copy(isExporting = false) }
                sendEvent(UIEvent.OpenFile(file, "application/pdf", "Statement"))
            }.onFailure {
                updateState { it.copy(isExporting = false) }
                handleError(it)
            }
        }
    }

    fun downloadGroupStatement() {
        val group = uiState.value.group ?: return
        launchJob("download_group_ledger") {
            updateState { it.copy(isExporting = true) }
            ledgerRepo.getGroupLedger(group.id!!).onSuccess { entries ->
                exportRepo.exportLedgerToPdf(group, entries).onSuccess { file ->
                    updateState { it.copy(isExporting = false) }
                    sendEvent(UIEvent.OpenFile(file, "application/pdf", "Group Ledger"))
                }
            }.onFailure {
                updateState { it.copy(isExporting = false) }
                handleError(it)
            }
        }
    }

    fun downloadGroupConstitution() {
        val group = uiState.value.group ?: return
        launchJob("download_constitution") {
            updateState { it.copy(isLoading = true) }
            exportRepo.exportGroupConstitution(group).onSuccess { file ->
                updateState { it.copy(isLoading = false) }
                sendEvent(UIEvent.OpenFile(file, "application/pdf", "Constitution"))
            }.onFailure {
                updateState { it.copy(isLoading = false) }
                handleError(it)
            }
        }
    }

    fun getDownloadParams(url: String): Map<String, String> {
        return supabaseRepo.accessToken?.let { mapOf("Authorization" to "Bearer $it") } ?: emptyMap()
    }
}
