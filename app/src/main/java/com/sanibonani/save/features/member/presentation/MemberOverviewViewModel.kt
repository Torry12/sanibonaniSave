package com.sanibonani.save.features.member.presentation

import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.UserRole
import com.sanibonani.save.domain.logging.AppLogger
import com.sanibonani.save.domain.service.DevSettingsService
import com.sanibonani.save.domain.service.NetworkConnectivityService
import com.sanibonani.save.domain.usecase.auth.BuildDocumentAuthHeadersUseCase
import com.sanibonani.save.domain.usecase.auth.GetAttributableActorIdUseCase
import com.sanibonani.save.domain.usecase.auth.GetCurrentUserRoleUseCase
import com.sanibonani.save.domain.usecase.group.GetGroupByIdUseCase
import com.sanibonani.save.domain.usecase.member.ObserveMembershipsUseCase
import com.sanibonani.save.domain.utils.PaymentCalculator
import com.sanibonani.save.domain.utils.toUserMessage
import com.sanibonani.save.features.common.presentation.BaseViewModel
import com.sanibonani.save.features.common.presentation.HydrationWatchdog
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
import kotlinx.coroutines.flow.combine
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
    private val devSettings: DevSettingsService,
    networkService: NetworkConnectivityService,
) : BaseViewModel<MemberUiState>(MemberUiState()) {

    val state: StateFlow<MemberUiState> = uiState

    /** The group/member pair the live reads are currently streaming, or null when nothing runs. */
    private var liveObservationSubject: String? = null

    /**
     * Gives the portal a bound on a hydration that is not coming.
     *
     * The screen shows "Syncing your portal…" while isLoading is set with nothing rendered, and only a
     * snapshot package releases it - so a warm-up that could not read the memberships, which keeps the
     * cached contexts and returns by design, used to leave the member spinning with the Retry that could
     * re-pull the book hidden behind the loader.
     */
    private val hydrationWatchdog = HydrationWatchdog(
        key = "portal_hydration_watchdog",
        launch = { jobKey, block -> launchJob(jobKey) { block() } },
        cancelJob = ::cancelJob,
        shouldReport = {
            val current = uiState.value
            // An exit is already on its way out. A Support Mode entry that never produces the
            // targeted book is this wait's to end: the privacy lock has no other way off the screen.
            !current.isExiting && (current.member == null || current.group == null)
        },
        onVerdict = { message ->
            updateState { it.copy(isLoading = false, error = message) }
        }
    )

    /**
     * The group the route last asked this portal to open, recorded when the request is made rather than
     * when its transition job runs. The memberships read is subscribed to in `init`, so its first
     * emission can land in the same dispatch as a Support Mode entry, and the auto-select below would
     * otherwise cancel the entry the operator just asked for and open their own portal instead.
     */
    private var requestedPortalGroupId: String? = null

    /**
     * Set in the same call that asks for Support Mode, before that call suspends.
     *
     * The dashboard's first frame warms every group the operator belongs to. That warm-up and the
     * impersonation share one hydration budget, so the warm-up has to be skipped once a member
     * has been asked for, even while the role check is still in flight.
     */
    private var requestedSupportMemberId: String? = null

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
        observeDatabaseChanges()
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

    /**
     * Moves the portal onto the tier the developer switch just installed.
     *
     * A switch replaces both the Supabase client and the Room file, so the rows on screen and the
     * live reads streaming them belong to a database that is now closed - and a feed subscribed to a
     * closed instance never delivers the new tier's rows. The cache drops first, because a warm-up
     * that rebuilds the snapshot it already holds does not re-emit, which would leave this portal
     * showing the replaced book for the rest of the session; the memberships re-collect too, since
     * the list itself came from that file.
     */
    private fun observeDatabaseChanges() {
        launchJob("portal_database_change") {
            devSettings.settingsChanged.collect {
                stopLiveObservation()
                memberContextCacheService.invalidateForDatabaseChange()
                val groupId = uiState.value.selectedGroupId
                val memberId = uiState.value.targetMemberId.takeIf { uiState.value.isImpersonating }
                loadMemberships()
                if (groupId != null) {
                    // Re-entering the portal is what re-warms it - and for Support Mode it is the
                    // entry itself, so the operator lands on the member as that member exists now.
                    setPortalContext(memberId, groupId)
                } else {
                    updateState { it.clearGroupContext().copy(isLoading = false) }
                }
            }
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
                // A verdict the member can act on outranks the loader: re-claiming isLoading here put
                // the Retry back behind a spinner on the next pass.
                if (!hydrationWatchdog.isReporting) updateState { it.copy(isLoading = true) }
                // A snapshot whose pillars never finished is not a load in progress: nothing else is
                // going to hand this portal a package, so the wait needs its own verdict.
                hydrationWatchdog.armIfIdle()
            }
            return
        }

        val timeoutReported = hydrationWatchdog.settle()

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
                    // A route that named its group outranks "the first group I belong to": auto-selecting
                    // over a Support Mode entry re-enters setPortalContext for a different portal than the
                    // one the operator asked for, and cancels the entry already in flight.
                    if (uiState.value.selectedGroupId == null && requestedPortalGroupId == null &&
                        memberships.isNotEmpty()
                    ) {
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
        val requestedGroupId = groupId?.takeIf { it.isNotBlank() }
        requestedPortalGroupId = requestedGroupId
        requestedSupportMemberId = memberId?.takeIf { it.isNotBlank() }
        // The refresh in flight belongs to the portal being left. Letting it finish writes its
        // loader, its timeout, or its failure onto the portal this call is opening.
        cancelJob("refresh_data")
        if (requestedSupportMemberId != null) {
            cancelJob("context_warmup")
            memberContextCacheService.cancelBackgroundWarmups()
        }
        launchJob("portal_context_transition") {
            val targetMemberId = memberId?.takeIf { it.isNotBlank() }
            val targetGroupId = requestedGroupId
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
                // The role check suspended, so a warm-up started on the way in can be holding the
                // hydration permits this entry needs. Drop it before the entry reads.
                cancelJob("context_warmup")
                memberContextCacheService.cancelBackgroundWarmups()
                updateState { it.clearGroupContext().copy(
                    isImpersonating = true,
                    targetMemberId = targetMemberId,
                    selectedGroupId = targetGroupId,
                    isExiting = false,
                    isLoading = true
                ) }
                // The privacy lock stays up until this member is on screen. A read that never
                // returns has to become the refusal, or the lock is the whole screen forever.
                hydrationWatchdog.arm()
                memberContextCacheService.impersonateMember(targetMemberId, targetGroupId)
                    .onSuccess { cachedCtx ->
                        val realMemberId = cachedCtx.member?.id ?: targetMemberId
                        updateState { it.copy(targetMemberId = realMemberId, isLoading = false) }
                        val shown = uiState.value
                        val memberShown = shown.member
                        val rendered = memberShown != null && shown.group != null &&
                            (memberShown.id == realMemberId || memberShown.userId == realMemberId)
                        if (rendered) hydrationWatchdog.settle()
                    }
                    .onFailure { e ->
                        hydrationWatchdog.cancel()
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
                hydrationWatchdog.arm()
            } else {
                hydrationWatchdog.cancel()
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

    /** The refresh result is only meaningful for the portal that asked for it. */
    private fun portalStillShowing(groupId: String, impersonating: Boolean): Boolean {
        val current = uiState.value
        return current.selectedGroupId == groupId && current.isImpersonating == impersonating
    }

    fun switchGroup(groupId: String) = selectGroup(groupId)

    fun clearImpersonation(onComplete: () -> Unit = {}) {
        val operatorId = getAttributableActorId()
        requestedSupportMemberId = null
        cancelJob("portal_context_transition")
        cancelJob("refresh_data")
        hydrationWatchdog.cancel()
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
        // Pull-to-refresh is a new request for the book. The previous timeout does not describe
        // this one. A refresh that returns without a package the portal can render has to stay
        // inside that wait: clearing the loader here used to leave a blank dashboard, with the
        // Retry gone and nothing on screen to ask for the book again.
        if (!state.isImpersonating) hydrationWatchdog.arm()
        launchJob("refresh_data") {
            val result = if (state.isImpersonating) {
                val memberId = impersonated?.id.serverIdOrReport("member")
                if (memberId == null) Result.success(Unit)
                else memberContextCacheService.refreshImpersonatedMember(memberId, groupId)
            } else {
                memberContextCacheService.refreshGroup(userId, groupId)
            }
            if (!portalStillShowing(groupId, state.isImpersonating)) return@launchJob
            result.onSuccess {
                val rendered = uiState.value.let { it.member != null && it.group != null }
                if (rendered || state.isImpersonating) {
                    // The package collector settles a wait it actually rendered. This covers the
                    // refresh that found the book already on screen and so never re-emitted it.
                    if (rendered) hydrationWatchdog.settle()
                    updateState { it.copy(isLoading = false) }
                }
            }.onFailure { e ->
                if (!portalStillShowing(groupId, state.isImpersonating)) return@onFailure
                if (!state.isImpersonating) hydrationWatchdog.cancel()
                updateState { s -> s.copy(isLoading = false, error = e.toUserMessage()) }
            }
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
            val supportEntry = uiState.value.isImpersonating || requestedSupportMemberId != null
            if (!supportEntry) {
                getAttributableActorId()?.let { userId ->
                    launchJob("context_warmup") { memberContextCacheService.warmUpForUser(userId) }
                }
                // Coming back to a portal that shows nothing is a wait whether the load was asked
                // for here or on a screen the member has since come back from.
                if (uiState.value.member == null || uiState.value.group == null) {
                    hydrationWatchdog.arm()
                }
            }
        } else {
            hydrationWatchdog.cancel()
            listOf(
                "context_observation",
                "insights_loading",
                "audit_report",
                "context_warmup",
                "refresh_data"
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
}
