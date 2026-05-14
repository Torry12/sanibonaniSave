package com.sanibonani.save.domain.model

/**
 * Thrown when the `send-whatsapp` Edge Function returns a non-2xx response.
 *
 * Carries the raw HTTP status code and, where available, the WhatsApp Cloud API
 * error code so that debug information can be surfaced in the UI without
 * needing to inspect Edge Function logs.
 *
 * @param httpCode       HTTP status code returned by the Edge Function (e.g. 400, 502).
 * @param waErrorCode    WhatsApp Cloud API numeric error code from the response body
 *                       (e.g. 131030 = re-engagement message, 190 = invalid token).
 *                       Null when the error originated before reaching the WhatsApp API.
 * @param waErrorType    WhatsApp error type string (e.g. "OAuthException"), if present.
 * @param apiMessage     Human-readable error detail from the server response.
 * @param attemptType    Which attempt failed: "template", "text-fallback", or "direct".
 */
class WhatsAppSendException(
    val httpCode: Int,
    val waErrorCode: Int? = null,
    val waErrorType: String? = null,
    val apiMessage: String,
    val attemptType: String = "send"
) : Exception(buildDebugMessage(httpCode, waErrorCode, waErrorType, apiMessage, attemptType)) {

    /** Returns a copy with the [attemptType] overridden. */
    fun copy(attemptType: String): WhatsAppSendException = WhatsAppSendException(
        httpCode = httpCode,
        waErrorCode = waErrorCode,
        waErrorType = waErrorType,
        apiMessage = apiMessage,
        attemptType = attemptType
    )

    companion object {
        fun buildDebugMessage(
            httpCode: Int,
            waErrorCode: Int?,
            waErrorType: String?,
            apiMessage: String,
            attemptType: String
        ): String = buildString {
            append("WhatsApp $attemptType failed")
            append(" [HTTP $httpCode")
            if (waErrorCode != null) append(" / WA-$waErrorCode")
            if (!waErrorType.isNullOrBlank()) append(" $waErrorType")
            append("]")
            if (apiMessage.isNotBlank()) {
                append(": ")
                append(apiMessage)
            }
        }
    }
}

