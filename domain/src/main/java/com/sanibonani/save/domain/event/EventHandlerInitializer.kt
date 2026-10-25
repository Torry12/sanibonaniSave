package com.sanibonani.save.domain.event

import com.sanibonani.save.domain.logging.AppLogger
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.jvm.JvmSuppressWildcards

@Singleton
class EventHandlerInitializer @Inject constructor(
    private val handlers: @JvmSuppressWildcards Set<EventHandler<DomainEvent>>,
) {
    private var initialized = false

    fun initialize() {
        if (initialized) return
        initialized = true

        AppLogger.i("EventHandlerInitializer", "Initializing ${handlers.size} event handlers")

        handlers.forEach { handler ->
            EventBus.register(handler)
        }

        EventBus.start()
    }
}
