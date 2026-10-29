package com.sanibonani.save.features.admin.presentation

import com.sanibonani.save.domain.usecase.treasury.AuditGroupBalanceUseCase
import com.sanibonani.save.domain.usecase.treasury.ConsolidateGroupLedgerUseCase
import com.sanibonani.save.domain.usecase.treasury.GetPlatformLedgerUseCase
import com.sanibonani.save.domain.usecase.treasury.ObserveGroupReconciliationsUseCase
import com.sanibonani.save.features.admin.presentation.state.PlatformLedgerUiState
import com.sanibonani.save.features.common.presentation.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class PlatformLedgerViewModel @Inject constructor(
    private val getPlatformLedger: GetPlatformLedgerUseCase,
    private val observeGroupReconciliations: ObserveGroupReconciliationsUseCase,
    private val auditGroupBalance: AuditGroupBalanceUseCase,
    private val consolidateLedger: ConsolidateGroupLedgerUseCase
) : BaseViewModel<PlatformLedgerUiState>(PlatformLedgerUiState()) {

    val state: StateFlow<PlatformLedgerUiState> = uiState

    fun load() {
        launchJob("load_ledger") {
            getPlatformLedger()
                .onSuccess { ledger -> updateState { it.copy(platformLedger = ledger) } }
                .onFailure { e ->
                    // The ledger on screen stays untouched: only a verified response replaces it.
                    handleError(e, "Could not refresh the platform ledger.")
                }
        }
    }

    fun downloadPlatformStatement() {
        showMessage("Platform statement export is currently in development.")
    }

    fun fetchReconciliations(groupId: String) {
        launchJob("fetch_recon_$groupId") {
            observeGroupReconciliations(groupId).collect { result ->
                result.onSuccess { list ->
                    updateState { it.copy(groupReconciliations = it.groupReconciliations + (groupId to list)) }
                }
            }
        }
    }

    fun reconcileGroup(groupId: String) {
        launchJob("reconcile") {
            updateState { it.copy(isReconciling = true, lastAuditResult = null) }
            try {
                auditGroupBalance(groupId, repair = false).onSuccess { res ->
                    updateState { it.copy(lastAuditResult = res) }
                    showMessage("Audit Complete. Discrepancy: ${res.discrepancy.amount.toPlainString()}")
                    load()
                }.onFailure { handleError(it) }
            } finally {
                // The flag is cleared whatever the call does, so a repair that fails or throws
                // cannot leave the group's button spinning for the rest of the session.
                updateState { it.copy(isReconciling = false) }
            }
        }
    }

    fun repairGroupFinancials(groupId: String) {
        launchJob("repair") {
            updateState { it.copy(isRepairing = true, lastAuditResult = null) }
            try {
                auditGroupBalance(groupId, repair = true).onSuccess { res ->
                    updateState { it.copy(lastAuditResult = res) }
                    showMessage("Repair Complete. New Balance: ${res.currentBalance.amount.toPlainString()}")
                    load()
                }.onFailure { handleError(it) }
            } finally {
                updateState { it.copy(isRepairing = false) }
            }
        }
    }

    fun consolidateGroupLedger(groupId: String) {
        launchJob("consolidate") {
            updateState { it.copy(isConsolidating = true) }
            try {
                consolidateLedger(groupId).onSuccess { load() }.onFailure { handleError(it) }
            } finally {
                updateState { it.copy(isConsolidating = false) }
            }
        }
    }
}
