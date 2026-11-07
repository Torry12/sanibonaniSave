package com.sanibonani.save.data.logging

import android.content.Context
import com.sanibonani.save.domain.logging.AppLogSink
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [AppLogSink] that mirrors every line into the app's private files dir, on top of the
 * `android.util.Log` passthrough [AndroidLogSink].
 *
 * Some firmware drops app-authored logcat lines outright (the Huawei/MTK under test keeps
 * `init.svc.applogcat` stopped and refuses `persist.sys.huawei.debug.on` without root), which makes
 * every on-device diagnostic a guess. A file in `filesDir` is readable through
 * `adb shell run-as <applicationId>` on any debug build regardless of what the log daemon does.
 *
 * Writing never propagates a failure: a device that refuses the file must not fail the operation
 * that was being recorded.
 */
@Singleton
class DiagnosticFileSink @Inject constructor(
    @ApplicationContext private val context: Context,
    private val androidLogSink: AndroidLogSink
) : AppLogSink {

    override fun d(tag: String, message: String) {
        androidLogSink.d(tag, message)
        record(tag, message, null)
    }

    override fun i(tag: String, message: String) {
        androidLogSink.i(tag, message)
        record(tag, message, null)
    }

    override fun w(tag: String, message: String, throwable: Throwable?) {
        androidLogSink.w(tag, message, throwable)
        record(tag, message, throwable)
    }

    override fun e(tag: String, message: String, throwable: Throwable?) {
        androidLogSink.e(tag, message, throwable)
        record(tag, message, throwable)
    }

    override fun wtf(tag: String, message: String, throwable: Throwable?) {
        androidLogSink.wtf(tag, message, throwable)
        record(tag, message, throwable)
    }

    /** The tail that is still in the file, so a full file never has to be read back to be trimmed. */
    private val recent = ArrayDeque<String>()
    private var loaded = false

    @Synchronized
    private fun record(tag: String, message: String, throwable: Throwable?) {
        try {
            val file = File(context.filesDir, FILE_NAME)
            if (!loaded) {
                loaded = true
                if (file.exists()) {
                    recent.addAll(file.readLines())
                    while (recent.size > MAX_LINES) recent.removeFirst()
                }
            }
            val line = buildString {
                append(System.currentTimeMillis())
                append(' ').append(tag).append(' ')
                append(message.replace('\n', ' '))
                if (throwable != null) append(" [").append(throwable.message).append(']')
            }
            if (recent.size < MAX_LINES) {
                file.appendText(line + "\n")
                recent.addLast(line)
            } else {
                recent.addLast(line)
                recent.removeFirst()
                file.writeText(recent.joinToString("\n") + "\n")
            }
        } catch (_: Throwable) {
            // The operation being logged matters more than the record of it.
        }
    }

    companion object {
        const val FILE_NAME = "diagnostics.log"

        /** One screen's worth of verdicts, and small enough that the rolling rewrite stays cheap. */
        const val MAX_LINES = 300
    }
}
