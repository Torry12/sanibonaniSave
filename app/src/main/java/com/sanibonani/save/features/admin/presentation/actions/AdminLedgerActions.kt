package com.sanibonani.save.features.admin.presentation.actions

import com.sanibonani.save.domain.usecase.payment.ReconcileBankTransactionUseCase
import com.sanibonani.save.domain.usecase.system.BuildGroupLedgerStatementUseCase
import com.sanibonani.save.domain.usecase.treasury.ConsolidateGroupLedgerUseCase
import com.sanibonani.save.domain.usecase.treasury.PerformGroupReconciliationUseCase
import com.sanibonani.save.domain.usecase.treasury.ResolveLedgerDiscrepancyUseCase
import com.sanibonani.save.features.common.presentation.UIEvent
import com.sanibonani.save.service.AdminGroupContextCacheService
import javax.inject.Inject

/**
 * Ledger integrity actions: reconciliation, discrepancy resolution,
 * deep consolidation and statement export.
 */
class AdminLedgerActions @Inject constructor(
    private val performReconciliationUseCase: PerformGroupReconciliationUseCase,
    private val resolveDiscrepancyUseCase: ResolveLedgerDiscrepancyUseCase,
    private val reconcileManualUseCase: ReconcileBankTransactionUseCase,
    private val consolidateGroupLedger: ConsolidateGroupLedgerUseCase,
    private val buildGroupLedgerStatement: BuildGroupLedgerStatementUseCase,
    private val adminContextCacheService: AdminGroupContextCacheService,
) {

    fun performReconciliation(host: AdminFinancialActionHost) {
        val groupId = adminContextCacheService.selectedGroupId.value ?: return
        host.launchAction("perform_reconciliation") {
            host.updateActionState { it.copy(isReconciling = true) }
            performReconciliationUseCase(groupId)
                .onSuccess { record ->
                    host.updateActionState { it.copy(isReconciling = false, successMessage = "Reconciliation complete: ${record.status.name}") }
                }
                .onFailure { e ->
                    host.updateActionState { it.copy(isReconciling = false) }
                    host.reportError(e)
                }
        }
    }

    fun resolveDiscrepancy(host: AdminFinancialActionHost, reconciliationId: String, notes: String) {
        host.launchAction("resolve_discrepancy") {
            host.updateActionState { it.copy(isReconciling = true) }
            resolveDiscrepancyUseCase(reconciliationId, notes)
                .onSuccess {
                    host.updateActionState { it.copy(isReconciling = false, successMessage = "Discrepancy resolved and documented.") }
                }
                .onFailure { e ->
                    host.updateActionState { it.copy(isReconciling = false) }
                    host.reportError(e)
                }
        }
    }

    fun consolidateLedger(host: AdminFinancialActionHost) {
        val groupId = adminContextCacheService.selectedGroupId.value ?: return
        host.launchAction("consolidate_ledger") {
            host.updateActionState { it.copy(isConsolidating = true, error = null) }
            consolidateGroupLedger(groupId)
                .onSuccess { report ->
                    host.updateActionState {
                        it.copy(
                            isConsolidating = false,
                            successMessage = "Deep consolidation complete. ${report.entriesCreated} entries reconstructed."
                        )
                    }
                    adminContextCacheService.warmUpGroup(groupId)
                    host.reloadFinancialData(groupId)
                }
                .onFailure { e ->
                    host.updateActionState { it.copy(isConsolidating = false) }
                    host.reportError(e)
                }
        }
    }

    fun reconcileManual(host: AdminFinancialActionHost, bankTxId: String, ledgerId: String) {
        host.launchAction("reconcile_manual") {
            host.updateActionState { it.copy(isLoading = true, error = null) }
            reconcileManualUseCase(bankTxId, ledgerId)
                .onSuccess {
                    host.updateActionState { it.copy(isLoading = false, successMessage = "Transaction reconciled successfully") }
                }
                .onFailure { e ->
                    host.updateActionState { it.copy(isLoading = false) }
                    host.reportError(e)
                }
        }
    }

    fun downloadGroupStatement(host: AdminFinancialActionHost) {
        val state = host.actionState
        val group = state.group ?: return
        if (state.ledger.isEmpty()) {
            host.updateActionState { it.copy(error = "No group transactions found to export.") }
            return
        }

        host.launchAction("download_statement") {
            host.updateActionState { it.copy(isExporting = true, error = null) }
            buildGroupLedgerStatement(group).onSuccess { file ->
                host.updateActionState { it.copy(isExporting = false, exportFile = file) }
                host.publishEvent(UIEvent.OpenFile(file, "application/pdf", "Group Statement"))
            }.onFailure { e ->
                host.updateActionState { it.copy(isExporting = false) }
                host.reportError(e)
            }
        }
    }
}
