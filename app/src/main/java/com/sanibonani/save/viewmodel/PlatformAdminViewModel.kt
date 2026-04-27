package com.sanibonani.save.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanibonani.save.data.utils.toUserMessage
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.*
import com.sanibonani.save.domain.usecase.ProcessPayoutUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
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
            _state.update { it.copy(isLoading = true, error = null) }
            
            val analyticsResult = platformRepo.getPlatformAnalytics()
            val groupsResult = platformRepo.getAllGroups()
            val paymentsResult = platformRepo.getPlatformPayments()
            val payoutsResult = payoutRepo.getPendingPayouts()

            if (analyticsResult.isSuccess && groupsResult.isSuccess) {
                _state.update { it.copy(
                    analytics = analyticsResult.getOrThrow(),
                    groups = groupsResult.getOrThrow(),
                    payments = paymentsResult.getOrDefault(emptyList()),
                    payouts = payoutsResult.getOrDefault(emptyList()),
                    isLoading = false
                ) }
            } else {
                val error = (analyticsResult.exceptionOrNull() ?: groupsResult.exceptionOrNull())
                    ?.toUserMessage()
                    ?: "Unable to load platform data. Please try again."
                _state.update { it.copy(isLoading = false, error = error) }
            }
        }
    }

    private fun loadSettings() {
        viewModelScope.launch {
            platformRepo.getPlatformSettings().onSuccess { settings ->
                val mCharge = settings["monthly_per_member"] ?: 10.0
                val rFee = settings["registration_fee"] ?: 700.0
                
                // Update global singleton for system-wide effect
                com.sanibonani.save.domain.model.PlatformFees.MONTHLY_PER_MEMBER = mCharge
                com.sanibonani.save.domain.model.PlatformFees.REGISTRATION = rFee

                _state.update { it.copy(
                    memberCharge = mCharge.toString(),
                    registrationFee = rFee.toString()
                ) }
            }
        }
    }

    fun unsuspendGroup(groupId: String) {
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            platformRepo.unsuspendGroup(groupId)
                .onSuccess {
                    _state.update { it.copy(isSaving = false) }
                    loadData() // Refresh list
                }
                .onFailure { e ->
                    _state.update { it.copy(isSaving = false, error = e.toUserMessage()) }
                }
        }
    }

    fun suspendGroup(groupId: String, reason: String) {
        viewModelScope.launch {
            _state.update { it.copy(isSuspending = true) }
            platformRepo.suspendGroup(groupId, reason)
                .onSuccess {
                    _state.update { it.updateGroupStatus(groupId, true) }
                    _state.update { it.copy(isSuspending = false) }
                }
                .onFailure { e ->
                    _state.update { it.copy(isSuspending = false, error = e.toUserMessage()) }
                }
        }
    }

    private fun PlatformAdminUiState.updateGroupStatus(groupId: String, suspended: Boolean): PlatformAdminUiState {
        return copy(groups = groups.map { 
            if (it.id == groupId) it.copy(isPlatformSuspended = suspended) else it 
        })
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
                    com.sanibonani.save.domain.model.PlatformFees.MONTHLY_PER_MEMBER = charge
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
            processPayoutUseCase(payoutId, groupId, status)
                .onSuccess {
                    _state.update { it.copy(isProcessingPayout = false) }
                    loadData()
                }
                .onFailure { e ->
                    _state.update { it.copy(isProcessingPayout = false, error = e.toUserMessage()) }
                }
        }
    }
}
