package com.sanibonani.save.features.member.presentation

import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.UserRole
import com.sanibonani.save.domain.logging.AppLogger
import com.sanibonani.save.domain.service.NetworkConnectivityService
import com.sanibonani.save.domain.usecase.auth.BuildDocumentAuthHeadersUseCase
import com.sanibonani.save.domain.usecase.auth.GetAttributableActorIdUseCase
import com.sanibonani.save.domain.usecase.auth.GetCurrentUserRoleUseCase
import com.sanibonani.save.domain.usecase.group.GetGroupByIdUseCase
import com.sanibonani.save.domain.usecase.member.ObserveMembershipsUseCase
import com.sanibonani.save.domain.utils.PaymentCalculator
import com.sanibonani.save.domain.utils.toUserMessage
import com.sanibonani.save.features.common.presentation.BaseViewModel
import com.sanibonani.save.features.common.presentation.UIEvent
import com.sanibonani.save.features.member.presentation.coordination.MemberActionHost
import com.sanibonani.save.features.member.presentation.coordination.MemberDocumentCoordinator
import com.sanibonani.save.features.member.presentation.coordination.MemberLiveObservationCoordinator
import com.sanibonani.save.features.member.presentation.coordination.MemberMessagingCoordinator
import com.sanibonani.save.service.CachedGroupContext
import com.sanibonani.save.features.member.presentation.state.MemberUiState
import com.sanibonani.save.service.MemberGroupContextCacheService
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

@HiltViewModel
class MemberOverviewViewModel @Inject constructor(
    private val memberContextCacheService: MemberGroupContextCacheService,
    private val observeMemberships: ObserveMembershipsUseCase,
    private val getGroupById: GetGroupByIdUseCase,
    private val getAttributableActorId: GetAttributableActorIdUseCase,
    private val getCurrentUserRole: GetCurrentUserRoleUseCase,
    private val buildDocumentAuthHeaders: BuildDocumentAuthHeadersUseCase,
    private val paymentCalculator: PaymentCalculator,
    private val messagingCoordinator: MemberMessagingCoordinator,
    private val documentCoordinator: MemberDocumentCoordinator,
    private val liveObservation: MemberLiveObservationCoordinator,
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
            uiState
                .map { it.selectedGroupId }
                .distinctUntilChanged()
                .flatMapLatest { groupId ->
                    if (groupId.isNullOrBlank()) flowOf(null)
                    else memberContextCacheService.contexts.map { it[groupId] }
                }
                .distinctUntilChanged()
                .collectLatest(::hydrateSelectedContext)
        }
    }

    private fun hydrateSelectedContext(context: CachedGroupContext?) {
        val state = uiState.value
        val partialSupportMemberId = state.targetMemberId.takeIf { state.isImpersonating }
        val pkg = context?.toMemberContextPackage(partialSupportMemberId)
        if (pkg == null) {
            cancelJob("live_observation")
            if (context != null) updateState { it.copy(isLoading = true) }
            return
        }

        updateState { s ->
            s.copy(
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
            )
        }

        val liveMemberId = pkg.member.id.serverIdOrReport("member")
        if (liveMemberId == null) cancelJob("live_observation")
        else startLiveObservation(liveMemberId, pkg.group)
    }

    private fun startLiveObservation(memberId: String, group: Group) {
        val groupId = group.id.serverIdOrReport("group") ?: return
        liveObservation.start(host, groupId, memberId, group)
    }

    private fun loadMemberships() {
        val userId = getAttributableActorId() ?: return
        launchJob("membership_observation") {
            observeMemberships(userId).collect { res ->
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

            if (canImpersonate && targetGroupId != null) {
                updateState { it.clearGroupContext().copy(
                    isImpersonating = true,
                    impersonatorId = getAttributableActorId(),
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
                getAttributableActorId()?.let { memberContextCacheService.warmUpForUser(it) }
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
        val role = getCurrentUserRole()
        if (role == UserRole.PLATFORM_ADMIN) return true
        val userId = getAttributableActorId() ?: return false
        val group = getGroupById(groupId).getOrNull() ?: return false
        return group.adminUserId == userId
    }

    fun selectGroup(groupId: String) = setPortalContext(null, groupId)

    fun switchGroup(groupId: String) = selectGroup(groupId)

    fun clearImpersonation(onComplete: () -> Unit = {}) {
        val operatorId = getAttributableActorId()
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
        val state = uiState.value
        val groupId = state.selectedGroupId ?: return
        val impersonated = state.member.takeIf { state.isImpersonating }
        if (state.isImpersonating && impersonated == null) return
        val userId = if (state.isImpersonating) {
            impersonated?.userId.orEmpty()
        } else {
            getAttributableActorId() ?: return
        }

        updateState { it.copy(isLoading = true, error = null) }
        launchJob("refresh_data") {
            val result = if (state.isImpersonating) {
                val memberId = impersonated?.id.serverIdOrReport("member")
                if (memberId == null) Result.success(Unit)
                else memberContextCacheService.refreshImpersonatedMember(memberId, groupId)
            } else {
                memberContextCacheService.refreshGroup(userId, groupId)
            }
            result.onFailure { e -> updateState { s -> s.copy(error = e.toUserMessage()) } }
            updateState { it.copy(isLoading = false) }
        }
    }

    fun selectTab(index: Int) { updateState { it.copy(selectedTab = index) } }

    fun updateMessageText(text: String) = messagingCoordinator.updateText(host, text)

    fun sendMessageToAdmin() = messagingCoordinator.sendToAdmin(host)

    fun dismissImpersonationBanner() { updateState { it.copy(isImpersonationBannerDismissed = true) } }

    fun loadMemberAuditReport() = documentCoordinator.loadAuditReport(host)

    fun setActive(active: Boolean) {
        if (active) {
            observeContext()
            // Entering the dashboard warms the cache only; a full network refresh stays bound
            // to the pull-to-refresh gesture.
            if (!uiState.value.isImpersonating) {
                getAttributableActorId()?.let { userId ->
                    launchJob("context_warmup") { memberContextCacheService.warmUpForUser(userId) }
                }
            }
        } else {
            listOf("context_observation", "live_observation", "insights_loading", "audit_report", "context_warmup").forEach { cancelJob(it) }
        }
    }

    fun downloadMembershipContract() = documentCoordinator.downloadMembershipContract(host)

    fun downloadPdfStatement() = documentCoordinator.downloadPdfStatement(host)

    fun downloadCsvStatement() = documentCoordinator.downloadCsvStatement(host)

    fun downloadGroupStatement() = documentCoordinator.downloadGroupStatement(host)

    fun downloadGroupConstitution() = documentCoordinator.downloadGroupConstitution(host)

    fun getDownloadParams(url: String): Map<String, String> = buildDocumentAuthHeaders(url)
}
