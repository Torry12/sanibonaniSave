package com.sanibonani.save.features.member.presentation.coordination

import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.usecase.credit.ObserveMemberLoansUseCase
import com.sanibonani.save.domain.usecase.equity.ObserveMemberContributionsUseCase
import com.sanibonani.save.domain.usecase.group.GetGroupBusinessInsightsUseCase
import com.sanibonani.save.domain.usecase.group.ObserveGroupDetailUseCase
import com.sanibonani.save.domain.usecase.member.ObserveGroupMembersUseCase
import com.sanibonani.save.domain.usecase.member.ObserveMemberByIdUseCase
import com.sanibonani.save.domain.usecase.treasury.ObserveMemberLedgerUseCase
import com.sanibonani.save.features.member.presentation.state.MemberUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Streams the live, server-side truth for one member/group pair into the portal state.
 *
 * Every emission is guarded by [updateSelectedGroup] so a late response from a group the user
 * has already switched away from can never overwrite the visible snapshot.
 */
class MemberLiveObservationCoordinator @Inject constructor(
    private val observeGroupMembers: ObserveGroupMembersUseCase,
    private val observeMemberContributions: ObserveMemberContributionsUseCase,
    private val observeGroupDetail: ObserveGroupDetailUseCase,
    private val observeMemberLedger: ObserveMemberLedgerUseCase,
    private val observeMemberLoans: ObserveMemberLoansUseCase,
    private val observeMemberById: ObserveMemberByIdUseCase,
    private val getGroupBusinessInsights: GetGroupBusinessInsightsUseCase,
) {

    fun start(host: MemberActionHost, groupId: String, memberId: String, group: Group) {
        host.launchMemberJob("live_observation") {
            coroutineScope {
                launch {
                    val members = observeGroupMembers(groupId).firstOrNull()?.getOrNull()
                        ?: return@launch
                    val insights = withContext(Dispatchers.Default) {
                        getGroupBusinessInsights(group, members)
                    }
                    updateSelectedGroup(host, groupId) { it.copy(businessInsight = insights) }
                }
                launch {
                    observeGroupDetail(groupId).collect { res ->
                        res.onSuccess { g ->
                            if (g != null) updateSelectedGroup(host, groupId) { it.copy(group = g) }
                        }
                    }
                }
                launch {
                    observeMemberById(memberId).collect { res ->
                        res.onSuccess { m ->
                            if (m != null) updateSelectedGroup(host, groupId) { it.copy(member = m) }
                        }
                    }
                }
                launch {
                    observeMemberContributions(memberId, groupId).collect { res ->
                        res.onSuccess { list ->
                            updateSelectedGroup(host, groupId) { it.copy(contributions = list) }
                        }
                    }
                }
                launch {
                    observeMemberLoans(memberId).collect { res ->
                        res.onSuccess { list -> updateSelectedGroup(host, groupId) { it.copy(loans = list) } }
                    }
                }
                launch {
                    observeMemberLedger(memberId).collect { res ->
                        res.onSuccess { list ->
                            updateSelectedGroup(host, groupId) { it.copy(memberLedger = list) }
                        }
                    }
                }
                launch {
                    observeGroupMembers(groupId).collectLatest { res ->
                        res.onSuccess { members ->
                            val insights = withContext(Dispatchers.Default) {
                                getGroupBusinessInsights(host.memberState.value.group ?: group, members)
                            }
                            updateSelectedGroup(host, groupId) { it.copy(businessInsight = insights) }
                        }
                    }
                }
            }
        }
    }

    private fun updateSelectedGroup(
        host: MemberActionHost,
        groupId: String,
        reducer: (MemberUiState) -> MemberUiState
    ) {
        host.updateMemberState { state ->
            if (state.selectedGroupId == groupId) reducer(state) else state
        }
    }
}
