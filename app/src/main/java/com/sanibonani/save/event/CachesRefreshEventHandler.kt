package com.sanibonani.save.event

import com.sanibonani.save.domain.event.DomainEvent
import com.sanibonani.save.domain.event.EventHandler
import com.sanibonani.save.domain.event.FullSyncCompletedEvent
import com.sanibonani.save.domain.logging.AppLogger
import com.sanibonani.save.service.AdminGroupContextCacheService
import com.sanibonani.save.service.MemberGroupContextCacheService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CachesRefreshEventHandler @Inject constructor(
    private val adminCache: AdminGroupContextCacheService,
    private val memberCache: MemberGroupContextCacheService
) : EventHandler<DomainEvent> {

    override suspend fun handle(event: DomainEvent) {
        if (event is FullSyncCompletedEvent) {
            AppLogger.i("CachesRefreshEventHandler", "🔄 Full sync completed for user ${event.userId}. Refreshing caches...")

            // Re-warm contexts from the now-populated local database
            memberCache.warmUpForUser(event.userId)
            adminCache.warmUpForUser(event.userId)
        }
    }
}
