package com.sanibonani.save.data.utils

import com.sanibonani.save.data.logging.AppLogger
import kotlinx.coroutines.CancellationException

/**
 * Safe result extension functions to handle errors consistently across the app.
 * Prevents exception leaks and provides standardized error messages.
 */

/**
 * Execute a block of code safely, catching exceptions and logging them.
 * Prevents crash propagation.
 */
inline fun <T> safeCall(
    tag: String = "safeCall",
    block: () -> T
): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e // Don't catch cancellations
} catch (e: Exception) {
    AppLogger.e(tag, "Error: ${e.message}", e)
    Result.failure(e)
}

/**
 * Convert Throwable to user-friendly error message
 */
fun Throwable.toUserMessage(): String {
    val msg = this.message.orEmpty()

    // Always apply message-based mappings FIRST.
    // Some libraries wrap decoding/network exceptions inside IllegalStateException/RuntimeException,
    // and we still want to show a friendly message.
    when {
        // Supabase auth duplicate-account / opaque signup failures should never leak raw request details.
        msg.contains("already registered", ignoreCase = true) ||
            msg.contains("user already", ignoreCase = true) ||
            msg.contains("already been registered", ignoreCase = true) ||
            ((msg.contains("unknown error", ignoreCase = true) || msg.contains("unprocessable", ignoreCase = true)) &&
                msg.contains("/auth/v1/signup", ignoreCase = true)) ||
            (msg.contains("/auth/v1/signup", ignoreCase = true) && msg.contains("headers:", ignoreCase = true)) ->
            return "This email is already registered. Please sign in or reset your password."

        // Login failures should not present raw provider wording.
        msg.contains("invalid login credentials", ignoreCase = true) ||
            msg.contains("invalid_credentials", ignoreCase = true) ->
            return "Invalid email or password. Please try again or reset your password."

        // Generic HTTP errors (Ktor / PostgREST / WhatsApp API etc.)
        // Avoid leaking raw codes like "HTTP 400" into the UI.
        msg.contains("HTTP 400", ignoreCase = true) ||
            msg.contains("400 Bad Request", ignoreCase = true) ->
            return "Request failed. Please check your details and try again."

        msg.contains("HTTP 401", ignoreCase = true) ||
            msg.contains("401 Unauthorized", ignoreCase = true) ->
            return "Your session has expired. Please log in again."

        msg.contains("HTTP 403", ignoreCase = true) ||
            msg.contains("403 Forbidden", ignoreCase = true) ->
            return "You don’t have permission to perform this action."

        msg.contains("HTTP 404", ignoreCase = true) ||
            msg.contains("404 Not Found", ignoreCase = true) ->
            return "We couldn’t find the requested item. Please refresh and try again."

        msg.contains("HTTP 429", ignoreCase = true) ||
            msg.contains("429 Too Many", ignoreCase = true) ->
            return "Too many requests. Please wait a moment and try again."

        msg.contains("HTTP 500", ignoreCase = true) ||
            msg.contains("Internal Server Error", ignoreCase = true) ->
            return "Server error. Please try again in a few minutes."

        // Kotlin Serialization / API shape mismatch (commonly happens if server returns an object
        // but the client attempts to decode a list, or vice versa)
        msg.contains("Unexpected JSON token", ignoreCase = true) ||
            msg.contains("Expected start of the array", ignoreCase = true) ||
            msg.contains("Expected start of the object", ignoreCase = true) ->
            return "We received an unexpected response from the server. Please try again. If the problem persists, update the app or contact support."

        // Missing serializer for 'Any' (Kotlin Serialization error)
        msg.contains("Serializer for class 'Any' is not found", ignoreCase = true) ->
            return "Data structure error: Some information could not be processed. Please check your model definitions for 'Any' types."

        // Supabase/PostgREST often returns SQLSTATE 42501 for missing GRANTs
        msg.contains("permission denied", ignoreCase = true) ||
            msg.contains("42501") ->
            return "Database permissions are not configured. Please run the Supabase grants/RLS setup script and try again."

        // Supabase Storage RLS / policy failures
        msg.contains("storage.objects", ignoreCase = true) ->
            return "File storage access is blocked by security rules. Please ensure Supabase Storage buckets and policies are configured."

        // Generic RLS / row-level security violation (tables)
        msg.contains("row-level security", ignoreCase = true) ||
            msg.contains("violates row-level security", ignoreCase = true) ->
            return "You don't have permission to access or modify this data (RLS Violation). Check your user role or the table's security policies."

        // Missing bucket / misconfiguration
        (msg.contains("bucket", ignoreCase = true) && msg.contains("not found", ignoreCase = true)) ||
            msg.contains("Bucket not found", ignoreCase = true) ->
            return "File storage is not configured on the server. Please create the required Supabase Storage buckets and apply the storage policies."

        // Large upload payload
        msg.contains("Payload too large", ignoreCase = true) ||
            msg.contains("Request Entity Too Large", ignoreCase = true) ||
            msg.contains("413") ->
            return "This file is too large to upload. Please choose a smaller file and try again."

        // Schema mismatch often surfaces as "column ... does not exist"
        (msg.contains("column", ignoreCase = true) && msg.contains("does not exist", ignoreCase = true)) ->
            return "The server database schema is out of date. Please run the latest Supabase schema/migrations and try again."
    }

    return when (this) {
        is IllegalArgumentException -> msg.ifBlank { "Invalid input" }
        is NoSuchElementException -> "Data not found"
        is SecurityException -> "Permission denied"
        is java.net.SocketTimeoutException -> "Connection timeout. Check your internet"
        is java.net.ConnectException -> "Network error. Check your internet"
        is java.net.UnknownHostException -> "No internet connection. Please check your network."
        is java.io.IOException -> "Network error. Please try again"
        is IllegalStateException -> msg.ifBlank { "Invalid operation" }
        else -> msg.ifBlank { "An error occurred" }
    }
}

/**
 * Standardized way to get error message from a Throwable
 */
fun Throwable.getErrorMessage(): String = this.toUserMessage()

/**
 * Log error with tag and return user-friendly message
 */
fun Throwable.logAndGetMessage(tag: String): String {
    AppLogger.e(tag, this.message ?: "Unknown error", this)
    return this.toUserMessage()
}

/**
 * Safe execution with error callback
 */
inline fun <T> safeCallWithCallback(
    tag: String,
    onError: (String) -> Unit = {},
    block: () -> T
): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    val message = e.logAndGetMessage(tag)
    onError(message)
    Result.failure(e)
}

/**
 * Chain results safely with error propagation
 */
inline fun <T, R> Result<T>.flatMap(
    tag: String = "flatMap",
    transform: (T) -> Result<R>
): Result<R> = try {
    when {
        this.isSuccess -> {
            val value = this.getOrNull()
            if (value != null) transform(value)
            else Result.failure(IllegalStateException("Null value in successful result"))
        }
        else -> Result.failure(this.exceptionOrNull() ?: Exception("Unknown error"))
    }
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    AppLogger.e(tag, "flatMap error: ${e.message}", e)
    Result.failure(e)
}

/**
 * Get user-friendly error message from Result
 */
fun <T> Result<T>.getErrorMessage(): String? = 
    this.exceptionOrNull()?.toUserMessage()

/**
 * Execute callback safely without throwing
 */
inline fun <T> Result<T>.onSuccessSafe(action: (T) -> Unit): Result<T> = try {
    onSuccess(action)
    this
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    AppLogger.e("onSuccessSafe", "Callback error: ${e.message}", e)
    this
}

/**
 * Execute callback safely without throwing
 */
inline fun <T> Result<T>.onFailureSafe(action: (Throwable) -> Unit): Result<T> = try {
    onFailure(action)
    this
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    AppLogger.e("onFailureSafe", "Error callback failed: ${e.message}", e)
    this
}

/**
 * Recover from error with fallback value
 */
fun <T> Result<T>.recoverValue(fallback: T): T = 
    this.getOrNull() ?: fallback

/**
 * Recover from error with transform
 */
inline fun <T> Result<T>.recover(transform: (Throwable) -> T): T =
    this.getOrNull() ?: transform(this.exceptionOrNull() ?: Exception("Unknown error"))
