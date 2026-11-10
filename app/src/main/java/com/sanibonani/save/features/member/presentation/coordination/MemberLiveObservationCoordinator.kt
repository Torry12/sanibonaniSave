package com.sanibonani.save.features.member.presentation.coordination

import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.logging.AppLogger
import com.sanibonani.save.domain.usecase.credit.ObserveMemberLoansUseCase
import com.sanibonani.save.domain.usecase.disbursement.ObserveMemberPayoutsUseCase
import com.sanibonani.save.domain.usecase.equity.ObserveMemberContributionsUseCase
import com.sanibonani.save.domain.usecase.group.GetGroupBusinessInsightsUseCase
import com.sanibonani.save.domain.usecase.group.ObserveGroupDetailUseCase
import com.sanibonani.save.domain.usecase.member.ObserveGroupMembersUseCase
import com.sanibonani.save.domain.usecase.member.ObserveMemberByIdUseCase
import com.sanibonani.save.domain.usecase.member.ObserveMemberClaimsUseCase
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
    private val observeMemberClaims: ObserveMemberClaimsUseCase,
    private val observeMemberPayouts: ObserveMemberPayoutsUseCase,
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
                    AppLogger.i(LEDGER_LOG_TAG, "member ledger read attempted for $memberId")
                    observeMemberLedger(memberId).collect { res ->
                        res.onSuccess { list ->
                            // The count is the only thing that separates "this member has no group
                            // entries" from "the read never ran" on a device whose firmware drops
                            // app-authored logcat lines; files/diagnostics.log carries both words.
                            AppLogger.i(
                                LEDGER_LOG_TAG,
                                "member ledger read answered with ${ledgerCountWords(list.size)} for $memberId"
                            )
                            updateSelectedGroup(host, groupId) {
                                it.copy(memberLedger = list, memberLedgerAnswered = true)
                            }
                        }.onFailure { e ->
                            AppLogger.w(
                                LEDGER_LOG_TAG,
                                "member ledger read refused for $memberId: ${e.message.orEmpty()}"
                            )
                        }
                    }
                }
                // The claims tab, the burial-society stat card and the "Financial Snapshot" row all
                // read these two lists, so a member's own claim has to reach them from the book it
                // was filed in - not from a field nothing ever wrote.
                launch {
                    observeMemberClaims(memberId, groupId).collect { res ->
                        res.onSuccess { list ->
                            updateSelectedGroup(host, groupId) { it.copy(burialClaims = list) }
                        }
                    }
                }
                launch {
                    observeMemberPayouts(memberId).collect { res ->
                        res.onSuccess { list ->
                            updateSelectedGroup(host, groupId) { it.copy(payouts = list) }
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

    private fun ledgerCountWords(size: Int): String = "$size ${if (size == 1) "entry" else "entries"}"

    companion object {
        /**
         * Its own tag so one grep of the diagnostics file answers the one question the portal cannot:
         * whether this member's ledger was read, and what it came back with.
         */
        const val LEDGER_LOG_TAG = "MemberLedgerRead"
    }
}
