package com.sanibonani.save.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanibonani.save.data.utils.toUserMessage
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.BehaviorTrackingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BehaviorTrackingUiState(
    val memberBehavior: MemberBehaviorTrack? = null,
    val groupMembers: List<MemberBehaviorTrack> = emptyList(),
    val highRiskMembers: List<MemberBehaviorTrack> = emptyList(),
    val flaggedMembers: List<MemberBehaviorTrack> = emptyList(),
    val fraudEvents: List<FraudDetectionEvent> = emptyList(),
    val analytics: BehaviorAnalyticsSummary? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

/**
 * ViewModel for member behavior tracking and fraud detection
 * Provides reactive access to behavioral metrics and fraud risk indicators
 */
@HiltViewModel
class BehaviorTrackingViewModel @Inject constructor(
    private val repository: BehaviorTrackingRepository
) : ViewModel() {

    private val _state = MutableStateFlow(BehaviorTrackingUiState())
    val state: StateFlow<BehaviorTrackingUiState> = _state.asStateFlow()
    private var groupMembersJob: Job? = null
    private var highRiskMembersJob: Job? = null
    private var flaggedMembersJob: Job? = null
    private var fraudEventsJob: Job? = null

    private val isActive = MutableStateFlow(false)

    fun setActive(active: Boolean) {
        isActive.value = active
        if (!active) {
            groupMembersJob?.cancel()
            highRiskMembersJob?.cancel()
            flaggedMembersJob?.cancel()
            fraudEventsJob?.cancel()
        }
    }

    /**
     * Load behavior tracking data for a specific member
     */
    fun loadMemberBehavior(memberId: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            repository.getMemberBehavior(memberId)
                .onSuccess { track ->
                    _state.update { it.copy(memberBehavior = track, isLoading = false) }
                }
                .onFailure { e ->
                    _state.update { it.copy(error = e.toUserMessage(), isLoading = false) }
                }
        }
    }

    /**
     * Load behavior tracking by member ID number
     */
    fun loadMemberBehaviorByIdNumber(idNumber: String, groupId: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            repository.getMemberBehaviorByIdNumber(idNumber, groupId)
                .onSuccess { track ->
                    _state.update { it.copy(memberBehavior = track, isLoading = false) }
                }
                .onFailure { e ->
                    _state.update { it.copy(error = e.toUserMessage(), isLoading = false) }
                }
        }
    }

    /**
     * Observe behavior data for all members in a group
     */
    fun observeGroupMembersBehavior(groupId: String) {
        groupMembersJob?.cancel()
        groupMembersJob = viewModelScope.launch {
            repository.observeGroupMembersBehavior(groupId).collect { result ->
                result.onSuccess { members ->
                    _state.update { it.copy(groupMembers = members, error = null) }
                }
                .onFailure { e ->
                    _state.update { it.copy(error = e.toUserMessage()) }
                }
            }
        }
    }

    /**
     * Observe high-risk members in a group
     */
    fun observeHighRiskMembers(groupId: String) {
        highRiskMembersJob?.cancel()
        highRiskMembersJob = viewModelScope.launch {
            repository.observeHighRiskMembers(groupId).collect { result ->
                result.onSuccess { members ->
                    _state.update { it.copy(highRiskMembers = members, error = null) }
                }
                .onFailure { e ->
                    _state.update { it.copy(error = e.toUserMessage()) }
                }
            }
        }
    }

    /**
     * Observe flagged members in a group
     */
    fun observeFlaggedMembers(groupId: String) {
        flaggedMembersJob?.cancel()
        flaggedMembersJob = viewModelScope.launch {
            repository.observeFlaggedMembers(groupId).collect { result ->
                result.onSuccess { members ->
                    _state.update { it.copy(flaggedMembers = members, error = null) }
                }
                .onFailure { e ->
                    _state.update { it.copy(error = e.toUserMessage()) }
                }
            }
        }
    }

    /**
     * Observe fraud detection events for a member
     */
    fun observeFraudEvents(memberId: String) {
        fraudEventsJob?.cancel()
        fraudEventsJob = viewModelScope.launch {
            repository.observeFraudEventsByMember(memberId).collect { result ->
                result.onSuccess { events ->
                    _state.update { it.copy(fraudEvents = events, error = null) }
                }
                .onFailure { e ->
                    _state.update { it.copy(error = e.toUserMessage()) }
                }
            }
        }
    }

    /**
     * Calculate and update member behavior scores
     */
    fun calculateMemberBehavior(memberId: String, groupId: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            repository.calculateAndUpdateMemberBehavior(memberId, groupId)
                .onSuccess { track ->
                    _state.update { it.copy(memberBehavior = track, isLoading = false) }
                }
                .onFailure { e ->
                    _state.update { it.copy(error = e.toUserMessage(), isLoading = false) }
                }
        }
    }

    /**
     * Recalculate all behavior scores for a group
     */
    fun recalculateGroupScores(groupId: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            repository.recalculateGroupBehaviorScores(groupId)
                .onSuccess {
                    _state.update { it.copy(isLoading = false) }
                }
                .onFailure { e ->
                    _state.update { it.copy(error = e.toUserMessage(), isLoading = false) }
                }
        }
    }

    /**
     * Flag a member for review
     */
    fun flagMemberForReview(memberId: String, reason: String, notes: String? = null) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            repository.flagMemberForReview(memberId, reason, notes)
                .onSuccess {
                    repository.getMemberBehavior(memberId)
                        .onSuccess { track ->
                            _state.update { it.copy(memberBehavior = track, isLoading = false) }
                        }
                        .onFailure { e ->
                            _state.update { it.copy(error = e.toUserMessage(), isLoading = false) }
                        }
                }
                .onFailure { e ->
                    _state.update { it.copy(error = e.toUserMessage(), isLoading = false) }
                }
        }
    }

    /**
     * Unflag a member from review
     */
    fun unflagMember(memberId: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            repository.unflagMember(memberId)
                .onSuccess {
                    repository.getMemberBehavior(memberId)
                        .onSuccess { track ->
                            _state.update { it.copy(memberBehavior = track, isLoading = false) }
                        }
                        .onFailure { e ->
                            _state.update { it.copy(error = e.toUserMessage(), isLoading = false) }
                        }
                }
                .onFailure { e ->
                    _state.update { it.copy(error = e.toUserMessage(), isLoading = false) }
                }
        }
    }

    /**
     * Suspend a member
     */
    fun suspendMember(memberId: String, reason: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            repository.suspendMember(memberId, reason)
                .onSuccess {
                    repository.getMemberBehavior(memberId)
                        .onSuccess { track ->
                            _state.update { it.copy(memberBehavior = track, isLoading = false) }
                        }
                        .onFailure { e ->
                            _state.update { it.copy(error = e.toUserMessage(), isLoading = false) }
                        }
                }
                .onFailure { e ->
                    _state.update { it.copy(error = e.toUserMessage(), isLoading = false) }
                }
        }
    }

    /**
     * Unsuspend a member
     */
    fun unsuspendMember(memberId: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            repository.unsuspendMember(memberId)
                .onSuccess {
                    repository.getMemberBehavior(memberId)
                        .onSuccess { track ->
                            _state.update { it.copy(memberBehavior = track, isLoading = false) }
                        }
                        .onFailure { e ->
                            _state.update { it.copy(error = e.toUserMessage(), isLoading = false) }
                        }
                }
                .onFailure { e ->
                    _state.update { it.copy(error = e.toUserMessage(), isLoading = false) }
                }
        }
    }

    /**
     * Load behavior analytics for a group
     */
    fun loadBehaviorAnalytics(groupId: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            repository.calculateBehaviorAnalytics(groupId)
                .onSuccess { analytics ->
                    _state.update { it.copy(analytics = analytics, isLoading = false) }
                }
                .onFailure { e ->
                    _state.update { it.copy(error = e.toUserMessage(), isLoading = false) }
                }
        }
    }

    /**
     * Record a fraud event
     */
    fun recordFraudEvent(event: FraudDetectionEvent) {
        viewModelScope.launch {
            repository.recordFraudEvent(event)
                .onFailure { e ->
                    _state.update { it.copy(error = e.toUserMessage()) }
                }
        }
    }

    /**
     * Resolve a fraud event
     */
    fun resolveFraudEvent(eventId: String, actionTaken: String) {
        viewModelScope.launch {
            repository.resolveFraudEvent(eventId, actionTaken)
                .onFailure { e ->
                    _state.update { it.copy(error = e.toUserMessage()) }
                }
        }
    }

    /**
     * Clear any errors
     */
    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    override fun onCleared() {
        super.onCleared()
        groupMembersJob?.cancel()
        highRiskMembersJob?.cancel()
        flaggedMembersJob?.cancel()
        fraudEventsJob?.cancel()
        _state.update { BehaviorTrackingUiState() }
    }
}
