package com.sanibonani.save.features.admin.presentation.state

import com.sanibonani.save.domain.model.*

data class PlatformLedgerUiState(
    val platformLedger: List<LedgerEntry> = emptyList(),
    val groupReconciliations: Map<String, List<ReconciliationRecord>> = emptyMap(),
    val lastAuditResult: GroupAuditResult? = null,
    val isReconciling: Boolean = false,
    val isRepairing: Boolean = false,
    val isConsolidating: Boolean = false,
    val isExporting: Boolean = false
)
