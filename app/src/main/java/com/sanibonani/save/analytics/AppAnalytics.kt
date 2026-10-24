package com.sanibonani.save.analytics

import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.sanibonani.save.domain.logging.AppLogger

object AppAnalytics {
    private var firebaseAnalytics: FirebaseAnalytics? = null

    fun initialize(firebaseAnalytics: FirebaseAnalytics) {
        this.firebaseAnalytics = firebaseAnalytics
    }

    fun track(event: String, params: Map<String, String?> = emptyMap()) {
        val analytics = firebaseAnalytics ?: return
        val bundle = Bundle().apply {
            params.forEach { (key, value) ->
                value?.takeIf { it.isNotBlank() }?.let { putString(key, it.take(100)) }
            }
        }
        analytics.logEvent(event, bundle)
        AppLogger.d("AppAnalytics", "event=$event params=$params")
    }
}
