package com.sanibonani.save.features.admin.presentation.actions

import com.sanibonani.save.features.admin.presentation.state.AdminFinancialUiState
import com.sanibonani.save.features.common.presentation.UIEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job

/**
 * Narrow contract the admin financial action delegates use to drive the ViewModel.
 *
 * AdminFinancialViewModel implements this so that cohesive action groups
 * (loans, claims, payouts, banking, ledger) can live in their own classes
 * while the ViewModel keeps ownership of the single UiState container.
 */
interface AdminFinancialActionHost {
    val actionState: AdminFinancialUiState

    fun updateActionState(reducer: (AdminFinancialUiState) -> AdminFinancialUiState)

    fun launchAction(key: String, block: suspend CoroutineScope.() -> Unit): Job

    fun reportError(throwable: Throwable)

    /** Error path that opens the MFA challenge when the backend demands it. */
    fun reportActionError(throwable: Throwable)

    fun publishEvent(event: UIEvent)

    fun notifyUser(message: String)

    fun reloadFinancialData(groupId: String)
}
