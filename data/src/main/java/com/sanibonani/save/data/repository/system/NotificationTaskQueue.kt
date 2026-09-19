package com.sanibonani.save.data.repository.system

import com.sanibonani.save.domain.logging.AppLogger
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A Producer-Consumer queue for managing outgoing notification tasks.
 * Prevents network congestion by processing deliveries at a controlled rate.
 */
@Singleton
class NotificationTaskQueue @Inject constructor(
    private val whatsAppChannel: WhatsAppChannel
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val taskChannel = Channel<NotificationTask>(Channel.BUFFERED)
    private val tag = "NotificationTaskQueue"

    init {
        startConsumer()
    }

    /**
     * Produces a new notification task into the queue.
     */
    fun enqueue(phone: String, message: String) {
        scope.launch {
            if (phone.isNotBlank()) {
                taskChannel.send(NotificationTask(phone, message))
            }
        }
    }

    private fun startConsumer() {
        scope.launch {
            for (task in taskChannel) {
                try {
                    whatsAppChannel.deliver(task.recipientPhone, task.message)
                    // Throttle to avoid rate limiting or stack congestion
                    delay(100)
                } catch (e: Exception) {
                    AppLogger.e(tag, "Failed to deliver queued notification to ${task.recipientPhone}", e)
                }
            }
        }
    }

    fun shutdown() {
        taskChannel.close()
        scope.cancel()
    }
}

data class NotificationTask(
    val recipientPhone: String,
    val message: String
)
