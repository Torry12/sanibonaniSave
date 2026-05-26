package com.sanibonani.save.domain.event

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Registry for event handlers. Call start() to begin listening.
 */
object EventHandlerRegistry {
    private val handlers = mutableListOf<EventHandler<DomainEvent>>()

    fun register(handler: EventHandler<DomainEvent>) {
        handlers.add(handler)
    }

    fun start() {
        DomainEventDispatcher.events.onEach { event ->
            handlers.forEach { handler ->
                CoroutineScope(Dispatchers.Default).launch {
                    handler.handle(event)
                }
            }
        }.launchIn(CoroutineScope(Dispatchers.Default))
    }
}

