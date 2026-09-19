package com.sanibonani.save.features.landing

import androidx.lifecycle.viewModelScope
import com.sanibonani.save.analytics.AnalyticsTaxonomy
import com.sanibonani.save.analytics.AppAnalytics
import com.sanibonani.save.domain.logging.AppLogger
import com.sanibonani.save.domain.utils.toUserMessage
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.equity.*
import com.sanibonani.save.domain.repository.system.*
import com.sanibonani.save.domain.repository.treasury.*
import com.sanibonani.save.viewmodel.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class LandingUiState(
    val analytics: AsyncResult<PlatformAnalytics> = AsyncResult.Uninitialized,
    val settings: AsyncResult<Map<String, Double>> = AsyncResult.Uninitialized,
    val isMemberOrAdmin: Boolean = false,
    val isLoggedIn: Boolean = false,
    val userRole: UserRole = UserRole.MEMBER,
    val pendingGroup: Group? = null,
    val isSystemOnline: Boolean = true,
    val isAirplaneMode: Boolean = false,
    val activeSupabaseUrl: String = ""
)

sealed interface LandingIntent {
    object Refresh : LandingIntent
}

@HiltViewModel
class LandingViewModel @Inject constructor(
    private val platformRepository: PlatformRepository,
    private val memberRepository: MemberRepository,
    private val groupRepository: GroupRepository,
    val supabaseRepository: SupabaseRepository,
    val platformConfigRepository: PlatformConfigRepository,
    private val networkService: com.sanibonani.save.domain.service.NetworkConnectivityService,
    val devSettings: com.sanibonani.save.domain.utils.DevSettings
) : BaseViewModel<LandingUiState>(LandingUiState()) {

    val state: StateFlow<LandingUiState> = uiState
    private var refreshJob: Job? = null

    init {
        observeNetwork()
        observeActiveUrl()
        observeSession()
    }

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    private fun observeSession() {
        viewModelScope.launch {
            supabaseRepository.sessionFlow
                .distinctUntilChanged { old, new -> old?.accessToken == new?.accessToken }
                .debounce(300)
                .collect { session ->
                    val isLoggedIn = session != null
                    if (isLoggedIn == uiState.value.isLoggedIn && uiState.value.userRole != UserRole.MEMBER) {
                        return@collect
                    }

                    val userRole = if (isLoggedIn) {
                        runCatching { supabaseRepository.getUserRole() }.getOrDefault(UserRole.MEMBER)
                    } else UserRole.MEMBER

                    updateState { it.copy(isLoggedIn = isLoggedIn, userRole = userRole) }
                    if (isLoggedIn) {
                        refreshData()
                    }
                }
        }
    }

    private fun observeActiveUrl() {
        viewModelScope.launch {
            updateState { it.copy(activeSupabaseUrl = supabaseRepository.supabaseUrl) }

            devSettings.settingsChanged.collect {
                delay(500)
                updateState { it.copy(activeSupabaseUrl = supabaseRepository.supabaseUrl) }
                refreshData()
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

    fun onIntent(intent: LandingIntent) {
        when (intent) {
            LandingIntent.Refresh -> refreshData()
        }
    }

    fun refreshData() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            try {
                AppAnalytics.track(AnalyticsTaxonomy.Events.LANDING_REFRESH_STARTED)
                updateState { it.copy(analytics = AsyncResult.Loading, settings = AsyncResult.Loading) }

                val isLoggedIn = supabaseRepository.isLoggedIn
                val userId = supabaseRepository.currentUserId

                AppLogger.d("LandingVM", "🔄 Refreshing landing data. LoggedIn=$isLoggedIn, UserID=${userId?.take(5)}")

                val results = withTimeoutOrNull(12_000) {
                    val analyticsDeferred = async(Dispatchers.IO) { platformRepository.getPlatformAnalytics() }
                    val settingsDeferred = async(Dispatchers.IO) { platformRepository.getPlatformSettings() }

                    val membershipsDeferred = if (userId != null) {
                        async(Dispatchers.IO) { memberRepository.getMemberships(userId) }
                    } else null

                    val adminGroupsDeferred = if (userId != null) {
                        async(Dispatchers.IO) { groupRepository.getGroupsByAdmin(userId) }
                    } else null

                    Triple(analyticsDeferred.await(), settingsDeferred.await(), membershipsDeferred?.await() to adminGroupsDeferred?.await())
                }

                if (results == null) {
                    val error = "Connection timeout. Please check your internet."
                    updateState { it.copy(analytics = AsyncResult.Error(error), settings = AsyncResult.Error(error)) }
                    return@launch
                }

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
                    val currentConfig = platformConfigRepository.current()
                    val regFee = settings["registration_fee"] ?: currentConfig.registrationFee.toDouble()
                    val monthlyFee = settings["monthly_member_fee"] ?: settings["monthly_per_member"] ?: currentConfig.monthlyMemberFee.toDouble()

                    platformConfigRepository.update(monthlyMemberFee = Money(monthlyFee), registrationFee = Money(regFee))

                    updateState {
                        it.copy(
                            analytics = AsyncResult.Success(analyticsRes.getOrThrow()),
                            settings = AsyncResult.Success(settings),
                            isLoggedIn = isLoggedIn,
                            isMemberOrAdmin = isMemberOrAdmin,
                            pendingGroup = pendingGroup
                        )
                    }
                    AppAnalytics.track(AnalyticsTaxonomy.Events.LANDING_REFRESH_SUCCESS)
                } else {
                    val error = analyticsRes.exceptionOrNull()?.toUserMessage() ?: settingsRes.exceptionOrNull()?.toUserMessage() ?: "Sync failed"
                    updateState {
                        it.copy(
                            analytics = if (analyticsRes.isFailure) AsyncResult.Error(error, analyticsRes.exceptionOrNull()) else AsyncResult.Success(analyticsRes.getOrThrow()),
                            settings = if (settingsRes.isFailure) AsyncResult.Error(error, settingsRes.exceptionOrNull()) else AsyncResult.Success(settingsRes.getOrThrow()),
                            isLoggedIn = isLoggedIn,
                            isMemberOrAdmin = isMemberOrAdmin,
                            pendingGroup = pendingGroup
                        )
                    }
                }

            } catch (e: Exception) {
                if (e is CancellationException) throw e
                val error = e.toUserMessage()
                updateState { it.copy(analytics = AsyncResult.Error(error, e), settings = AsyncResult.Error(error, e)) }
            }
        }
    }
}
