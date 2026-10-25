package com.sanibonani.save.event

import com.sanibonani.save.domain.event.DomainEvent
import com.sanibonani.save.domain.event.EventHandler
import com.sanibonani.save.domain.event.PillarRefreshRequestedEvent
import com.sanibonani.save.domain.logging.AppLogger
import com.sanibonani.save.domain.repository.system.SyncRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PillarRefreshEventHandler @Inject constructor(
    private val syncRepo: SyncRepository
) : EventHandler<DomainEvent> {

    override suspend fun handle(event: DomainEvent) {
        if (event is PillarRefreshRequestedEvent) {
            AppLogger.i("PillarRefreshEventHandler", "🔄 Refresh requested for pillar ${event.pillar.name} in group ${event.groupId}")
            event.groupId?.let { gid ->
                syncRepo.syncRefresh(event.pillar, gid)
            }
        }
    }
}
