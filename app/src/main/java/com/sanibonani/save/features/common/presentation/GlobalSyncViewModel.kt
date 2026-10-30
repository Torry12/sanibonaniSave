package com.sanibonani.save.features.common.presentation

import androidx.lifecycle.ViewModel
import com.sanibonani.save.domain.model.SyncStatus
import com.sanibonani.save.domain.usecase.system.ObserveSyncStatusUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class GlobalSyncViewModel @Inject constructor(
    observeSyncStatus: ObserveSyncStatusUseCase
) : ViewModel() {
    val syncStatus: StateFlow<SyncStatus> = observeSyncStatus()
}
