package com.sanibonani.save.features.landing.presentation

import com.sanibonani.save.domain.utils.safeRunCatching
import com.sanibonani.save.analytics.AnalyticsTaxonomy
import com.sanibonani.save.analytics.AppAnalytics
import com.sanibonani.save.domain.logging.AppLogger
import com.sanibonani.save.domain.model.AsyncResult
import com.sanibonani.save.domain.model.Group
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
        observeNetwork()
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

    private fun observeNetwork() {
        launchJob("observe_network") {
            networkService.isOnline.collect { online ->
                updateState { it.copy(isSystemOnline = online) }
            }
        }
        launchJob("observe_airplane_mode") {
            networkService.isAirplaneModeActive.collect { active ->
                updateState { it.copy(isAirplaneMode = active) }
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
                                (safeRunCatching { membershipsDeferred?.await() }.getOrNull() to safeRunCatching { adminGroupsDeferred?.await() }.getOrNull())
                            )
                        }
                    }

                    if (results != null) {
                        val (analyticsRes, settingsRes, userRes) = results
                        val (membershipsRes, adminGroupsRes) = userRes

                        var isMemberOrAdmin = false
                        var pendingGroup: Group? = null

                        if (userId != null) {
                            membershipsRes?.onSuccess { if (it.isNotEmpty()) isMemberOrAdmin = true }
                            adminGroupsRes?.onSuccess { groups ->
                                if (groups.isNotEmpty()) isMemberOrAdmin = true
                                pendingGroup = groups.find { !it.registrationPaid }
                            }
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
                            val error = analyticsRes.exceptionOrNull()?.toUserMessage() ?: settingsRes.exceptionOrNull()?.toUserMessage() ?: "Sync failed"
                            updateState {
                                it.copy(
                                    analytics = if (analyticsRes.isFailure) AsyncResult.Error(error, "SyncError") else AsyncResult.Success(analyticsRes.getOrThrow()),
                                    settings = if (settingsRes.isFailure) AsyncResult.Error(error, "SyncError") else AsyncResult.Success(settingsRes.getOrThrow()),
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
