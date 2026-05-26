package com.sanibonani.save.domain.event

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import com.sanibonani.save.domain.model.LedgerEntry
import com.sanibonani.save.domain.model.Payment

/**
 * Simple event logger/listener for demonstration.
 * In production, replace with a robust event handler/dispatcher.
 */
object EventLogger {
    fun start() {
        DomainEventDispatcher.events.onEach { event ->
            when (event) {
                is LedgerEntryCreatedEvent -> println("[EVENT] LedgerEntryCreated: ${event.entry}")
                is PaymentProcessedEvent -> println("[EVENT] PaymentProcessed: ${event.entry}")
                else -> println("[EVENT] Unknown event: $event")
            }
        }.launchIn(CoroutineScope(Dispatchers.Default))
    }
}

