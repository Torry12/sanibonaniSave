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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlin.time.Duration.Companion.seconds

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

    /** The group/member pair the live reads are currently streaming, or null when nothing runs. */
    private var liveObservationSubject: String? = null

    /**
     * Whether the portal watchdog already reported a load that did not arrive. The snapshot that
     * eventually lands invalidates that report - it was a guess about a read still running - but it
     * must not clear an error the member has to act on, so the two are told apart here rather than
     * by the message text.
     */
    private var portalHydrationTimedOut = false

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
        observeUnverifiedSections()
        loadUserRole()
    }

    /**
     * The portal hides the "Admin Dashboard" link unless the signed-in account is a group admin, so
     * the role has to come from the session. The use case fails closed to MEMBER when it cannot read
     * one, which is also the right answer for this screen.
     */
    private fun loadUserRole() {
        launchJob("portal_role") {
            // Suspend call hoisted out: updateState's reducer is a plain lambda with no coroutine body.
            val role = getCurrentUserRole()
            updateState { it.copy(userRole = role) }
        }
    }

    /**
     * Keeps the portal honest about which sections of the selected group the sync never delivered.
     * Driven by the sync layer's own verdict rather than by the snapshot, because a pillar that
     * arrives on a later pass clears the flag without rebuilding it.
     */
    private fun observeUnverifiedSections() {
        launchJob("unverified_sections") {
            combine(
                uiState.map { it.selectedGroupId }.distinctUntilChanged(),
                memberContextCacheService.unverifiedSections
            ) { groupId, stale -> groupId?.let { stale[it] }.orEmpty() }
                .collect { sections -> updateState { it.copy(unverifiedSections = sections) } }
        }
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
            stopLiveObservation()
            if (context != null) {
                updateState { it.copy(isLoading = true) }
                // A snapshot whose pillars never finished is not a load in progress: nothing else
                // is going to hand this portal a package, so the wait needs its own verdict.
                armPortalHydrationWatchdog()
            }
            return
        }

        cancelJob("portal_hydration_watchdog")
        val timeoutReported = portalHydrationTimedOut
        portalHydrationTimedOut = false

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
                error = if (timeoutReported) null else s.error,
                cacheLastSyncByGroup = s.cacheLastSyncByGroup + (pkg.group.id.orEmpty() to context.lastUpdatedMillis),
            )
        }

        val liveMemberId = pkg.member.id.serverIdOrReport("member")
        if (liveMemberId == null) stopLiveObservation()
        else startLiveObservation(liveMemberId, pkg.group)
    }

    /**
     * Gives the portal a bound on a hydration that is not coming.
     *
     * The screen shows "Syncing your portal…" while [MemberUiState.isLoading] is set with nothing
     * rendered, and only a snapshot package releases it - so a warm-up that could not read the
     * memberships, which keeps the cached contexts and returns by design, left the member spinning
     * with the Retry that could re-pull the book hidden behind the loader.
     */
    private fun armPortalHydrationWatchdog() {
        portalHydrationTimedOut = false
        launchJob("portal_hydration_watchdog") {
            delay(PORTAL_HYDRATION_TIMEOUT)
            val current = uiState.value
            // Support Mode owns its own refusal, and an exit is already on its way out; neither is
            // this watchdog's to overwrite.
            if (!current.isImpersonating && !current.isExiting &&
                (current.member == null || current.group == null)
            ) {
                portalHydrationTimedOut = true
                updateState {
                    it.copy(isLoading = false, error = "Loading timed out. Please try again.")
                }
            }
        }
    }

    private fun startLiveObservation(memberId: String, group: Group) {
        val groupId = group.id.serverIdOrReport("group") ?: return
        val subject = "$groupId|$memberId"
        // The cache re-emits the selected snapshot on every warm-up pass, because the pass stamps a
        // newer lastUpdatedMillis onto it. That is the same member and the same book, and restarting
        // here would cancel and re-run all nine live reads for no new data - which a Support Mode
        // session does several times a minute.
        if (liveObservationSubject == subject) return
        liveObservationSubject = subject
        liveObservation.start(host, groupId, memberId, group)
    }

    /**
     * Ends the live reads and forgets which member they were reading, so the next snapshot for the
     * same subject is allowed to start them again.
     */
    private fun stopLiveObservation() {
        liveObservationSubject = null
        cancelJob("live_observation")
    }

    private fun loadMemberships() {
        val userId = getAttributableActorId() ?: return
        launchJob("membership_observation") {
            // The flow re-emits on every sync pass, so one refusal is reported once: toasting the
            // same failure on each pass buries the message the member has to act on.
            var refusalReported = false
            observeMemberships(userId).collect { res ->
                res.onSuccess { memberships ->
                    refusalReported = false
                    updateState { it.copy(memberships = memberships) }
                    if (uiState.value.selectedGroupId == null && memberships.isNotEmpty()) {
                        selectGroup(memberships.first().groupId)
                    }
                }.onFailure { e ->
                    if (!refusalReported) {
                        refusalReported = true
                        handleError(e, "Unable to load the groups you belong to. The list on screen may be out of date.")
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
                    targetMemberId = null,
                    selectedGroupId = targetGroupId,
                    isExiting = false,
                    isLoading = true
                ) }
                getAttributableActorId()?.let { memberContextCacheService.warmUpForUser(it) }
                // The warm-up starts its reads and returns, so this is the only place that knows a
                // portal was asked for and is not on screen yet - including when there is no actor
                // to warm one for at all.
                armPortalHydrationWatchdog()
            } else {
                updateState { it.clearGroupContext().copy(
                    isImpersonating = false,
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
        // The live reads stream the impersonated member's book; leaving them running would keep
        // writing that member's ledger into the portal the operator is about to see as their own.
        stopLiveObservation()
        updateState {
            it.clearGroupContext().copy(
                isImpersonating = false,
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
        if (state.isImpersonating && impersonated == null) {
            // The hydration never produced a record, so there is no member to re-pull. Returning here
            // left the refusal screen's Retry - the only control still on screen - doing nothing; the
            // targeted id is still in the state, so the refresh this needs is the entry itself.
            val targetMemberId = state.targetMemberId ?: return
            setPortalContext(targetMemberId, groupId)
            return
        }
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
                // Coming back to a portal that shows nothing is a wait whether the load was asked
                // for here or on a screen the member has since come back from.
                if (uiState.value.member == null || uiState.value.group == null) {
                    armPortalHydrationWatchdog()
                }
            }
        } else {
            listOf(
                "context_observation",
                "insights_loading",
                "audit_report",
                "context_warmup",
                // A portal left within the bound is no longer waiting on the book, so the wait must
                // not report a timeout to a screen nobody is looking at.
                "portal_hydration_watchdog"
            ).forEach { cancelJob(it) }
            stopLiveObservation()
        }
    }

    fun downloadMembershipContract() = documentCoordinator.downloadMembershipContract(host)

    fun downloadPdfStatement() = documentCoordinator.downloadPdfStatement(host)

    fun downloadCsvStatement() = documentCoordinator.downloadCsvStatement(host)

    fun downloadGroupStatement() = documentCoordinator.downloadGroupStatement(host)

    fun downloadGroupConstitution() = documentCoordinator.downloadGroupConstitution(host)

    fun getDownloadParams(url: String): Map<String, String> = buildDocumentAuthHeaders(url)

    private companion object {
        /**
         * How long the portal waits for a hydration before saying the book did not arrive. The same
         * bound the admin dashboard gives its own, so a slow tier answers and a wedged one does not
         * become the member's problem.
         */
        val PORTAL_HYDRATION_TIMEOUT = 7.seconds
    }
}
