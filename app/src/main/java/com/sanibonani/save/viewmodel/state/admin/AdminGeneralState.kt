package com.sanibonani.save.viewmodel.state.admin

import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.LedgerEntry
import java.io.File

data class AdminGeneralState(
    val managedGroups: List<Group> = emptyList(),
    val currentGroupId: String? = null,
    val selectedTab: Int = 0,
    val isLoading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
    val loadingMessage: String? = null,
    val daysOverdue: Int = 0,
    val restoreRequested: Boolean = false,
    val isExporting: Boolean = false,
    val exportFile: File? = null,
    val isUploading: Boolean = false,
    val uploadProgress: Double? = null,
    val ledger: List<LedgerEntry> = emptyList(),
    val selectedLedgerEntry: LedgerEntry? = null
)
