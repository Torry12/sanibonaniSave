package com.sanibonani.save.domain.event

import com.sanibonani.save.data.logging.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

interface EventHandler<in T : DomainEvent> {
    suspend fun handle(event: T)
}

object EventBus {
    private const val TAG = "EventBus"
    private val _events = MutableSharedFlow<DomainEvent>(extraBufferCapacity = 64)
    val events = _events.asSharedFlow()
    private val handlers = mutableListOf<EventHandler<DomainEvent>>()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    fun register(handler: EventHandler<DomainEvent>) {
        handlers.add(handler)
        AppLogger.d(TAG, "Registered handler: ${handler::class.simpleName}")
    }

    fun start() {
        AppLogger.i(TAG, "Starting EventBus with ${handlers.size} handlers")
        events.onEach { event ->
            handlers.forEach { handler ->
                scope.launch {
                    try {
                        handler.handle(event)
                    } catch (e: Exception) {
                        AppLogger.e(TAG, "Error in handler ${handler::class.simpleName}", e)
                    }
                }
            }
        }.launchIn(scope)
    }

    suspend fun emit(event: DomainEvent) {
        AppLogger.d(TAG, "Emitting event: ${event::class.simpleName}")
        _events.emit(event)
    }

    fun tryEmit(event: DomainEvent): Boolean {
        val success = _events.tryEmit(event)
        if (success) {
            AppLogger.d(TAG, "Successfully try-emitted: ${event::class.simpleName}")
        } else {
            AppLogger.w(TAG, "Failed to try-emit: ${event::class.simpleName} (Buffer full)")
        }
        return success
    }
}
