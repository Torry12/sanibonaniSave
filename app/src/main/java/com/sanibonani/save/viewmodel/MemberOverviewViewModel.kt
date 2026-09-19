package com.sanibonani.save.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanibonani.save.analytics.AppAnalytics
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.*
import com.sanibonani.save.domain.service.MemberActuarialService
import com.sanibonani.save.service.MemberGroupContextCacheService
import com.sanibonani.save.viewmodel.state.MemberUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MemberOverviewViewModel @Inject constructor(
    private val memberContextCacheService: MemberGroupContextCacheService,
    private val memberRepo: MemberRepository,
    private val groupRepo: GroupRepository,
    private val actuarialService: MemberActuarialService,
    private val supabaseRepo: SupabaseRepository
) : ViewModel() {

    private val _state = MutableStateFlow(MemberUiState())
    val state: StateFlow<MemberUiState> = _state.asStateFlow()

    private var contextObservationJob: Job? = null

    init {
        observeContext()
        loadMemberships()
    }

    private fun observeContext() {
        contextObservationJob?.cancel()
        contextObservationJob = viewModelScope.launch {
            memberContextCacheService.contexts.collect { contexts ->
                val selectedId = _state.value.selectedGroupId
                val context = contexts[selectedId] ?: return@collect

                _state.update { it.copy(
                    member = context.member,
                    group = context.group,
                    contributions = context.contributions,
                    paymentCalculation = context.calculation,
                    notifications = context.notifications,
                    messages = context.messages,
                    isLoading = !context.isFullyHydrated
                ) }
            }
        }
    }

    private fun loadMemberships() {
        viewModelScope.launch {
            val userId = supabaseRepo.currentUserId ?: return@launch
            memberRepo.getMemberships(userId).onSuccess { memberships ->
                _state.update { it.copy(memberships = memberships) }
                if (_state.value.selectedGroupId == null && memberships.isNotEmpty()) {
                    selectGroup(memberships.first().groupId)
                }
            }
        }
    }

    fun selectGroup(groupId: String) {
        _state.update { it.copy(selectedGroupId = groupId) }
        viewModelScope.launch {
            val userId = supabaseRepo.currentUserId ?: return@launch
            memberContextCacheService.warmUpForUser(userId)
        }
    }

    fun refreshData() {
        viewModelScope.launch {
            val groupId = _state.value.selectedGroupId ?: return@launch
            val userId = supabaseRepo.currentUserId ?: return@launch
            _state.update { it.copy(isRefreshing = true) }
            // In a real scenario, this would trigger a sync in the cache service
            memberContextCacheService.warmUpForUser(userId)
            _state.update { it.copy(isRefreshing = false) }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}
