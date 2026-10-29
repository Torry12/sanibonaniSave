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
import com.sanibonani.save.domain.usecase.admin.RefreshPlatformConfigUseCase
import dagger.hilt.android.HiltAndroidApp
import androidx.work.Configuration
import androidx.hilt.work.HiltWorkerFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
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

    @Inject
    lateinit var refreshPlatformConfig: RefreshPlatformConfigUseCase

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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

        // Billing reads the in-memory config; without this seed it would charge PlatformConfig defaults.
        appScope.launch {
            refreshPlatformConfig().onFailure {
                AppLogger.d("SanibonaniApp", "Platform fee refresh deferred: ${it.message}")
            }
        }

        // Initialize OSMDroid for map views with extreme safety
        safeRunCatching {
            val config = org.osmdroid.config.Configuration.getInstance()
            config.userAgentValue = packageName

            // Mandatory load call for OSMDroid
            config.load(this, PreferenceManager.getDefaultSharedPreferences(this))

            val mapCache = java.io.File(cacheDir, "osmdroid_safe")
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
