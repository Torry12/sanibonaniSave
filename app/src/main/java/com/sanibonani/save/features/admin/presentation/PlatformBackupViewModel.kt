package com.sanibonani.save.features.admin.presentation

import com.sanibonani.save.domain.usecase.admin.DeleteCloudBackupUseCase
import com.sanibonani.save.domain.usecase.admin.ExportLocalDatabaseUseCase
import com.sanibonani.save.domain.usecase.admin.GetCloudBackupsUseCase
import com.sanibonani.save.domain.usecase.admin.TriggerCloudBackupUseCase
import com.sanibonani.save.features.admin.presentation.state.PlatformBackupUiState
import com.sanibonani.save.features.common.presentation.BaseViewModel
import com.sanibonani.save.features.common.presentation.UIEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class PlatformBackupViewModel @Inject constructor(
    private val getCloudBackups: GetCloudBackupsUseCase,
    private val triggerCloudBackupUseCase: TriggerCloudBackupUseCase,
    private val deleteCloudBackup: DeleteCloudBackupUseCase,
    private val exportLocalDatabaseUseCase: ExportLocalDatabaseUseCase
) : BaseViewModel<PlatformBackupUiState>(PlatformBackupUiState()) {

    val state: StateFlow<PlatformBackupUiState> = uiState

    fun loadBackups() {
        launchJob("load_backups") {
            updateState { it.copy(isFetchingBackups = true) }
            getCloudBackups().onSuccess { list ->
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
            triggerCloudBackupUseCase().onSuccess {
                loadBackups()
                showMessage("Cloud backup triggered")
            }.onFailure { handleError(it) }
            updateState { it.copy(isBackingUp = false) }
        }
    }

    fun deleteBackup(name: String) {
        launchJob("delete_backup") {
            deleteCloudBackup(name).onSuccess {
                loadBackups()
                showMessage("Backup deleted")
            }.onFailure { handleError(it) }
        }
    }

    fun exportLocalDatabase(useOnline: Boolean) {
        launchJob("export_db") {
            exportLocalDatabaseUseCase(useOnline).onSuccess {
                sendEvent(UIEvent.OpenFile(it, "application/octet-stream", "DB Export"))
            }.onFailure { handleError(it) }
        }
    }
}
