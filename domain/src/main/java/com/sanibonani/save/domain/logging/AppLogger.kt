package com.sanibonani.save.domain.logging

import com.sanibonani.save.domain.BuildConfig

/**
 * Centralized logging for the SanibonaniSave platform.
 *
 * This object is a pure-Kotlin facade: all writes are delegated to an [AppLogSink]
 * so `:domain` stays free of `android.*` imports. The Android-backed sink is
 * installed by the Application class; until then output falls back to
 * stdout/stderr, which is what local JVM unit tests rely on (the JVM
 * `android.util.Log` is an unstubbed stub in unit tests).
 */
object AppLogger {
    private const val DEFAULT_TAG = "SanibonaniSave"
    private val debugLoggingEnabled: Boolean = BuildConfig.DEBUG

    private val breadcrumbs = mutableListOf<String>()
    private const val MAX_BREADCRUMBS = 50

    @Volatile
    private var sink: AppLogSink = StdoutAppLogSink

    /** Replaces the active sink. Safe to call more than once; last install wins. */
    fun install(sink: AppLogSink) {
        this.sink = sink
    }

    fun d(tag: String = DEFAULT_TAG, message: String) {
        if (!debugLoggingEnabled) return
        addBreadcrumb("DEBUG: $message")
        sink.d(tag, message)
    }

    fun i(tag: String = DEFAULT_TAG, message: String) {
        if (!debugLoggingEnabled) return
        addBreadcrumb("INFO: $message")
        sink.i(tag, message)
    }

    fun w(tag: String = DEFAULT_TAG, message: String, throwable: Throwable? = null) {
        addBreadcrumb("WARN: $message")
        sink.w(tag, message, throwable)
    }

    fun e(tag: String = DEFAULT_TAG, message: String, throwable: Throwable? = null) {
        addBreadcrumb("ERROR: $message")
        sink.e(tag, message, throwable)
    }

    fun fatal(tag: String = DEFAULT_TAG, message: String, throwable: Throwable) {
        addBreadcrumb("FATAL: $message")
        sink.wtf(tag, message, throwable)
    }

    /**
     * Adds a navigational breadcrumb for remote debugging session reconstruction.
     */
    fun addBreadcrumb(message: String) {
        synchronized(breadcrumbs) {
            breadcrumbs.add("${System.currentTimeMillis()}: $message")
            if (breadcrumbs.size > MAX_BREADCRUMBS) breadcrumbs.removeAt(0)
        }
    }

    fun getBreadcrumbs(): List<String> = synchronized(breadcrumbs) { breadcrumbs.toList() }

    /**
     * Logs a message with potential PII masked.
     * Use this for logging objects that might contain emails, phones, or names.
     */
    fun pii(tag: String = DEFAULT_TAG, message: String, piiData: Map<String, String?>) {
        if (!debugLoggingEnabled) return
        val masked = piiData.mapValues { (key, value) ->
            if (value == null) "null"
            else when {
                key.contains("email", ignoreCase = true) -> maskEmail(value)
                key.contains("phone", ignoreCase = true) -> maskPhone(value)
                key.contains("name", ignoreCase = true) -> maskName(value)
                key.contains("id_number", ignoreCase = true) -> maskIDNumber(value)
                else -> "***"
            }
        }
        d(tag, "$message: $masked")
    }

    private fun maskEmail(email: String): String {
        val parts = email.split("@")
        if (parts.size != 2) return "***"
        return "${parts[0].take(1)}***@${parts[1]}"
    }

    private fun maskPhone(phone: String): String {
        if (phone.length <= 3) return "***"
        return "*".repeat(phone.length - 3) + phone.takeLast(3)
    }

    private fun maskName(name: String): String {
        return name.split(" ").joinToString(" ") { if (it.isEmpty()) "" else "${it.first()}***" }
    }

    private fun maskIDNumber(idNumber: String): String {
        if (idNumber.length < 6) return "***"
        return idNumber.take(6) + "******" + idNumber.takeLast(2)
    }

    /**
     * Default sink used before the Android sink is installed. Keeps logs visible
     * in `:domain`-only JVM tests without requiring a mocked Android framework.
     */
    private object StdoutAppLogSink : AppLogSink {
        override fun d(tag: String, message: String) = println("D/$tag: $message")

        override fun i(tag: String, message: String) = println("I/$tag: $message")

        override fun w(tag: String, message: String, throwable: Throwable?) {
            System.err.println("W/$tag: $message")
            throwable?.printStackTrace()
        }

        override fun e(tag: String, message: String, throwable: Throwable?) {
            System.err.println("E/$tag: $message")
            throwable?.printStackTrace()
        }

        override fun wtf(tag: String, message: String, throwable: Throwable?) {
            System.err.println("E/$tag: [FATAL] $message")
            throwable?.printStackTrace()
        }
    }
}
