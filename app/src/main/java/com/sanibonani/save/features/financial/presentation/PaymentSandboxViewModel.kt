package com.sanibonani.save.features.financial.presentation

import com.sanibonani.save.domain.utils.safeRunCatching
import com.sanibonani.save.data.remote.gateway.SimulationGateway
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.group.GroupRepository
import com.sanibonani.save.domain.repository.member.MemberRepository
import com.sanibonani.save.features.common.presentation.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
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
        launchJob("load_groups") {
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
        launchJob("select_group") {
            memberRepo.getGroupMembers(groupId).first().onSuccess { members ->
                updateState { it.copy(selectedGroupMembers = members) }
            }
        }
    }

    fun simulateBankTransfer(amount: String, reference: String) {
        val gid = uiState.value.selectedGroupId ?: return
        val cleanAmount = amount.trim().replace(Regex("[R\\s,]"), "")
        if (cleanAmount.isBlank()) {
            updateState { it.copy(error = "Please enter a valid amount") }
            return
        }
        val money = safeRunCatching { Money(cleanAmount) }.getOrNull()
        if (money == null || !money.isPositive) {
            updateState { it.copy(error = "Amount must be a positive number") }
            return
        }

        launchJob("simulate_bank_transfer") {
            updateState { it.copy(isSimulating = true, error = null) }
            simulationGateway.bank.simulateSinglePayment(gid, money, reference).onSuccess {
                updateState { it.copy(isSimulating = false, successMessage = "Bank transfer simulated") }
            }.onFailure { e ->
                updateState { it.copy(isSimulating = false, error = e.message ?: "Simulation failed") }
            }
        }
    }

    fun simulateMonthlyCollections() {
        val st = uiState.value
        val gid = st.selectedGroupId ?: return
        val group = st.groups.find { it.id == gid } ?: return
        val memberNames = st.selectedGroupMembers.map { it.fullName }
        if (memberNames.isEmpty()) {
            updateState { it.copy(error = "Selected group has no members to simulate") }
            return
        }

        launchJob("simulate_monthly_collections") {
            updateState { it.copy(isSimulating = true, error = null) }
            simulationGateway.bank.simulateMonthOfActivity(gid, memberNames, group.monthlyContribution)
                .onSuccess { txIds ->
                    updateState { it.copy(isSimulating = false, successMessage = "Successfully simulated monthly collections (${txIds.size} transactions)") }
                }
                .onFailure { e ->
                    updateState { it.copy(isSimulating = false, error = e.message ?: "Monthly collection simulation failed") }
                }
        }
    }

    fun simulateDeepHistoricalRun(months: Int) {
        val st = uiState.value
        val gid = st.selectedGroupId ?: return
        val group = st.groups.find { it.id == gid } ?: return
        val memberNames = st.selectedGroupMembers.map { it.fullName }
        if (memberNames.isEmpty()) {
            updateState { it.copy(error = "Selected group has no members to simulate") }
            return
        }

        launchJob("simulate_deep_historical") {
            updateState { it.copy(isSimulating = true, error = null) }
            safeRunCatching {
                var totalTx = 0
                repeat(months) {
                    val txIds = simulationGateway.bank.simulateMonthOfActivity(gid, memberNames, group.monthlyContribution).getOrThrow()
                    totalTx += txIds.size
                }
                totalTx
            }.onSuccess { total ->
                updateState { it.copy(isSimulating = false, successMessage = "Deep history simulated for $months months ($total total transactions)") }
            }.onFailure { e ->
                updateState { it.copy(isSimulating = false, error = e.message ?: "Deep historical simulation failed") }
            }
        }
    }

    fun clearMessages() {
        updateState { it.copy(successMessage = null, error = null) }
    }

}
