package com.sanibonani.save.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanibonani.save.analytics.AnalyticsTaxonomy
import com.sanibonani.save.analytics.AppAnalytics
import com.sanibonani.save.data.utils.toUserMessage
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.*
import com.sanibonani.save.domain.usecase.ProcessPayoutUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

/**
 * UI state for the Platform Admin portal.
 * Tracks analytics, group management, payouts, and global settings.
 * Updated via StateFlow for reactive UI.
 */
data class PlatformAdminUiState(
    val analytics: PlatformAnalytics = PlatformAnalytics(),
    val groups: List<Group> = emptyList(),
    val payments: List<com.sanibonani.save.domain.model.Payment> = emptyList(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    val error: String? = null,
    val selectedTab: Int = 0,
    val searchQuery: String = "",
    // Global Settings
    val memberCharge: String = "10.0",
    val registrationFee: String = "700.0",
    // Group Management
    val selectedGroupMetrics: com.sanibonani.save.domain.model.ActuarialMetrics? = null,
    val isSuspending: Boolean = false,
    // Payouts
    val payouts: List<PayoutRequest> = emptyList(),
    val isProcessingPayout: Boolean = false
)

/**
 * ViewModel for the Platform Admin portal.
 * Handles platform-wide analytics, group management, payout processing, and global fee settings.
 * Uses StateFlow for state, Hilt for DI, and robust error handling.
 */
@HiltViewModel
class PlatformAdminViewModel @Inject constructor(
    private val platformRepo: PlatformRepository,
    private val payoutRepo: PayoutRepository,
    private val processPayoutUseCase: ProcessPayoutUseCase,
    private val supabaseRepo: SupabaseRepository
) : ViewModel() {

    private val _state = MutableStateFlow(PlatformAdminUiState())
    val state: StateFlow<PlatformAdminUiState> = _state.asStateFlow()

    init {
        loadData()
        loadSettings()
    }

    fun loadData() {
        viewModelScope.launch {
            AppAnalytics.track(AnalyticsTaxonomy.Events.PLATFORM_DASHBOARD_LOAD_STARTED)
            _state.update { it.copy(isLoading = true, error = null) }
            
            // Run independent fetches in parallel for efficiency
            val analyticsDeferred = async { platformRepo.getPlatformAnalytics() }
            val groupsDeferred = async { platformRepo.getAllGroups() }
            val paymentsDeferred = async { platformRepo.getPlatformPayments() }
            val payoutsDeferred = async { payoutRepo.getPendingPayouts() }

            val analyticsResult = analyticsDeferred.await()
            val groupsResult = groupsDeferred.await()
            val paymentsResult = paymentsDeferred.await()
            val payoutsResult = payoutsDeferred.await()

            if (analyticsResult.isSuccess && groupsResult.isSuccess) {
                _state.update { it.copy(
                    analytics = analyticsResult.getOrThrow(),
                    groups = groupsResult.getOrThrow(),
                    payments = paymentsResult.getOrDefault(emptyList()),
                    payouts = payoutsResult.getOrDefault(emptyList()),
                    isLoading = false
                ) }
                AppAnalytics.track(AnalyticsTaxonomy.Events.PLATFORM_DASHBOARD_LOAD_SUCCESS)
            } else {
                val error = (analyticsResult.exceptionOrNull() ?: groupsResult.exceptionOrNull())
                    ?.toUserMessage()
                    ?: "Unable to load platform data. Please try again."
                _state.update { it.copy(isLoading = false, error = error) }
                AppAnalytics.track(
                    AnalyticsTaxonomy.Events.PLATFORM_DASHBOARD_LOAD_FAILURE,
                    mapOf(AnalyticsTaxonomy.Params.ERROR_TYPE to "load_failed")
                )
            }
        }
    }

    private fun loadSettings() {
        viewModelScope.launch {
            platformRepo.getPlatformSettings()
                .onSuccess { settings ->
                    val mCharge = settings["monthly_member_fee"] ?: settings["monthly_per_member"] ?: 10.0
                    val rFee = settings["registration_fee"] ?: 700.0
                    
                    // Update global singleton for system-wide effect
                    com.sanibonani.save.domain.model.PlatformFees.MONTHLY_MEMBER_FEE = mCharge
                    com.sanibonani.save.domain.model.PlatformFees.REGISTRATION = rFee

                    _state.update { it.copy(
                        memberCharge = String.format(Locale.US, "%.2f", mCharge),
                        registrationFee = String.format(Locale.US, "%.2f", rFee)
                    ) }
                }
                .onFailure { e ->
                    _state.update { it.copy(error = e.toUserMessage()) }
                }
        }
    }

    private fun updateGroupSuspensionState(groupId: String, isSuspended: Boolean, feeStatus: AdminFeeState) {
        _state.update { state ->
            state.copy(
                groups = state.groups.map { group ->
                    if (group.id == groupId) {
                        group.copy(
                            isPlatformSuspended = isSuspended,
                            feeStatus = feeStatus
                        )
                    } else {
                        group
                    }
                }
            )
        }
    }

    fun unsuspendGroup(groupId: String) {
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            AppAnalytics.track(
                AnalyticsTaxonomy.Events.PLATFORM_GROUP_UNSUSPEND_REQUESTED,
                mapOf(AnalyticsTaxonomy.Params.GROUP_ID to groupId)
            )
            platformRepo.unsuspendGroup(groupId)
                .onSuccess {
                    updateGroupSuspensionState(
                        groupId = groupId,
                        isSuspended = false,
                        feeStatus = AdminFeeState.PAID
                    )
                    _state.update { it.copy(isSaving = false, error = null) }
                    AppAnalytics.track(
                        AnalyticsTaxonomy.Events.PLATFORM_GROUP_UNSUSPEND_SUCCESS,
                        mapOf(AnalyticsTaxonomy.Params.GROUP_ID to groupId)
                    )
                }
                .onFailure { e ->
                    _state.update { it.copy(isSaving = false, error = e.toUserMessage()) }
                    AppAnalytics.track(
                        AnalyticsTaxonomy.Events.PLATFORM_GROUP_UNSUSPEND_FAILURE,
                        mapOf(
                            AnalyticsTaxonomy.Params.GROUP_ID to groupId,
                            AnalyticsTaxonomy.Params.ERROR_TYPE to "repo"
                        )
                    )
                }
        }
    }

    fun suspendGroup(groupId: String, reason: String) {
        viewModelScope.launch {
            _state.update { it.copy(isSuspending = true) }
            AppAnalytics.track(
                AnalyticsTaxonomy.Events.PLATFORM_GROUP_SUSPEND_REQUESTED,
                mapOf(AnalyticsTaxonomy.Params.GROUP_ID to groupId)
            )
            platformRepo.suspendGroup(groupId, reason)
                .onSuccess {
                    updateGroupSuspensionState(
                        groupId = groupId,
                        isSuspended = true,
                        feeStatus = AdminFeeState.SUSPENDED
                    )
                    _state.update { it.copy(isSuspending = false, error = null) }
                    AppAnalytics.track(
                        AnalyticsTaxonomy.Events.PLATFORM_GROUP_SUSPEND_SUCCESS,
                        mapOf(AnalyticsTaxonomy.Params.GROUP_ID to groupId)
                    )
                }
                .onFailure { e ->
                    _state.update { it.copy(isSuspending = false, error = e.toUserMessage()) }
                    AppAnalytics.track(
                        AnalyticsTaxonomy.Events.PLATFORM_GROUP_SUSPEND_FAILURE,
                        mapOf(
                            AnalyticsTaxonomy.Params.GROUP_ID to groupId,
                            AnalyticsTaxonomy.Params.ERROR_TYPE to "repo"
                        )
                    )
                }
        }
    }

    fun fetchGroupMetrics(groupId: String) {
        viewModelScope.launch {
            platformRepo.getGroupMetrics(groupId).onSuccess { metrics ->
                _state.update { it.copy(selectedGroupMetrics = metrics) }
            }
        }
    }

    fun updateMemberCharge(value: String) {
        _state.update { it.copy(memberCharge = value) }
    }

    fun updateRegistrationFee(value: String) {
        _state.update { it.copy(registrationFee = value) }
    }

    fun saveGlobalFees() {
        viewModelScope.launch {
            val charge = state.value.memberCharge.toDoubleOrNull()
            if (charge == null) {
                _state.update { it.copy(error = "Please enter a valid monthly charge.") }
                return@launch
            }
            val regFee = state.value.registrationFee.toDoubleOrNull()
            if (regFee == null) {
                _state.update { it.copy(error = "Please enter a valid registration fee.") }
                return@launch
            }

            _state.update { it.copy(isSaving = true, saveSuccess = false) }
            
            platformRepo.updateGlobalFees(charge, regFee)
                .onSuccess {
                    // Update global singleton for immediate effect across system
                    com.sanibonani.save.domain.model.PlatformFees.MONTHLY_MEMBER_FEE = charge
                    com.sanibonani.save.domain.model.PlatformFees.REGISTRATION = regFee

                    _state.update { it.copy(isSaving = false, saveSuccess = true) }
                }
                .onFailure { e ->
                    _state.update { it.copy(isSaving = false, error = e.toUserMessage()) }
                }
        }
    }

    fun setTab(index: Int) {
        _state.update { it.copy(selectedTab = index, saveSuccess = false, error = null) }
    }

    fun updateSearchQuery(query: String) {
        _state.update { it.copy(searchQuery = query) }
    }
    
    fun resetLocalData() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            runCatching { supabaseRepo.resetLocalCache() }
                .onSuccess {
                    _state.update { it.copy(isLoading = false, saveSuccess = true, error = null) }
                }
                .onFailure { e ->
                    _state.update { it.copy(isLoading = false, saveSuccess = false, error = e.toUserMessage()) }
                }
        }
    }

    fun dismissSuccess() {
        _state.update { it.copy(saveSuccess = false) }
    }

    fun approvePayout(payoutId: String, groupId: String) {
        processPayout(payoutId, groupId, PayoutStatus.PROCESSING)
    }

    fun completePayout(payoutId: String, groupId: String) {
        processPayout(payoutId, groupId, PayoutStatus.COMPLETED)
    }

    fun rejectPayout(payoutId: String, groupId: String) {
        processPayout(payoutId, groupId, PayoutStatus.FAILED)
    }

    private fun processPayout(payoutId: String, groupId: String, status: PayoutStatus) {
        viewModelScope.launch {
            _state.update { it.copy(isProcessingPayout = true) }
            AppAnalytics.track(
                AnalyticsTaxonomy.Events.PLATFORM_PAYOUT_TRANSITION_REQUESTED,
                mapOf(
                    AnalyticsTaxonomy.Params.PAYOUT_ID to payoutId,
                    AnalyticsTaxonomy.Params.GROUP_ID to groupId,
                    AnalyticsTaxonomy.Params.STATUS to status.name.lowercase()
                )
            )
            processPayoutUseCase(payoutId, groupId, status)
                .onSuccess {
                    _state.update { it.copy(isProcessingPayout = false) }
                    AppAnalytics.track(
                        AnalyticsTaxonomy.Events.PLATFORM_PAYOUT_TRANSITION_SUCCESS,
                        mapOf(
                            AnalyticsTaxonomy.Params.PAYOUT_ID to payoutId,
                            AnalyticsTaxonomy.Params.GROUP_ID to groupId,
                            AnalyticsTaxonomy.Params.STATUS to status.name.lowercase()
                        )
                    )
                    loadData()
                }
                .onFailure { e ->
                    _state.update { it.copy(isProcessingPayout = false, error = e.toUserMessage()) }
                    AppAnalytics.track(
                        AnalyticsTaxonomy.Events.PLATFORM_PAYOUT_TRANSITION_FAILURE,
                        mapOf(
                            AnalyticsTaxonomy.Params.PAYOUT_ID to payoutId,
                            AnalyticsTaxonomy.Params.GROUP_ID to groupId,
                            AnalyticsTaxonomy.Params.STATUS to status.name.lowercase(),
                            AnalyticsTaxonomy.Params.ERROR_TYPE to "usecase"
                        )
                    )
                }
        }
    }

    fun logAudit(action: String, targetMemberId: String? = null, targetGroupId: String? = null, details: Map<String, Any>? = null) {
        viewModelScope.launch {
            val actorId = supabaseRepo.currentUserId ?: "SYSTEM"
            val auditLog = AuditLog(
                actorId = actorId,
                targetMemberId = targetMemberId,
                targetGroupId = targetGroupId,
                action = action,
                details = details
            )
            platformRepo.logAuditEvent(auditLog)
        }
    }
}
