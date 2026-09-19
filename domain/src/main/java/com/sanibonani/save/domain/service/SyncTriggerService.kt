package com.sanibonani.save.domain.service

import com.sanibonani.save.domain.event.DomainEventListener

/**
 * Service that listens for manual refresh requests and coordinates with the SyncRepository.
 */
interface SyncTriggerService : DomainEventListener {
    fun start()
}
