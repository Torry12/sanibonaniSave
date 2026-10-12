package com.sanibonani.save.domain.utils

import com.sanibonani.save.domain.logging.AppLogger
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
 * Cancellation-safe replacement for [runCatching].
 *
 * [runCatching] swallows [CancellationException] which breaks cooperative
 * cancellation (structured concurrency). This variant rethrows cancellation
 * so coroutine scopes (e.g. Support Mode impersonation) tear down correctly.
 */
inline fun <T> safeRunCatching(
    tag: String = "safeRunCatching",
    block: () -> T
): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e // Never swallow cancellation
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

        msg.contains("Email not confirmed", ignoreCase = true) ||
            msg.contains("email_not_confirmed", ignoreCase = true) ->
            return "Please confirm your email address before signing in. Check your inbox for the confirmation link."

        msg.contains("Magic link", ignoreCase = true) && msg.contains("expired", ignoreCase = true) ->
            return "The login link has expired. Please request a new one."

        // Generic HTTP errors (Ktor / PostgREST / WhatsApp API etc.)
        // Avoid leaking raw codes or raw query URL parameters into the UI.
        msg.contains("IO Error: HTTP request to", ignoreCase = true) ||
            msg.contains("rest/v1/members", ignoreCase = true) ||
            msg.contains("rest/v1/groups", ignoreCase = true) ->
            return "Unable to sync with remote server. Using cached data."

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

        // Database constraint violations (Unique/Foreign/Check Key)
        msg.contains("violates unique constraint", ignoreCase = true) ||
            msg.contains("23505") ->
            return "This record already exists. Please check your data and try again."

        msg.contains("violates foreign key constraint", ignoreCase = true) ||
            msg.contains("23503") ->
            return "This action cannot be completed because it depends on other data that might have been removed."

        msg.contains("contributions_amount_check", ignoreCase = true) ||
            (msg.contains("23514") && msg.contains("amount", ignoreCase = true)) ->
            return "Contribution amount must be greater than zero."

        // RPC Business Logic Exceptions
        msg.contains("Repayment amount exceeds", ignoreCase = true) ->
            return "Repayment amount exceeds the remaining loan balance."

        msg.contains("Insufficient group balance", ignoreCase = true) ||
            msg.contains("liquidity", ignoreCase = true) ->
            return "The group does not have enough available funds for this operation."

        // Syntax errors / psql command mismatches
        msg.contains("syntax error", ignoreCase = true) ||
            msg.contains("42601") -> {
            if (msg.contains("\\")) {
                return "SQL Syntax Error: It looks like you tried to run a psql script (with \\ commands) in a tool that doesn't support them. Please use the Supabase CLI or psql tool."
            }
            return "A syntax error occurred in the database request. Please check your query or app version."
        }

        // Schema mismatch often surfaces as \"column ... does not exist\"
        (msg.contains("column", ignoreCase = true) && msg.contains("does not exist", ignoreCase = true)) ->
            return "The server database schema is out of date. Please run the latest Supabase schema/migrations and try again."

        // Missing table / stale PostgREST schema cache should not leak raw request URL/JWT headers.
        msg.contains("could not find the table", ignoreCase = true) ||
            (msg.contains("relation", ignoreCase = true) && msg.contains("does not exist", ignoreCase = true)) ->
            return "Server database tables are not aligned with this app version. Please apply the latest backend migrations and refresh."

        // PII Sanitization: If any message contains a sequence that looks like an email or ID number,
        // and hasn't been caught yet, mask it.
        msg.contains("@") -> return "A data error occurred. Please try again or contact support."
        msg.contains(Regex("\\d{13}")) -> return "A validation error occurred with an identification number."
    }

    return when (this) {
        is IllegalArgumentException -> msg.ifBlank { "Invalid input" }
        is NoSuchElementException -> "Data not found"
        is SecurityException -> "Permission denied"
        is java.net.SocketTimeoutException -> "Connection timeout: ${this.message}"
        is java.net.ConnectException -> {
            if (msg.contains("127.0.0.1") || msg.contains("localhost")) {
                val port = msg.substringAfter(":", "").substringBefore(" ").trim().filter { it.isDigit() }.ifBlank { "45421" }
                return "Connection failed: If using a physical device, run 'adb reverse tcp:$port tcp:$port' on your PC, OR use your computer's LAN IP. Ensure your local Supabase server is running."
            }
            if (msg.contains("10.0.2.2")) {
                return "Connection failed: 10.0.2.2 only works inside an Android Emulator. If using a physical device, turn OFF 'Force Emulator Bridge' and use 'adb reverse' or LAN IP."
            }
            "Network connect error: ${this.message}"
        }
        is java.net.UnknownHostException -> "Unknown host: ${this.message}"
        is java.io.IOException -> {
            if (msg.contains("127.0.0.1") || msg.contains("localhost")) {
                return "Connection failed: If you are using a physical device, run 'adb reverse tcp:45421 tcp:45421' on your PC. If on an emulator, ensure the Supabase URL is set to 10.0.2.2."
            }
            if (msg.contains("10.0.2.2")) {
                return "Connection failed: 10.0.2.2 only works inside an Android Emulator. If you are using a physical device, turn OFF 'Force Emulator Bridge' and use 'adb reverse'."
            }
            "IO Error: ${this.message}"
        }
        is IllegalStateException -> msg.ifBlank { "Invalid operation" }
        else -> msg.ifBlank { "An error occurred: ${this.message}" }
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
