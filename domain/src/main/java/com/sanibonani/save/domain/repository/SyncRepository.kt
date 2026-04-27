package com.sanibonani.save.domain.repository

import kotlinx.coroutines.flow.StateFlow

sealed class SyncStatus {
    object Idle : SyncStatus()
    data class Progress(val message: String, val progress: Float) : SyncStatus()
    object Completed : SyncStatus()
    data class Error(val message: String) : SyncStatus()
}

interface SyncRepository {
    val syncStatus: StateFlow<SyncStatus>
    suspend fun syncAllData(): Result<Unit>
}
