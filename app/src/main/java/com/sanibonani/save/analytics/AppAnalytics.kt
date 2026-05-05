package com.sanibonani.save.analytics

import android.os.Bundle
import android.util.Log
import com.google.firebase.analytics.FirebaseAnalytics

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
        Log.d("AppAnalytics", "event=$event params=$params")
    }
}
