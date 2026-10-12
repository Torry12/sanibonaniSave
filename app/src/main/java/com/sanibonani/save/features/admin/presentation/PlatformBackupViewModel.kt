package com.sanibonani.save.features.admin.presentation

import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.admin.PlatformRepository
import com.sanibonani.save.features.admin.presentation.state.PlatformBackupUiState
import com.sanibonani.save.features.common.presentation.BaseViewModel
import com.sanibonani.save.features.common.presentation.UIEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class PlatformBackupViewModel @Inject constructor(
    private val platformRepo: PlatformRepository
) : BaseViewModel<PlatformBackupUiState>(PlatformBackupUiState()) {

    val state: StateFlow<PlatformBackupUiState> = uiState

    fun loadBackups() {
        launchJob("load_backups") {
            updateState { it.copy(isFetchingBackups = true) }
            platformRepo.getCloudBackups().onSuccess { list ->
                updateState { it.copy(backups = list, isFetchingBackups = false) }
            }.onFailure {
                updateState { it.copy(isFetchingBackups = false) }
                handleError(it)
            }
        }
    }

    fun triggerCloudBackup() {
        launchJob("backup") {
            updateState { it.copy(isBackingUp = true) }
            platformRepo.triggerCloudBackup().onSuccess {
                loadBackups()
                showMessage("Cloud backup triggered")
            }.onFailure { handleError(it) }
            updateState { it.copy(isBackingUp = false) }
        }
    }

    fun deleteBackup(name: String) {
        launchJob("delete_backup") {
            platformRepo.deleteCloudBackup(name).onSuccess {
                loadBackups()
                showMessage("Backup deleted")
            }.onFailure { handleError(it) }
        }
    }

    fun exportLocalDatabase(useOnline: Boolean) {
        launchJob("export_db") {
            platformRepo.exportLocalDatabase(useOnline).onSuccess {
                sendEvent(UIEvent.OpenFile(it, "application/octet-stream", "DB Export"))
            }.onFailure { handleError(it) }
        }
    }
}
