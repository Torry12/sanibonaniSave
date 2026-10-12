package com.sanibonani.save.data.repository.system

import com.sanibonani.save.data.remote.EdgeFunctionGateway
import com.sanibonani.save.domain.logging.AppLogger
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import javax.inject.Inject

/**
 * Polymorphic interface for different notification delivery channels.
 */
interface NotificationChannel {
    suspend fun deliver(recipientPhone: String, message: String): Result<Unit>
}

class WhatsAppChannel @Inject constructor(
    private val edgeFunctionGateway: EdgeFunctionGateway
) : NotificationChannel {

    override suspend fun deliver(recipientPhone: String, message: String): Result<Unit> = runCatching {
        val templatePayload = buildJsonObject {
            put("to", recipientPhone)
            put("message", message)
            put("template", buildJsonObject {
                put("name", "general_notification")
                put("language", buildJsonObject { put("code", "en_US") })
                putJsonArray("components") {
                    addJsonObject {
                        put("type", "body")
                        putJsonArray("parameters") {
                            addJsonObject {
                                put("type", "text")
                                put("text", message)
                            }
                        }
                    }
                }
            })
        }

        val textPayload = buildJsonObject {
            put("to", recipientPhone)
            put("message", message)
        }

        val templateResult = edgeFunctionGateway.invoke("send-whatsapp", templatePayload)
        if (templateResult.isSuccess) return@runCatching

        val templateError = templateResult.exceptionOrNull()
        if (templateError != null && isTemplateDeliveryFailure(templateError)) {
            AppLogger.w("WhatsAppChannel", "Template delivery failed for $recipientPhone. Retrying as plain text.")
            edgeFunctionGateway.invoke("send-whatsapp", textPayload).getOrThrow()
        } else {
            throw templateError ?: Exception("Unknown WhatsApp error")
        }
    }

    private fun isTemplateDeliveryFailure(error: Throwable): Boolean {
        val message = error.message?.lowercase() ?: return false
        return message.contains("template") ||
            message.contains("parameter") ||
            message.contains("language") ||
            message.contains("not found") ||
            message.contains("unapproved") ||
            message.contains("rejected")
    }
}
