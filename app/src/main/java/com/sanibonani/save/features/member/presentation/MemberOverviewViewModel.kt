package com.sanibonani.save.features.member.presentation

import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.UserRole
import com.sanibonani.save.domain.logging.AppLogger
import com.sanibonani.save.domain.repository.auth.SupabaseRepository
import com.sanibonani.save.domain.repository.equity.ContributionRepository
import com.sanibonani.save.domain.repository.treasury.LedgerRepository
import com.sanibonani.save.domain.repository.credit.LoanRepository
import com.sanibonani.save.domain.repository.group.GroupRepository
import com.sanibonani.save.domain.repository.member.MemberRepository
import com.sanibonani.save.domain.service.NetworkConnectivityService
import com.sanibonani.save.domain.usecase.group.GetGroupBusinessInsightsUseCase
import com.sanibonani.save.domain.utils.PaymentCalculation
import com.sanibonani.save.domain.utils.PaymentCalculator
import com.sanibonani.save.domain.utils.safeRunCatching
import com.sanibonani.save.domain.utils.toUserMessage
import com.sanibonani.save.features.common.presentation.BaseViewModel
import com.sanibonani.save.features.common.presentation.UIEvent
import com.sanibonani.save.features.member.presentation.coordination.MemberActionHost
import com.sanibonani.save.features.member.presentation.coordination.MemberDocumentCoordinator
import com.sanibonani.save.features.member.presentation.coordination.MemberMessagingCoordinator
import com.sanibonani.save.features.member.presentation.state.MemberContextPackage
import com.sanibonani.save.features.member.presentation.state.MemberUiState
import com.sanibonani.save.service.MemberGroupContextCacheService
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
    private val supabaseRepo: SupabaseRepository,
    private val loanRepo: LoanRepository,
    private val getGroupBusinessInsightsUseCase: GetGroupBusinessInsightsUseCase,
    private val paymentCalculator: PaymentCalculator,
    private val messagingCoordinator: MemberMessagingCoordinator,
    private val documentCoordinator: MemberDocumentCoordinator,
    networkService: NetworkConnectivityService,
) : BaseViewModel<MemberUiState>(MemberUiState()) {

    val state: StateFlow<MemberUiState> = uiState

    private val host = object : MemberActionHost {
        override val memberState: StateFlow<MemberUiState> get() = uiState

        override fun updateMemberState(reducer: (MemberUiState) -> MemberUiState) = updateState(reducer)

        override fun launchMemberJob(key: String, block: suspend CoroutineScope.() -> Unit): Job =
            launchJob(key, block)

        override fun launchMemberSafeJob(
            key: String,
            errorPrefix: String?,
            block: suspend CoroutineScope.() -> Unit
        ): Job = launchSafeJob(key, errorPrefix, block = block)

        override fun sendUiEvent(event: UIEvent) = sendEvent(event)

        override fun reportError(throwable: Throwable, userMessage: String?) = handleError(throwable, userMessage)
    }

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
                        member = pkg.member,
                        group = pkg.group,
                        contributions = pkg.contributions,
                        beneficiaries = pkg.beneficiaries,
                        loans = pkg.loans,
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
                    updateState { it.copy(isLoading = true) }
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
                updateState { it.copy(businessInsight = insights) }
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
                            updateState { it.copy(businessInsight = insights) }
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
            val targetMemberId = memberId?.takeIf { it.isNotBlank() }
            val targetGroupId = groupId?.takeIf { it.isNotBlank() }
            // Defense in depth: the route is the only way into Support Mode, so it is also the
            // place an unauthorised deep link would arrive. A member id is honoured only for a
            // platform admin or an admin of that exact group.
            val canImpersonate = targetMemberId != null && isSupportOperator(targetGroupId)
            if (targetMemberId != null && !canImpersonate) {
                AppLogger.w(
                    "MemberOverviewViewModel",
                    "Refusing support-mode entry: the signed-in user does not administer group $targetGroupId."
                )
            }

            if (canImpersonate && targetMemberId != null && targetGroupId != null) {
                updateState { it.clearGroupContext().copy(
                    isImpersonating = true,
                    impersonatorId = supabaseRepo.currentUserId,
                    targetMemberId = targetMemberId,
                    selectedGroupId = targetGroupId,
                    isExiting = false,
                    isLoading = true
                ) }
                memberContextCacheService.impersonateMember(targetMemberId, targetGroupId)
                    .onSuccess { cachedCtx ->
                        val realMemberId = cachedCtx.member?.id ?: targetMemberId
                        updateState { it.copy(targetMemberId = realMemberId, isLoading = false) }
                    }
                    .onFailure { e ->
                        updateState { it.copy(isLoading = false, error = e.toUserMessage()) }
                        handleError(e)
                    }
            } else if (targetGroupId != null) {
                updateState { it.clearGroupContext().copy(
                    isImpersonating = false,
                    impersonatorId = null,
                    targetMemberId = null,
                    selectedGroupId = targetGroupId,
                    isExiting = false,
                    isLoading = true
                ) }
                supabaseRepo.currentUserId?.let { memberContextCacheService.warmUpForUser(it) }
            } else {
                updateState { it.clearGroupContext().copy(
                    isImpersonating = false,
                    impersonatorId = null,
                    targetMemberId = null,
                    isExiting = false,
                    isLoading = false
                ) }
            }
        }
    }

    private suspend fun isSupportOperator(groupId: String?): Boolean {
        if (groupId.isNullOrBlank()) return false
        // Fail closed: an unreadable role is never treated as administrative.
        val role = safeRunCatching { supabaseRepo.getUserRole() }.getOrDefault(UserRole.MEMBER)
        if (role == UserRole.PLATFORM_ADMIN) return true
        val userId = supabaseRepo.currentUserId ?: return false
        val group = groupRepo.getGroupById(groupId).getOrNull() ?: return false
        return group.adminUserId == userId
    }

    fun selectGroup(groupId: String) = setPortalContext(null, groupId)

    fun switchGroup(groupId: String) = selectGroup(groupId)

    fun clearImpersonation(onComplete: () -> Unit = {}) {
        val operatorId = supabaseRepo.currentUserId
        cancelJob("portal_context_transition")
        updateState {
            it.clearGroupContext().copy(
                isImpersonating = false,
                impersonatorId = null,
                targetMemberId = null,
                isExiting = operatorId != null,
                isLoading = false
            )
        }
        onComplete()

        operatorId?.let { userId ->
            launchJob("portal_context_warmup") {
                memberContextCacheService.warmUpForUser(userId)
                if (uiState.value.isExiting && !uiState.value.isImpersonating) {
                    updateState { it.copy(isExiting = false) }
                }
            }
        }
    }

    fun refreshData() {
        val userId = supabaseRepo.currentUserId ?: return
        launchJob("refresh_data") { memberContextCacheService.warmUpForUser(userId) }
    }

    fun selectTab(index: Int) { updateState { it.copy(selectedTab = index) } }

    fun updateMessageText(text: String) = messagingCoordinator.updateText(host, text)

    fun sendMessageToAdmin() = messagingCoordinator.sendToAdmin(host)

    fun dismissImpersonationBanner() { updateState { it.copy(isImpersonationBannerDismissed = true) } }

    fun loadMemberAuditReport() = documentCoordinator.loadAuditReport(host)

    fun setActive(active: Boolean) {
        if (active) {
            observeContext()
            refreshData()
        } else {
            listOf("context_observation", "live_observation", "insights_loading", "audit_report").forEach { cancelJob(it) }
        }
    }

    fun downloadMembershipContract() = documentCoordinator.downloadMembershipContract(host)

    fun downloadPdfStatement() = documentCoordinator.downloadPdfStatement(host)

    fun downloadCsvStatement() = documentCoordinator.downloadCsvStatement(host)

    fun downloadGroupStatement() = documentCoordinator.downloadGroupStatement(host)

    fun downloadGroupConstitution() = documentCoordinator.downloadGroupConstitution(host)

    fun getDownloadParams(): Map<String, String> = supabaseRepo.accessToken?.let { mapOf("Authorization" to "Bearer $it") } ?: emptyMap()
}
