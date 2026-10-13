package com.sanibonani.save

import android.app.Application
import android.util.Log
import com.google.firebase.analytics.FirebaseAnalytics
import com.sanibonani.save.analytics.AppAnalytics
import com.sanibonani.save.data.sync.RealtimeSyncManager
import com.sanibonani.save.domain.event.EventHandlerInitializer
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class SanibonaniApp : Application() {

    @Inject
    lateinit var eventHandlerInitializer: EventHandlerInitializer

    @Inject
    lateinit var realtimeSyncManager: RealtimeSyncManager

    override fun onCreate() {
        super.onCreate()
        AppAnalytics.initialize(FirebaseAnalytics.getInstance(this))

        // Start the background event handling system
        eventHandlerInitializer.initialize()

        // Start realtime sync for all entity types
        realtimeSyncManager.start()

        Log.d("SanibonaniApp", "Application initialized")
    }

    companion object {
        const val CHANNEL_GENERAL    = "general"
        const val CHANNEL_PAYMENTS   = "payments"
        const val CHANNEL_SUSPENSION = "group_status"
        const val CHANNEL_ACTUARIAL  = "actuarial"
    }
}
