package com.sanibonani.save

import com.sanibonani.save.domain.utils.safeRunCatching
import android.app.Application
import androidx.preference.PreferenceManager
import com.google.firebase.analytics.FirebaseAnalytics
import com.sanibonani.save.analytics.AppAnalytics
import com.sanibonani.save.data.logging.AndroidLogSink
import com.sanibonani.save.data.sync.RealtimeSyncManager
import com.sanibonani.save.domain.event.EventHandlerInitializer
import com.sanibonani.save.domain.logging.AppLogger
import dagger.hilt.android.HiltAndroidApp
import androidx.work.Configuration
import androidx.hilt.work.HiltWorkerFactory
import javax.inject.Inject

@HiltAndroidApp
class SanibonaniApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var eventHandlerInitializer: EventHandlerInitializer

    @Inject
    lateinit var realtimeSyncManager: RealtimeSyncManager

    @Inject
    lateinit var androidLogSink: AndroidLogSink

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // Bind the pure-Kotlin domain logging facade to android.util.Log before any
        // other component logs, so startup diagnostics reach logcat.
        AppLogger.install(androidLogSink)

        AppAnalytics.initialize(FirebaseAnalytics.getInstance(this))

        // Start the background event handling system
        eventHandlerInitializer.initialize()

        // Start realtime sync for all entity types
        realtimeSyncManager.start()

        // Initialize OSMDroid for map views with extreme safety
        safeRunCatching {
            val config = org.osmdroid.config.Configuration.getInstance()
            config.userAgentValue = packageName

            // Mandatory load call for OSMDroid
            config.load(this, PreferenceManager.getDefaultSharedPreferences(this))

            // Wipe cache to prevent corrupted database hangs
            val mapCache = java.io.File(cacheDir, "osmdroid_safe")
            if (mapCache.exists()) mapCache.deleteRecursively()
            mapCache.mkdirs()

            config.osmdroidBasePath = mapCache
            config.osmdroidTileCache = java.io.File(mapCache, "tiles")
        }

        AppLogger.i("SanibonaniApp", "Application initialized")
    }

    companion object {
        const val CHANNEL_GENERAL    = "general"
        const val CHANNEL_PAYMENTS   = "payments"
        const val CHANNEL_SUSPENSION = "group_status"
        const val CHANNEL_ACTUARIAL  = "actuarial"
    }
}
