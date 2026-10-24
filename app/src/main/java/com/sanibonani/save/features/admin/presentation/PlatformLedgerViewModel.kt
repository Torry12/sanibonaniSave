package com.sanibonani.save.features.admin.presentation

import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.admin.PlatformRepository
import com.sanibonani.save.domain.repository.treasury.LedgerRepository
import com.sanibonani.save.domain.repository.treasury.ReconciliationRepository
import com.sanibonani.save.features.admin.presentation.state.PlatformLedgerUiState
import com.sanibonani.save.features.common.presentation.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class PlatformLedgerViewModel @Inject constructor(
    private val platformRepo: PlatformRepository,
    private val ledgerRepo: LedgerRepository,
    private val reconRepo: ReconciliationRepository
) : BaseViewModel<PlatformLedgerUiState>(PlatformLedgerUiState()) {

    val state: StateFlow<PlatformLedgerUiState> = uiState

    fun load() {
        launchJob("load_ledger") {
            ledgerRepo.getPlatformLedger().onSuccess { ledger ->
                updateState { it.copy(platformLedger = ledger) }
            }
        }
    }

    fun downloadPlatformStatement() {
        showMessage("Platform statement export is currently in development.")
    }

    fun fetchReconciliations(groupId: String) {
        launchJob("fetch_recon_$groupId") {
            reconRepo.observeReconciliations(groupId).collect { result ->
                result.onSuccess { list ->
                    updateState { it.copy(groupReconciliations = it.groupReconciliations + (groupId to list)) }
                }
            }
        }
    }

    fun reconcileGroup(groupId: String) {
        launchJob("reconcile") {
            updateState { it.copy(isReconciling = true, lastAuditResult = null) }
            platformRepo.auditGroup(groupId).onSuccess { res ->
                updateState { it.copy(lastAuditResult = res) }
                showMessage("Audit Complete. Discrepancy: ${res.discrepancy.amount.toPlainString()}")
                load()
            }.onFailure { handleError(it) }
            updateState { it.copy(isReconciling = false) }
        }
    }

    fun repairGroupFinancials(groupId: String) {
        launchJob("repair") {
            updateState { it.copy(isRepairing = true, lastAuditResult = null) }
            platformRepo.repairGroup(groupId).onSuccess { res ->
                updateState { it.copy(lastAuditResult = res) }
                showMessage("Repair Complete. New Balance: ${res.currentBalance.amount.toPlainString()}")
                load()
            }.onFailure { handleError(it) }
            updateState { it.copy(isRepairing = false) }
        }
    }

    fun consolidateGroupLedger(groupId: String) {
        launchJob("consolidate") {
            updateState { it.copy(isConsolidating = true) }
            ledgerRepo.consolidateGroupLedger(groupId).onSuccess { load() }.onFailure { handleError(it) }
            updateState { it.copy(isConsolidating = false) }
        }
    }
}
