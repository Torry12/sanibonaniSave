package com.sanibonani.save.features.admin.presentation.state

import com.sanibonani.save.domain.model.*

data class PlatformBackupUiState(
    val backups: List<DatabaseBackup> = emptyList(),
    val isBackingUp: Boolean = false,
    val isFetchingBackups: Boolean = false
)
