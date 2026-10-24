package com.sanibonani.save.domain.logging

/**
 * Pure-Kotlin logging port.
 *
 * `:domain` never imports `android.util.Log`. The Android-backed implementation
 * (`AndroidLogSink`) lives in `:data` and is installed once from the Application
 * class. Until it is installed, [AppLogger] falls back to stdout/stderr which
 * keeps local JVM unit tests working without a mocked Android framework.
 */
interface AppLogSink {
    /** Verbose/developer diagnostics; suppressed in release builds by [AppLogger]. */
    fun d(tag: String, message: String)

    /** Informational progress markers; suppressed in release builds by [AppLogger]. */
    fun i(tag: String, message: String)

    /** Recoverable problem that the user may need to know about. */
    fun w(tag: String, message: String, throwable: Throwable? = null)

    /** Failure that aborted or degraded an operation. */
    fun e(tag: String, message: String, throwable: Throwable? = null)

    /** Unrecoverable failure. */
    fun wtf(tag: String, message: String, throwable: Throwable?)
}
