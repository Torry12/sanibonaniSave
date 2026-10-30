package com.sanibonani.save.data.utils
import android.content.Context
import android.content.SharedPreferences
import com.sanibonani.save.domain.service.DevSettingsService
import com.sanibonani.save.domain.service.LocalConnectionMode
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

@Singleton
class DevSettings @Inject constructor(
    @ApplicationContext context: Context
) : DevSettingsService {
    private val prefs: SharedPreferences = context.getSharedPreferences("dev_settings", Context.MODE_PRIVATE)

    private val _settingsChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val settingsChanged = _settingsChanged.asSharedFlow()

    override var useOnlineDatabase: Boolean
        get() = prefs.getBoolean("use_online_database", false)
        set(value) {
            prefs.edit().putBoolean("use_online_database", value).apply()
            _settingsChanged.tryEmit(Unit)
        }

    override var localConnectionMode: LocalConnectionMode
        get() {
            val name = prefs.getString("local_conn_mode", LocalConnectionMode.AUTOMATIC.name)
            return try { LocalConnectionMode.valueOf(name!!) } catch (e: Exception) { LocalConnectionMode.AUTOMATIC }
        }
        set(value) {
            prefs.edit().putString("local_conn_mode", value.name).apply()
            _settingsChanged.tryEmit(Unit)
        }

    override var localIpOverride: String
        get() = prefs.getString("local_ip_override", "") ?: ""
        set(value) {
            prefs.edit().putString("local_ip_override", value).apply()
            _settingsChanged.tryEmit(Unit)
        }

    // Compatibility layer for existing code
    override var forceEmulatorIp: Boolean
        get() = localConnectionMode == LocalConnectionMode.EMULATOR_BRIDGE
        set(value) {
            localConnectionMode = if (value) LocalConnectionMode.EMULATOR_BRIDGE else LocalConnectionMode.PHYSICAL_ADB
        }

    override fun toggleDatabase() {
        useOnlineDatabase = !useOnlineDatabase
    }
}
