package com.sanibonani.save.features.common.presentation

import androidx.lifecycle.ViewModel
import com.sanibonani.save.domain.repository.system.SyncRepository
import com.sanibonani.save.domain.repository.system.SyncStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class GlobalSyncViewModel @Inject constructor(
    private val syncRepository: SyncRepository
) : ViewModel() {
    val syncStatus: StateFlow<SyncStatus> = syncRepository.syncStatus
}
