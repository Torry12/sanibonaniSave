package com.sanibonani.save.data.repository.system

import java.io.IOException
import kotlinx.serialization.SerializationException

/**
 * Retry policy for the local outbox (`pending_operations`).
 */
object OutboxPolicy {

    /** Attempts a deterministically rejected operation gets before it stops blocking its lane. */
    const val MAX_PERMANENT_ATTEMPTS = 5

    fun isParked(retryCount: Int): Boolean = retryCount >= MAX_PERMANENT_ATTEMPTS

    /**
     * True when replaying the operation unchanged can never succeed: a malformed or unknown
     * payload, or a PostgREST/RLS/constraint/business-rule rejection.
     *
     * Only these failures advance toward the ceiling. Transport errors and 5xx stay transient so
     * that a long outage can never dead-letter a real money movement, and an unrecognised error is
     * treated as transient rather than silently parking an operation.
     */
    fun isPermanentFailure(error: Throwable): Boolean {
        if (error is IllegalArgumentException || error is SerializationException) return true
        if (error is IOException) return false
        val chain = error.causeChainText()
        return PERMANENT_MARKERS.any { chain.contains(it) }
    }

    private fun Throwable.causeChainText(): String = buildString {
        var current: Throwable? = this@causeChainText
        while (current != null) {
            append(current::class.simpleName.orEmpty().lowercase()).append(' ')
            append(current.message.orEmpty().lowercase()).append(' ')
            current = current.cause
        }
    }

    private val PERMANENT_MARKERS = listOf(
        "outbox payload is missing",
        "no handler for operation type",
        "numberformatexception",
        "serializationexception",
        "serializer for class",
        "missing serializer",
        "unexpected json token",
        "expected start of the",
        "could not find the",
        "permission denied",
        "row-level security",
        "violates",
        "does not exist",
        "syntax error",
        "insufficient",
        "exceeds",
        "http 400", "400 bad request",
        "http 403", "403 forbidden",
        "http 404", "404 not found",
        "http 422", "422 unprocessable",
        "42501", "23505", "23503", "23514", "42601"
    )
}
