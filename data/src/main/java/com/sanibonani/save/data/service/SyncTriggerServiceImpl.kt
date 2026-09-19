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

    override suspend fun handle(event: DomainEvent) {
        if (event is PillarRefreshRequestedEvent) {
            AppLogger.i(tag, "Manual refresh requested for pillar: ${event.pillar} (Group: ${event.groupId})")
            scope.launch {
                syncRepo.syncRefresh(event.pillar, event.groupId)
            }
        }
    }
}
