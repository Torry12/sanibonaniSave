package com.sanibonani.save.data.logging

import com.sanibonani.save.domain.BuildConfig
import android.util.Log

/**
 * Centralized logging for the SanibonaniSave platform.
 *
 * Note on JVM unit tests:
 * Android's `android.util.Log` is a stub in local unit tests and throws
 * "Method X in android.util.Log not mocked" at runtime.
 * We therefore guard Log calls and fallback to stdout/stderr.
 */
object AppLogger {
    private const val DEFAULT_TAG = "SanibonaniSave"
    private val debugLoggingEnabled: Boolean = BuildConfig.DEBUG

    private inline fun runLogSafely(fallback: () -> Unit, block: () -> Unit) {
        try {
            block()
        } catch (_: Throwable) {
            // Includes RuntimeException("Method … not mocked") from local unit tests.
            fallback()
        }
    }

    fun d(tag: String = DEFAULT_TAG, message: String) {
        if (!debugLoggingEnabled) return
        runLogSafely(
            fallback = { println("D/$tag: $message") },
            block = { Log.d(tag, message) }
        )
    }

    fun i(tag: String = DEFAULT_TAG, message: String) {
        if (!debugLoggingEnabled) return
        runLogSafely(
            fallback = { println("I/$tag: $message") },
            block = { Log.i(tag, message) }
        )
    }

    fun w(tag: String = DEFAULT_TAG, message: String, throwable: Throwable? = null) {
        runLogSafely(
            fallback = {
                System.err.println("W/$tag: $message")
                throwable?.printStackTrace()
            },
            block = { Log.w(tag, message, throwable) }
        )
    }

    fun e(tag: String = DEFAULT_TAG, message: String, throwable: Throwable? = null) {
        runLogSafely(
            fallback = {
                System.err.println("E/$tag: $message")
                throwable?.printStackTrace()
            },
            block = { Log.e(tag, message, throwable) }
        )
    }

    fun fatal(tag: String = DEFAULT_TAG, message: String, throwable: Throwable) {
        runLogSafely(
            fallback = {
                System.err.println("E/$tag: [FATAL] $message")
                throwable.printStackTrace()
            },
            block = { Log.e(tag, "[FATAL] $message", throwable) }
        )
    }
}
