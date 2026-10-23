package com.sanibonani.save.features.admin.presentation.coordination

import com.sanibonani.save.features.admin.presentation.state.AdminOverviewUiState
import com.sanibonani.save.features.common.presentation.UIEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow

/**
 * Narrow surface a collaborator needs to act on the admin dashboard.
 *
 * Collaborators never own state: [AdminOverviewViewModel] remains the single owner of
 * [AdminOverviewUiState] so Support Mode never sees two competing snapshots.
 */
interface AdminActionHost {
    val adminState: StateFlow<AdminOverviewUiState>

    fun updateAdminState(reducer: (AdminOverviewUiState) -> AdminOverviewUiState)

    fun launchAdminJob(key: String, block: suspend CoroutineScope.() -> Unit): Job

    fun launchAdminSafeJob(
        key: String,
        errorPrefix: String? = null,
        block: suspend CoroutineScope.() -> Unit
    ): Job

    fun cancelAdminJob(key: String)

    suspend fun <T> runWithLoading(block: suspend () -> T): T

    fun sendUiEvent(event: UIEvent)

    fun reportError(throwable: Throwable)
}
