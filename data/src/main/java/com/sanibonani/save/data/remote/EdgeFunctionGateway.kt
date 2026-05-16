package com.sanibonani.save.data.remote

import com.sanibonani.save.data.logging.AppLogger
import com.sanibonani.save.domain.model.WhatsAppSendException
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EdgeFunctionGateway @Inject constructor(
    private val supabaseClient: SupabaseClient,
    private val okHttpClient: OkHttpClient,
    private val json: Json
) {
    suspend fun invoke(
        functionName: String,
        payload: JsonObject = buildJsonObject { },
        requireAuth: Boolean = true
    ): Result<JsonObject> = withContext(Dispatchers.IO) {
        runCatching {
            val token = supabaseClient.auth.currentSessionOrNull()?.accessToken
            if (requireAuth && token.isNullOrBlank()) {
                throw IllegalStateException("You need to sign in again before retrying this action.")
            }

            val baseUrl = normalizeSupabaseUrl(supabaseClient.supabaseUrl)
            val requestBuilder = Request.Builder()
                .url("${baseUrl.trimEnd('/')}/functions/v1/$functionName")
                .post(json.encodeToString(JsonObject.serializer(), payload).toRequestBody(JSON_MEDIA_TYPE))
                .header("Content-Type", "application/json")

            if (!token.isNullOrBlank()) {
                requestBuilder.header("Authorization", "Bearer $token")
            }

            okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
                val bodyString = response.body?.string().orEmpty().trim()
                if (!response.isSuccessful) {
                    // Parse the top-level "error" field from the response JSON.
                    val parsedBody = runCatching {
                        if (bodyString.isBlank()) null
                        else json.parseToJsonElement(bodyString).jsonObject
                    }.getOrNull()

                    val errorNode = parsedBody?.get("error")
                    val parsedErrorMessage: String? = when {
                        errorNode is JsonPrimitive -> errorNode.content
                        errorNode is JsonObject -> {
                            // WhatsApp Cloud API nested error object: { "error": { "message": "...", "code": 131030, "type": "..." } }
                            errorNode["message"]?.jsonPrimitive?.content
                        }
                        else -> null
                    }

                    val message = parsedErrorMessage
                        ?: bodyString.takeIf { it.isNotBlank() }
                        ?: "Edge function $functionName failed with HTTP ${response.code}."

                    // For WhatsApp send failures, throw a richer exception with debug codes.
                    if (functionName == "send-whatsapp") {
                        val waCode = runCatching {
                            when (errorNode) {
                                is JsonObject -> errorNode["code"]?.jsonPrimitive?.intOrNull
                                else -> parsedBody?.get("code")?.jsonPrimitive?.intOrNull
                            }
                        }.getOrNull()
                        val waType = runCatching {
                            when (errorNode) {
                                is JsonObject -> errorNode["type"]?.jsonPrimitive?.content
                                else -> parsedBody?.get("type")?.jsonPrimitive?.content
                            }
                        }.getOrNull()
                        throw WhatsAppSendException(
                            httpCode = response.code,
                            waErrorCode = waCode,
                            waErrorType = waType,
                            apiMessage = message
                        )
                    }

                    throw IllegalStateException(message)
                }

                if (bodyString.isBlank()) {
                    return@use buildJsonObject { }
                }

                val parsed = json.parseToJsonElement(bodyString)
                when {
                    parsed is JsonObject -> parsed
                    else -> buildJsonObject { put("value", JsonPrimitive(parsed.toString())) }
                }
            }
        }.onFailure { throwable ->
            AppLogger.e("EdgeFunctionGateway", "Edge function invocation failed: $functionName", throwable)
        }
    }

    companion object {
        internal fun normalizeSupabaseUrl(rawUrl: String): String {
            val trimmed = rawUrl.trim().trimEnd('/')
            if (trimmed.isBlank()) {
                throw IllegalStateException("Supabase URL is empty. Check your configuration.")
            }

            if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                return trimmed
            }

            return if (trimmed.contains('.')) {
                "https://$trimmed"
            } else {
                "https://$trimmed.supabase.co"
            }
        }

        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
