package com.sanibonani.save.domain.event

/**
 * Interface for handling domain events.
 */
interface EventHandler<in T : DomainEvent> {
    suspend fun handle(event: T)
}

