package com.sanibonani.save.domain.event

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Simple in-memory event dispatcher for domain events.
 */
object DomainEventDispatcher {
    private val _events = MutableSharedFlow<DomainEvent>(extraBufferCapacity = 64)
    val events = _events.asSharedFlow()

    suspend fun emit(event: DomainEvent) {
        _events.emit(event)
    }
}

