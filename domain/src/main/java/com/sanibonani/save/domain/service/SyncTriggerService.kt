package com.sanibonani.save.domain.service

import com.sanibonani.save.domain.event.EventHandler
import com.sanibonani.save.domain.event.DomainEvent

/**
 * Service that listens for manual refresh requests and coordinates with the SyncRepository.
 */
interface SyncTriggerService : EventHandler<DomainEvent> {
    fun start()
}
