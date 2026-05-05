package com.sanibonani.save.data.remote

import com.sanibonani.save.data.logging.AppLogger
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
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
    ): Result<JsonObject> = runCatching {
        val token = supabaseClient.auth.currentSessionOrNull()?.accessToken
        if (requireAuth && token.isNullOrBlank()) {
            throw IllegalStateException("You need to sign in again before retrying this action.")
        }

        val requestBuilder = Request.Builder()
            .url("${supabaseClient.supabaseUrl.trimEnd('/')}/functions/v1/$functionName")
            .post(json.encodeToString(JsonObject.serializer(), payload).toRequestBody(JSON_MEDIA_TYPE))
            .header("Content-Type", "application/json")

        if (!token.isNullOrBlank()) {
            requestBuilder.header("Authorization", "Bearer $token")
        }

        okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
            val bodyString = response.body?.string().orEmpty().trim()
            if (!response.isSuccessful) {
                val parsedError = runCatching {
                    if (bodyString.isBlank()) null
                    else json.parseToJsonElement(bodyString).jsonObject["error"]?.let {
                        (it as? JsonPrimitive)?.content ?: it.toString().trim('"')
                    }
                }.getOrNull()
                val message = parsedError
                    ?: bodyString.takeIf { it.isNotBlank() }
                    ?: "Edge function $functionName failed with HTTP ${response.code}."
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

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
