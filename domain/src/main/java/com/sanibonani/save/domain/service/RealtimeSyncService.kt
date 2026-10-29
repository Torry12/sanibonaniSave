package com.sanibonani.save.domain.service

import com.sanibonani.save.domain.model.RealtimeSyncStatus
import kotlinx.coroutines.flow.StateFlow

/**
 * The Supabase realtime subscription the app runs for the signed-in account.
 */
interface RealtimeSyncService {
    val syncMetrics: StateFlow<Map<String, RealtimeSyncStatus>>
    fun start()
    fun stop()
}
