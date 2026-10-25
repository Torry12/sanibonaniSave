package com.sanibonani.save.data.logging

import android.util.Log
import com.sanibonani.save.domain.logging.AppLogSink
import javax.inject.Inject
import javax.inject.Singleton

/**
 * `android.util.Log`-backed [AppLogSink].
 *
 * This is the only place in the app that binds the domain logging facade to the
 * Android framework. It is installed into `AppLogger` from `SanibonaniApp.onCreate()`.
 */
@Singleton
class AndroidLogSink @Inject constructor() : AppLogSink {

    override fun d(tag: String, message: String) {
        Log.d(tag, message)
    }

    override fun i(tag: String, message: String) {
        Log.i(tag, message)
    }

    override fun w(tag: String, message: String, throwable: Throwable?) {
        if (throwable != null) Log.w(tag, message, throwable) else Log.w(tag, message)
    }

    override fun e(tag: String, message: String, throwable: Throwable?) {
        if (throwable != null) Log.e(tag, message, throwable) else Log.e(tag, message)
    }

    override fun wtf(tag: String, message: String, throwable: Throwable?) {
        if (throwable != null) Log.wtf(tag, message, throwable) else Log.wtf(tag, message)
    }
}
