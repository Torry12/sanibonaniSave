package com.sanibonani.save.features.member.presentation.coordination

import com.sanibonani.save.features.common.presentation.UIEvent
import com.sanibonani.save.features.member.presentation.state.MemberUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow

/**
 * Narrow surface a collaborator needs to act on the member portal.
 *
 * [com.sanibonani.save.features.member.presentation.MemberOverviewViewModel] stays the only
 * owner of [MemberUiState] so Support Mode always renders one consistent snapshot.
 */
interface MemberActionHost {
    val memberState: StateFlow<MemberUiState>

    fun updateMemberState(reducer: (MemberUiState) -> MemberUiState)

    fun launchMemberJob(key: String, block: suspend CoroutineScope.() -> Unit): Job

    fun launchMemberSafeJob(
        key: String,
        errorPrefix: String? = null,
        block: suspend CoroutineScope.() -> Unit
    ): Job

    fun sendUiEvent(event: UIEvent)

    fun reportError(throwable: Throwable, userMessage: String? = null)
}
