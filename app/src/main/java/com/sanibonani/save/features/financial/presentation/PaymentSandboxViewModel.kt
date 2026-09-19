package com.sanibonani.save.features.financial.presentation

import androidx.lifecycle.viewModelScope
import com.sanibonani.save.data.remote.gateway.SimulationGateway
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.equity.GroupRepository
import com.sanibonani.save.domain.repository.equity.MemberRepository
import com.sanibonani.save.viewmodel.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PaymentSandboxUiState(
    val groups: List<Group> = emptyList(),
    val selectedGroupId: String? = null,
    val selectedGroupMembers: List<Member> = emptyList(),
    val isLoading: Boolean = false,
    val isSimulating: Boolean = false,
    val successMessage: String? = null,
    val error: String? = null,
)

@HiltViewModel
class PaymentSandboxViewModel @Inject constructor(
    private val groupRepo: GroupRepository,
    private val memberRepo: MemberRepository,
    private val simulationGateway: SimulationGateway
) : BaseViewModel<PaymentSandboxUiState>(PaymentSandboxUiState()) {

    val state: StateFlow<PaymentSandboxUiState> = uiState

    init { loadGroups() }

    private fun loadGroups() {
        viewModelScope.launch {
            updateState { it.copy(isLoading = true) }
            groupRepo.getPublicGroups().first().onSuccess { groups ->
                updateState { it.copy(groups = groups, isLoading = false) }
                if (groups.isNotEmpty() && (uiState.value.selectedGroupId == null)) {
                    selectGroup(groups.first().id ?: "")
                }
            }
        }
    }

    fun selectGroup(groupId: String) {
        updateState { it.copy(selectedGroupId = groupId) }
        viewModelScope.launch {
            memberRepo.getGroupMembers(groupId).first().onSuccess { members ->
                updateState { it.copy(selectedGroupMembers = members) }
            }
        }
    }

    fun simulateBankTransfer(amount: Double, reference: String) {
        val gid = uiState.value.selectedGroupId ?: return
        viewModelScope.launch {
            updateState { it.copy(isSimulating = true) }
            simulationGateway.bank.simulateSinglePayment(gid, Money(amount), reference).onSuccess {
                updateState { it.copy(isSimulating = false, successMessage = "Bank transfer simulated") }
            }
        }
    }

    fun simulateMonthlyCollections() {
        val gid = uiState.value.selectedGroupId ?: return
        viewModelScope.launch {
            updateState { it.copy(isSimulating = true) }
            // Placeholder
            updateState { it.copy(isSimulating = false, successMessage = "Monthly collections simulated") }
        }
    }

    fun simulateDeepHistoricalRun(months: Int) {
        val gid = uiState.value.selectedGroupId ?: return
        viewModelScope.launch {
            updateState { it.copy(isSimulating = true) }
            // Placeholder
            updateState { it.copy(isSimulating = false, successMessage = "Deep history simulated for $months months") }
        }
    }

    fun clearMessages() {
        updateState { it.copy(successMessage = null, error = null) }
    }

}
