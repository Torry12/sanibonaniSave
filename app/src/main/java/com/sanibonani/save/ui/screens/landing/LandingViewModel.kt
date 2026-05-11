package com.sanibonani.save.ui.screens.landing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanibonani.save.analytics.AnalyticsTaxonomy
import com.sanibonani.save.analytics.AppAnalytics
import com.sanibonani.save.data.utils.toUserMessage
import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.PlatformFees
import com.sanibonani.save.domain.model.PlatformAnalytics
import com.sanibonani.save.domain.model.UserRole
import com.sanibonani.save.domain.repository.GroupRepository
import com.sanibonani.save.domain.repository.MemberRepository
import com.sanibonani.save.domain.repository.PlatformRepository
import com.sanibonani.save.domain.repository.SupabaseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LandingUiState(
    val analytics: PlatformAnalytics = PlatformAnalytics(),
    val settings: Map<String, Double> = emptyMap(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val isMemberOrAdmin: Boolean = false,
    val isQualifyingPlatformAdmin: Boolean = false,
    val isLoggedIn: Boolean = false,
    val userRole: UserRole = UserRole.MEMBER,
    val pendingGroup: Group? = null
)

@HiltViewModel
class LandingViewModel @Inject constructor(
    private val platformRepository: PlatformRepository,
    private val memberRepository: MemberRepository,
    private val groupRepository: GroupRepository,
    private val supabaseRepository: SupabaseRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LandingUiState())
    val uiState: StateFlow<LandingUiState> = _uiState.asStateFlow()

    // init {
    //     refreshData()
    // }

    fun refreshData() {
        viewModelScope.launch {
            AppAnalytics.track(AnalyticsTaxonomy.Events.LANDING_REFRESH_STARTED)
            _uiState.update { it.copy(isLoading = true, error = null) }

            val analyticsResult = platformRepository.getPlatformAnalytics()
            val settingsResult = platformRepository.getPlatformSettings()
            
            var isMemberOrAdmin = false
            var isQualifyingPlatformAdmin = false
            var userRole = UserRole.MEMBER
            var pendingGroup: Group? = null
            val isLoggedIn = supabaseRepository.isLoggedIn
            val userId = supabaseRepository.currentUserId
            val email = supabaseRepository.currentSessionEmail
            
            if (userId != null) {
                // Check if user is the qualifying platform admin
                isQualifyingPlatformAdmin = com.sanibonani.save.domain.utils.PlatformAdminAuthPolicy.isPlatformAdminEmail(email)
                userRole = supabaseRepository.getUserRole()

                val membershipsResult = memberRepository.getMemberships(userId)
                if (membershipsResult.isSuccess) {
                    val memberships = membershipsResult.getOrThrow()
                    if (memberships.isNotEmpty()) {
                        isMemberOrAdmin = true
                    }
                }
                
                // Check for pending registration
                val managedGroupsResult = groupRepository.getGroupsByAdmin(userId)
                if (managedGroupsResult.isSuccess) {
                    pendingGroup = managedGroupsResult.getOrThrow().find { !it.registrationPaid }
                }
            }

            when {
                analyticsResult.isFailure -> {
                    val message = analyticsResult.exceptionOrNull()?.toUserMessage()
                        ?: "Failed to load platform data. Please check your connection and try again."
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isMemberOrAdmin = isMemberOrAdmin,
                            isQualifyingPlatformAdmin = isQualifyingPlatformAdmin,
                            isLoggedIn = isLoggedIn,
                            userRole = userRole,
                            pendingGroup = pendingGroup,
                            error = message
                        )
                    }
                    AppAnalytics.track(
                        AnalyticsTaxonomy.Events.LANDING_REFRESH_FAILURE,
                        mapOf(AnalyticsTaxonomy.Params.ERROR_TYPE to "analytics")
                    )
                }
                settingsResult.isFailure -> {
                    val message = settingsResult.exceptionOrNull()?.toUserMessage()
                        ?: "Failed to load settings. Please check your connection and try again."
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isMemberOrAdmin = isMemberOrAdmin,
                            isQualifyingPlatformAdmin = isQualifyingPlatformAdmin,
                            isLoggedIn = isLoggedIn,
                            userRole = userRole,
                            pendingGroup = pendingGroup,
                            error = message
                        )
                    }
                    AppAnalytics.track(
                        AnalyticsTaxonomy.Events.LANDING_REFRESH_FAILURE,
                        mapOf(AnalyticsTaxonomy.Params.ERROR_TYPE to "settings")
                    )
                }
                analyticsResult.isSuccess && settingsResult.isSuccess -> {
                    val settings = settingsResult.getOrThrow()

                    // Update global singleton for consistency across the app
                    settings["registration_fee"]?.let { PlatformFees.REGISTRATION = it }
                    (settings["monthly_member_fee"] ?: settings["monthly_per_member"])
                        ?.let { PlatformFees.MONTHLY_MEMBER_FEE = it }

                    _uiState.update {
                        LandingUiState(
                            analytics = analyticsResult.getOrThrow(),
                            settings = settings,
                            isLoading = false,
                            isMemberOrAdmin = isMemberOrAdmin,
                            isQualifyingPlatformAdmin = isQualifyingPlatformAdmin,
                            isLoggedIn = isLoggedIn,
                            userRole = userRole,
                            pendingGroup = pendingGroup,
                            error = null
                        )
                    }
                    AppAnalytics.track(
                        AnalyticsTaxonomy.Events.LANDING_REFRESH_SUCCESS,
                        mapOf(AnalyticsTaxonomy.Params.ROLE to userRole.name.lowercase())
                    )
                }
                else -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isMemberOrAdmin = isMemberOrAdmin,
                            isQualifyingPlatformAdmin = isQualifyingPlatformAdmin,
                            isLoggedIn = isLoggedIn,
                            userRole = userRole,
                            pendingGroup = pendingGroup,
                            error = "Unable to load platform data. Please try again."
                        )
                    }
                    AppAnalytics.track(
                        AnalyticsTaxonomy.Events.LANDING_REFRESH_FAILURE,
                        mapOf(AnalyticsTaxonomy.Params.ERROR_TYPE to "unknown")
                    )
                }
            }
        }
    }
}
