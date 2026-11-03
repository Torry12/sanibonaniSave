package com.sanibonani.save.features.landing.presentation

import com.sanibonani.save.domain.utils.safeRunCatching
import com.sanibonani.save.analytics.AnalyticsTaxonomy
import com.sanibonani.save.analytics.AppAnalytics
import com.sanibonani.save.domain.logging.AppLogger
import com.sanibonani.save.domain.model.AsyncResult
import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.Member
import com.sanibonani.save.domain.model.PlatformAnalytics
import com.sanibonani.save.domain.model.PlatformConfig
import com.sanibonani.save.domain.model.UserRole
import com.sanibonani.save.domain.service.DevSettingsService
import com.sanibonani.save.domain.service.NetworkConnectivityService
import com.sanibonani.save.domain.usecase.admin.GetPlatformAnalyticsUseCase
import com.sanibonani.save.domain.usecase.admin.RefreshPlatformConfigUseCase
import com.sanibonani.save.domain.usecase.auth.GetAttributableActorIdUseCase
import com.sanibonani.save.domain.usecase.auth.GetCurrentUserRoleUseCase
import com.sanibonani.save.domain.usecase.auth.IsLoggedInUseCase
import com.sanibonani.save.domain.usecase.auth.ObserveAuthSessionUseCase
import com.sanibonani.save.domain.usecase.group.GetManagedGroupsUseCase
import com.sanibonani.save.domain.usecase.member.GetMembershipsUseCase
import com.sanibonani.save.domain.utils.toUserMessage
import com.sanibonani.save.features.common.presentation.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull

data class LandingUiState(
    val analytics: AsyncResult<PlatformAnalytics> = AsyncResult.Uninitialized,
    val settings: AsyncResult<PlatformConfig> = AsyncResult.Uninitialized,
    val isMemberOrAdmin: Boolean = false,
    val isLoggedIn: Boolean = false,
    val userRole: UserRole = UserRole.MEMBER,
    val pendingGroup: Group? = null,
    val isSystemOnline: Boolean = true,
    val isAirplaneMode: Boolean = false
)

sealed interface LandingIntent {
    object Refresh : LandingIntent
}

@HiltViewModel
class LandingViewModel @Inject constructor(
    private val getPlatformAnalytics: GetPlatformAnalyticsUseCase,
    private val refreshPlatformConfig: RefreshPlatformConfigUseCase,
    private val getMemberships: GetMembershipsUseCase,
    private val getManagedGroups: GetManagedGroupsUseCase,
    private val observeAuthSession: ObserveAuthSessionUseCase,
    private val getCurrentUserRole: GetCurrentUserRoleUseCase,
    private val isLoggedIn: IsLoggedInUseCase,
    private val getAttributableActorId: GetAttributableActorIdUseCase,
    private val networkService: NetworkConnectivityService,
    private val devSettings: DevSettingsService
) : BaseViewModel<LandingUiState>(LandingUiState()) {

    val state: StateFlow<LandingUiState> = uiState
    private var refreshJob: Job? = null

    private fun currentState(): LandingUiState = uiState.value

    init {
        observeNetwork(networkService) { s, online, airplane ->
            s.copy(isSystemOnline = online, isAirplaneMode = airplane)
        }
        observeDevSettingsChanges()
        observeSession()
        refreshData()
    }

    @OptIn(FlowPreview::class)
    private fun observeSession() {
        launchJob("observe_session") {
            observeAuthSession()
                .distinctUntilChanged { old, new -> old?.accessToken == new?.accessToken }
                .debounce(300)
                .collect { session ->
                    val current = currentState()
                    val signedIn = session != null
                    if (signedIn == current.isLoggedIn && (current.userRole != UserRole.MEMBER)) {
                        return@collect
                    }

                    val userRole = if (signedIn) getCurrentUserRole() else UserRole.MEMBER

                    updateState { it.copy(isLoggedIn = signedIn, userRole = userRole) }
                    if (signedIn) {
                        refreshData()
                    }
                }
        }
    }

    private fun observeDevSettingsChanges() {
        launchJob("observe_dev_settings") {
            devSettings.settingsChanged.collect {
                delay(500)
                refreshData()
            }
        }
    }

    fun onIntent(intent: LandingIntent) {
        when (intent) {
            LandingIntent.Refresh -> refreshData()
        }
    }

    fun refreshData() {
        refreshJob?.cancel()
        refreshJob = launchJob("refresh_data") {
            try {
                if (!uiState.value.isSystemOnline) {
                    AppLogger.w("LandingVM", "Refusal to refresh: System is offline.")
                    val offlineMsg = "You are currently offline. Please check your internet."
                    updateState { it.copy(
                        analytics = AsyncResult.Error(offlineMsg, "offline"),
                        settings = AsyncResult.Error(offlineMsg, "offline")
                    ) }
                } else {
                    AppAnalytics.track(AnalyticsTaxonomy.Events.LANDING_REFRESH_STARTED)
                    updateState { it.copy(analytics = AsyncResult.Loading, settings = AsyncResult.Loading) }

                    val signedIn = isLoggedIn()
                    val userId = getAttributableActorId()

                    AppLogger.d("LandingVM", "🔄 Refreshing landing data. LoggedIn=$signedIn, UserID=${userId?.take(5)}")

                    val results = withTimeoutOrNull(30_000) {
                        supervisorScope {
                            val analyticsDeferred = async(Dispatchers.IO) { getPlatformAnalytics() }
                            val settingsDeferred = async(Dispatchers.IO) { refreshPlatformConfig() }

                            val membershipsDeferred = if (userId != null) {
                                async(Dispatchers.IO) { getMemberships(userId) }
                            } else null

                            val adminGroupsDeferred = if (userId != null) {
                                async(Dispatchers.IO) { getManagedGroups(userId, adminOnly = true) }
                            } else null

                            Triple(
                                safeRunCatching { analyticsDeferred.await() }.getOrElse { Result.failure(it) },
                                safeRunCatching { settingsDeferred.await() }.getOrElse { Result.failure(it) },
                                // A leg that threw stays a failed Result instead of collapsing to
                                // null, which is how it used to be read as "no memberships".
                                (membershipsDeferred?.let { deferred -> safeRunCatching { deferred.await() }.getOrElse { Result.failure(it) } } to
                                    adminGroupsDeferred?.let { deferred -> safeRunCatching { deferred.await() }.getOrElse { Result.failure(it) } })
                            )
                        }
                    }

                    if (results != null) {
                        val (analyticsRes, settingsRes, userRes) = results
                        val (membershipsRes, adminGroupsRes) = userRes

                        var isMemberOrAdmin = uiState.value.isMemberOrAdmin
                        var pendingGroup: Group? = uiState.value.pendingGroup

                        if (userId != null) {
                            val verdict = landingMembershipVerdict(
                                membershipsRes = membershipsRes,
                                managedGroupsRes = adminGroupsRes,
                                previousIsMemberOrAdmin = isMemberOrAdmin,
                                previousPendingGroup = pendingGroup
                            )
                            isMemberOrAdmin = verdict.isMemberOrAdmin
                            pendingGroup = verdict.pendingGroup
                            verdict.failure?.let {
                                handleError(it, "Unable to check which groups you belong to. The landing screen is showing the last result.")
                            }
                        } else {
                            // Not signed in is a proven answer, not a missing one: a visitor holds
                            // no membership and has no registration to finish.
                            isMemberOrAdmin = false
                            pendingGroup = null
                        }

                        if (analyticsRes.isSuccess && settingsRes.isSuccess) {
                            val settings = settingsRes.getOrThrow()

                            updateState {
                                it.copy(
                                    analytics = AsyncResult.Success(analyticsRes.getOrThrow()),
                                    settings = AsyncResult.Success(settings),
                                    isLoggedIn = signedIn,
                                    isMemberOrAdmin = isMemberOrAdmin,
                                    pendingGroup = pendingGroup
                                )
                            }
                            AppAnalytics.track(AnalyticsTaxonomy.Events.LANDING_REFRESH_SUCCESS)
                        } else {
                            val (analyticsError, settingsError) = landingSyncErrorMessages(
                                analyticsRes.exceptionOrNull(),
                                settingsRes.exceptionOrNull()
                            )
                            updateState {
                                it.copy(
                                    analytics = analyticsError?.let { message -> AsyncResult.Error(message, "SyncError") }
                                        ?: AsyncResult.Success(analyticsRes.getOrThrow()),
                                    settings = settingsError?.let { message -> AsyncResult.Error(message, "SyncError") }
                                        ?: AsyncResult.Success(settingsRes.getOrThrow()),
                                    isLoggedIn = signedIn,
                                    isMemberOrAdmin = isMemberOrAdmin,
                                    pendingGroup = pendingGroup
                                )
                            }
                        }
                    } else {
                        val error = "Connection timeout. Please check your internet."
                        updateState { it.copy(analytics = AsyncResult.Error(error, "timeout"), settings = AsyncResult.Error(error, "timeout")) }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val error = e.toUserMessage()
                updateState { it.copy(analytics = AsyncResult.Error(error, e.javaClass.simpleName), settings = AsyncResult.Error(error, e.javaClass.simpleName)) }
            }
        }
    }
}

/**
 * The two verdicts the landing hero acts on. `isMemberOrAdmin = false` puts "Join a group" in
 * front of someone already in one, and a null `pendingGroup` hides the prompt to finish a
 * registration whose joining fee is still owed, so neither may be asserted from a read that did
 * not answer: `false` needs both legs to answer empty, and the pending registration is only
 * cleared by the managed-groups read itself.
 */
internal data class LandingMembershipVerdict(
    val isMemberOrAdmin: Boolean,
    val pendingGroup: Group?,
    val failure: Throwable?
)

internal fun landingMembershipVerdict(
    membershipsRes: Result<List<Member>>?,
    managedGroupsRes: Result<List<Group>>?,
    previousIsMemberOrAdmin: Boolean,
    previousPendingGroup: Group?
): LandingMembershipVerdict {
    val memberships = membershipsRes?.getOrNull()
    val managed = managedGroupsRes?.getOrNull()

    return LandingMembershipVerdict(
        isMemberOrAdmin = when {
            !memberships.isNullOrEmpty() || !managed.isNullOrEmpty() -> true
            memberships != null && managed != null -> false
            else -> previousIsMemberOrAdmin
        },
        pendingGroup = if (managed != null) managed.find { !it.registrationPaid } else previousPendingGroup,
        failure = membershipsRes?.exceptionOrNull() ?: managedGroupsRes?.exceptionOrNull()
    )
}

/**
 * The analytics and settings cards render separately, so a leg that answered must not carry the
 * other leg's cause. One shared string reported the analytics complaint on the settings card
 * whenever both reads were refused, and hid a permission failure behind a network one.
 */
internal fun landingSyncErrorMessages(
    analyticsFailure: Throwable?,
    settingsFailure: Throwable?
): Pair<String?, String?> =
    analyticsFailure?.toUserMessage() to settingsFailure?.toUserMessage()
