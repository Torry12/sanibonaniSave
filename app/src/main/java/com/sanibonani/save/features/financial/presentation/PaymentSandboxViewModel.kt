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
            getPublicGroups().first().onSuccess { groups ->
                updateState { it.copy(groups = groups) }
                if (groups.isNotEmpty() && (uiState.value.selectedGroupId == null)) {
                    selectGroup(groups.first().id ?: "")
                }
            }.onFailure { e ->
                // The screen has no spinner for this read, so a refusal that set nothing at all left
                // the operator staring at an empty dropdown with no way to tell it was not empty.
                updateState { it.copy(error = e.toUserMessage()) }
            }
        }
    }

    fun selectGroup(groupId: String) {
        // Every simulate method books activity against this roster, so switching groups has to drop
        // the previous one rather than let a failed read bill the new group for the old members.
        updateState { it.copy(selectedGroupId = groupId, selectedGroupMembers = emptyList()) }
        launchJob("select_group") {
            observeGroupMembers(groupId).first().onSuccess { members ->
                updateState { it.copy(selectedGroupMembers = members) }
            }.onFailure { e ->
                updateState { it.copy(error = e.toUserMessage()) }
            }
        }
    }

    fun simulateBankTransfer(amount: String, reference: String) {
        val gid = uiState.value.selectedGroupId ?: run {
            updateState { it.copy(error = MSG_NO_GROUP) }
            return
        }
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
        val gid = st.selectedGroupId ?: run {
            updateState { it.copy(error = MSG_NO_GROUP) }
            return
        }
        val group = st.groups.find { it.id == gid } ?: run {
            updateState { it.copy(error = MSG_STALE_GROUP) }
            return
        }
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
        val gid = st.selectedGroupId ?: run {
            updateState { it.copy(error = MSG_NO_GROUP) }
            return
        }
        val group = st.groups.find { it.id == gid } ?: run {
            updateState { it.copy(error = MSG_STALE_GROUP) }
            return
        }
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

    private companion object {
        // Every simulate path used to bail out of these two cases without touching the state, so the
        // button looked pressed and nothing answered. The screen renders `state.error` as a modal.
        const val MSG_NO_GROUP = "Select a group to simulate before running this."
        const val MSG_STALE_GROUP = "That group is no longer in the list. Select a group again."
    }
}
