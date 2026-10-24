package com.sanibonani.save.data.service

import com.sanibonani.save.domain.event.DomainEvent
import com.sanibonani.save.domain.event.PillarRefreshRequestedEvent
import com.sanibonani.save.domain.logging.AppLogger
import com.sanibonani.save.domain.repository.system.SyncRepository
import com.sanibonani.save.domain.service.SyncTriggerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncTriggerServiceImpl @Inject constructor(
    private val syncRepo: SyncRepository
) : SyncTriggerService {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val tag = "SyncTriggerService"

    override fun start() {
        AppLogger.i(tag, "SyncTriggerService started. Listening for refresh events.")
    }

    override suspend fun handle(event: DomainEvent) {
        if (event is PillarRefreshRequestedEvent) {
            AppLogger.i(tag, "Manual refresh requested for pillar: ${event.pillar} (Group: ${event.groupId ?: "GLOBAL"})")
            scope.launch {
                val groupId = event.groupId
                if (groupId != null) {
                    syncRepo.syncRefresh(event.pillar, groupId)
                } else {
                    // Global pillar refresh (not tied to a specific group)
                    // Currently, syncRefresh handles mostly group-based data.
                    // For Global, we might want to sync platform settings or similar.
                    syncRepo.syncAllData()
                }
            }
        }
    }
}
