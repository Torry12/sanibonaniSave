package com.sanibonani.save.features.financial.presentation

import com.sanibonani.save.domain.utils.safeRunCatching
import com.sanibonani.save.domain.utils.toUserMessage
import com.sanibonani.save.domain.gateway.BankBackendSimulationModule
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.usecase.group.GetPublicGroupsUseCase
import com.sanibonani.save.domain.usecase.member.ObserveGroupMembersUseCase
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
    private val getPublicGroups: GetPublicGroupsUseCase,
    private val observeGroupMembers: ObserveGroupMembersUseCase,
    private val bankSimulation: BankBackendSimulationModule
) : BaseViewModel<PaymentSandboxUiState>(PaymentSandboxUiState()) {

    val state: StateFlow<PaymentSandboxUiState> = uiState

    init { loadGroups() }

    private fun loadGroups() {
        launchJob("load_groups") {
            updateState { it.copy(isLoading = true) }
            getPublicGroups().first().onSuccess { groups ->
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
            observeGroupMembers(groupId).first().onSuccess { members ->
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
            bankSimulation.simulateSinglePayment(gid, money, reference).onSuccess {
                updateState { it.copy(isSimulating = false, successMessage = "Bank transfer simulated") }
            }.onFailure { e ->
                updateState { it.copy(isSimulating = false, error = e.toUserMessage()) }
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
            bankSimulation.simulateMonthOfActivity(gid, memberNames, group.monthlyContribution)
                .onSuccess { txIds ->
                    updateState { it.copy(isSimulating = false, successMessage = "Successfully simulated monthly collections (${txIds.size} transactions)") }
                }
                .onFailure { e ->
                    updateState { it.copy(isSimulating = false, error = e.toUserMessage()) }
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
                    val txIds = bankSimulation.simulateMonthOfActivity(gid, memberNames, group.monthlyContribution).getOrThrow()
                    totalTx += txIds.size
                }
                totalTx
            }.onSuccess { total ->
                updateState { it.copy(isSimulating = false, successMessage = "Deep history simulated for $months months ($total total transactions)") }
            }.onFailure { e ->
                updateState { it.copy(isSimulating = false, error = e.toUserMessage()) }
            }
        }
    }

    fun clearMessages() {
        updateState { it.copy(successMessage = null, error = null) }
    }

}
