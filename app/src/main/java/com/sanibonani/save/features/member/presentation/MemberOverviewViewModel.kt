package com.sanibonani.save.features.member.presentation

import com.sanibonani.save.domain.model.AppNotification
import com.sanibonani.save.domain.model.AsyncResult
import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.NotifEvent
import com.sanibonani.save.domain.repository.admin.AuditRepository
import com.sanibonani.save.domain.repository.auth.SupabaseRepository
import com.sanibonani.save.domain.repository.equity.ContributionRepository
import com.sanibonani.save.domain.repository.treasury.LedgerRepository
import com.sanibonani.save.domain.repository.credit.LoanRepository
import com.sanibonani.save.domain.repository.group.GroupRepository
import com.sanibonani.save.domain.repository.member.MemberRepository
import com.sanibonani.save.domain.repository.system.ExportRepository
import com.sanibonani.save.domain.repository.system.NotificationRepository
import com.sanibonani.save.domain.service.NetworkConnectivityService
import com.sanibonani.save.domain.usecase.group.GetGroupBusinessInsightsUseCase
import com.sanibonani.save.domain.utils.PaymentCalculation
import com.sanibonani.save.domain.utils.PaymentCalculator
import com.sanibonani.save.domain.utils.toUserMessage
import com.sanibonani.save.features.common.presentation.BaseViewModel
import com.sanibonani.save.features.common.presentation.UIEvent
import com.sanibonani.save.features.member.presentation.state.MemberContextPackage
import com.sanibonani.save.features.member.presentation.state.MemberUiState
import com.sanibonani.save.service.MemberGroupContextCacheService
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    private val paymentCalculator: PaymentCalculator,
    networkService: NetworkConnectivityService,
) : BaseViewModel<MemberUiState>(MemberUiState()) {

    val state: StateFlow<MemberUiState> = uiState

    init {
        observeNetwork(networkService) { s, online, airplane ->
            s.copy(isSystemOnline = online, isAirplaneMode = airplane)
        }
        loadMemberships()
    }

    private fun observeContext() {
        launchJob("context_observation") {
            observeGroupContext(
                groupIdFlow = uiState.map { it.selectedGroupId },
                contextSource = memberContextCacheService.contexts,
            ) { context ->
                if (context == null) {
                    updateState { it.copy(memberContext = AsyncResult.Uninitialized) }
                    return@observeGroupContext
                }

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

                    updateState { s -> s.copy(
                        memberContext = AsyncResult.Success(pkg),
                        member = pkg.member,
                        group = pkg.group,
                        contributions = pkg.contributions,
                        beneficiaries = pkg.beneficiaries,
                        loans = pkg.loans,
                        loanRepayments = pkg.repayments,
                        paymentCalculation = pkg.calculation,
                        monthlyContributionAmount = paymentCalculator.calculateExpectedMemberGroupPart(pkg.group, pkg.member),
                        notifications = pkg.notifications,
                        messages = pkg.messages,
                        isLoading = false,
                        cacheLastSyncByGroup = s.cacheLastSyncByGroup + (pkg.group.id.orEmpty() to context.lastUpdatedMillis),
                    ) }

                    val liveMemberId = pkg.member.id.serverIdOrReport("member") ?: return@observeGroupContext
                    startLiveObservation(liveMemberId, pkg.group)
                } else {
                    updateState { it.copy(memberContext = AsyncResult.Loading, isLoading = true) }
                }
            }
        }
    }

    private fun startLiveObservation(memberId: String, group: Group) {
        val groupId = group.id.serverIdOrReport("group") ?: return
        launchJob("live_observation") {
            // AI Performance optimization: Parallel warmup pattern
            coroutineScope {
                val membersTask = async {
                    memberRepo.getGroupMembers(groupId).firstOrNull()?.getOrNull() ?: emptyList()
                }

                val members = membersTask.await()
                val insightsTask = async(Dispatchers.Default) {
                    getGroupBusinessInsightsUseCase(group, members)
                }

                val insights = insightsTask.await()
                updateState { it.copy(businessInsight = insights, allMembers = members) }
            }

            coroutineScope {
                launch {
                    groupRepo.observeGroup(groupId).collect { res ->
                        res.onSuccess { g -> if (g != null) updateState { it.copy(group = g) } }
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
                launch {
                    memberRepo.getGroupMembers(groupId).collectLatest { res ->
                        res.onSuccess { members ->
                            val insights = withContext(Dispatchers.Default) {
                                getGroupBusinessInsightsUseCase(group, members)
                            }
                            updateState { it.copy(businessInsight = insights, allMembers = members) }
                        }
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

    fun setPortalContext(memberId: String?, groupId: String?) {
        launchJob("portal_context_transition") {
            if (!memberId.isNullOrBlank() && !groupId.isNullOrBlank()) {
                val role = supabaseRepo.getUserRole()
                updateState { it.clearGroupContext().copy(
                    isImpersonating = true,
                    impersonatorId = supabaseRepo.currentUserId,
                    targetMemberId = memberId,
                    impersonatorRole = role,
                    selectedGroupId = groupId,
                    isLoading = true
                ) }
                memberContextCacheService.impersonateMember(memberId, groupId)
                    .onSuccess { cachedCtx ->
                        val realMemberId = cachedCtx.member?.id ?: memberId
                        updateState { it.copy(targetMemberId = realMemberId, isLoading = false) }
                    }
                    .onFailure { e ->
                        updateState { it.copy(isLoading = false, error = e.toUserMessage()) }
                        handleError(e)
                    }
            } else if (!groupId.isNullOrBlank()) {
                updateState { it.clearGroupContext().copy(
                    isImpersonating = false,
                    targetMemberId = null,
                    selectedGroupId = groupId,
                    isLoading = true
                ) }
                supabaseRepo.currentUserId?.let { memberContextCacheService.warmUpForUser(it) }
            } else {
                updateState { it.clearGroupContext().copy(isImpersonating = false, targetMemberId = null, isLoading = false) }
            }
        }
    }

    fun selectGroup(groupId: String) = setPortalContext(null, groupId)

    fun switchGroup(groupId: String) = selectGroup(groupId)

    fun clearImpersonation(onComplete: () -> Unit = {}) {
        setPortalContext(null, uiState.value.selectedGroupId)
        onComplete()
    }

    fun refreshData() {
        val userId = supabaseRepo.currentUserId ?: return
        launchJob("refresh_data") { memberContextCacheService.warmUpForUser(userId) }
    }

    fun selectTab(index: Int) { updateState { it.copy(selectedTab = index) } }

    fun updateMessageText(text: String) { updateState { it.copy(messageText = text) } }

    fun sendMessageToAdmin() {
        val s = uiState.value
        val msg = s.messageText
        if (msg.isBlank()) return
        val groupId = s.selectedGroupId ?: return
        launchJob("sending_message") {
            updateState { it.copy(isSendingMessage = true) }
            val n = AppNotification(groupId = groupId, memberId = s.member?.id, message = msg, triggerEvent = NotifEvent.MEMBER_MESSAGE)
            notifRepo.sendNotification(n).onSuccess {
                updateState { it.copy(isSendingMessage = false, messageSentSuccess = true, messageText = "") }
                delay(2000.milliseconds)
                updateState { it.copy(messageSentSuccess = false) }
            }.onFailure { handleError(it) }
        }
    }

    fun dismissImpersonationBanner() { updateState { it.copy(isImpersonationBannerDismissed = true) } }

    fun loadMemberAuditReport() {
        val memberId = uiState.value.member?.id ?: return
        launchSafeJob("audit_report", "Failed to load audit report") {
            updateState { it.copy(memberAuditReport = AsyncResult.Loading, isLoadingAuditReport = true) }
            auditRepo.triggerMemberAudit(memberId)
                .onSuccess { report ->
                    updateState { it.copy(memberAuditReport = AsyncResult.Success(report), isLoadingAuditReport = false) }
                }
                .onFailure { e ->
                    updateState { it.copy(memberAuditReport = AsyncResult.Error(e.toUserMessage(), e.javaClass.simpleName), isLoadingAuditReport = false) }
                }
        }
    }

    fun setActive(active: Boolean) {
        if (active) {
            observeContext()
            refreshData()
        } else {
            listOf("context_observation", "live_observation", "insights_loading", "audit_report").forEach { cancelJob(it) }
        }
    }

    fun downloadMembershipContract() {
        val s = uiState.value
        val member = s.member ?: return
        val group = s.group ?: return
        launchSafeJob("download_contract", "Failed to download contract") {
            updateState { it.copy(isExporting = true) }
            exportRepo.exportMembershipContract(member, group).onSuccess { file ->
                updateState { it.copy(isExporting = false) }
                sendEvent(UIEvent.OpenFile(file, "application/pdf", "Membership Certificate"))
            }.onFailure {
                updateState { it.copy(isExporting = false) }
                handleError(it)
            }
        }
    }

    fun downloadPdfStatement() {
        val s = uiState.value
        val group = s.group ?: return
        val ledger = s.memberLedger
        launchSafeJob("download_statement", "Failed to download statement") {
            updateState { it.copy(isExporting = true) }
            exportRepo.exportLedgerToPdf(group, ledger).onSuccess { file ->
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
        val groupId = group.id.serverIdOrReport("group") ?: return
        launchSafeJob("download_group_ledger", "Failed to download group ledger") {
            updateState { it.copy(isExporting = true) }
            ledgerRepo.getGroupLedger(groupId).onSuccess { entries ->
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
        launchSafeJob("download_constitution", "Failed to download constitution") {
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

    fun getDownloadParams(): Map<String, String> = supabaseRepo.accessToken?.let { mapOf("Authorization" to "Bearer $it") } ?: emptyMap()
}
