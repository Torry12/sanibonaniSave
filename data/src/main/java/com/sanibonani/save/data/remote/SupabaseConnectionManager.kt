package com.sanibonani.save.data.remote

import android.content.Context
import androidx.room.Room
import com.sanibonani.save.BuildConfig
import com.sanibonani.save.data.local.ALL_MIGRATIONS
import com.sanibonani.save.data.local.SanibonaniDatabase
import com.sanibonani.save.domain.logging.AppLogger
import com.sanibonani.save.domain.utils.DevSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.serializer.KotlinXSerializer
import io.github.jan.supabase.storage.Storage
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds

@Singleton
class SupabaseConnectionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json,
    private val okHttpClient: OkHttpClient,
    private val devSettings: DevSettings
) {
    private val tag = "SupabaseConnMgr"

    private val _currentClient = MutableStateFlow<SupabaseClient?>(null)
    val currentClient: StateFlow<SupabaseClient?> = _currentClient.asStateFlow()

    private val _currentDatabase = MutableStateFlow<SanibonaniDatabase?>(null)
    val currentDatabase: StateFlow<SanibonaniDatabase?> = _currentDatabase.asStateFlow()

    init {
        rebuildConnections()
    }

    @OptIn(io.github.jan.supabase.annotations.SupabaseInternal::class)
    fun rebuildConnections() {
        val useOnline = devSettings.useOnlineDatabase
        AppLogger.i(tag, "🔄 Rebuilding connections. Online: $useOnline")

        val newClient = createClient(useOnline)
        _currentClient.value = newClient

        val dbName = if (useOnline) "sanibonani_online.db" else "sanibonani_local.db"
        _currentDatabase.value?.close()

        val newDb = Room.databaseBuilder(
            context,
            SanibonaniDatabase::class.java,
            dbName
        ).addMigrations(*ALL_MIGRATIONS).apply {
            if (BuildConfig.DEBUG) {
                fallbackToDestructiveMigration()
            }
        }.build()

        _currentDatabase.value = newDb
        AppLogger.i(tag, "✅ Connections rebuilt. Active DB: $dbName")
    }

    @OptIn(io.github.jan.supabase.annotations.SupabaseInternal::class)
    private fun createClient(useOnline: Boolean): SupabaseClient {
        var url = if (useOnline) {
            BuildConfig.SUPABASE_URL_ONLINE
        } else {
            BuildConfig.SUPABASE_URL_LOCAL
        }.trim().removeSurrounding("\"").takeIf { it.isNotBlank() } ?: throw IllegalStateException("SUPABASE_URL is empty.")

        val anonKey = if (useOnline) {
            BuildConfig.SUPABASE_ANON_KEY_ONLINE
        } else {
            BuildConfig.SUPABASE_ANON_KEY_LOCAL
        }.trim().removeSurrounding("\"").takeIf { it.isNotBlank() } ?: throw IllegalStateException("SUPABASE_ANON_KEY is empty.")

        if (!useOnline) {
            val isEmulator = devSettings.forceEmulatorIp || isEmulatorHardware()
            if (isEmulator && (url.contains("127.0.0.1") || url.contains("localhost"))) {
                url = url.replace("127.0.0.1", "10.0.2.2").replace("localhost", "10.0.2.2")
                AppLogger.w(tag, "🔄 Emulator detected. Mapping localhost to Bridge IP: $url")
            }
        }

        val finalUrl = url
        return createSupabaseClient(
            supabaseUrl = finalUrl,
            supabaseKey = anonKey
        ) {
            httpConfig {
                install(HttpTimeout) {
                    val timeout = if (!useOnline && finalUrl.contains("10.0.2.2")) 10.seconds else 30.seconds
                    requestTimeoutMillis = timeout.inWholeMilliseconds
                    connectTimeoutMillis = timeout.inWholeMilliseconds
                    socketTimeoutMillis = timeout.inWholeMilliseconds
                }
                install(Logging) {
                    level = if (BuildConfig.DEBUG) LogLevel.HEADERS else LogLevel.NONE
                    logger = object : io.ktor.client.plugins.logging.Logger {
                        override fun log(message: String) {
                            if (BuildConfig.DEBUG) {
                                android.util.Log.d("Supabase", message)
                            }
                        }
                    }
                }
            }

            httpEngine = OkHttp.create {
                preconfigured = okHttpClient
            }
            defaultSerializer = KotlinXSerializer(json)

            install(Auth) {
                autoSaveToStorage = true
                alwaysAutoRefresh = true
            }
            install(Postgrest)
            install(Storage)
            install(Realtime)
            install(Functions)
        }
    }

    private fun isEmulatorHardware(): Boolean {
        return android.os.Build.FINGERPRINT.contains("generic")
                || android.os.Build.FINGERPRINT.contains("unknown")
                || android.os.Build.MODEL.contains("google_sdk")
                || android.os.Build.MODEL.contains("Emulator")
                || android.os.Build.MODEL.contains("Android SDK built for x86")
                || android.os.Build.MANUFACTURER.contains("Genymotion")
                || "google_sdk" == android.os.Build.PRODUCT
                || android.os.Build.HARDWARE.contains("goldfish")
                || android.os.Build.HARDWARE.contains("ranchu")
                || android.os.Build.PRODUCT.contains("sdk")
    }

    fun getClient(): SupabaseClient = _currentClient.value ?: throw IllegalStateException("SupabaseClient not initialized")
    fun getDatabase(): SanibonaniDatabase = _currentDatabase.value ?: throw IllegalStateException("SanibonaniDatabase not initialized")
}
