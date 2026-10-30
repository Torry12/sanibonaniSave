package com.sanibonani.save.domain.service

import kotlinx.coroutines.flow.SharedFlow

/**
 * How the app should reach the backend during development.
 */
enum class LocalConnectionMode {
    AUTOMATIC,
    EMULATOR_BRIDGE,
    PHYSICAL_ADB,
    LAN_IP_CONFIG,
    CUSTOM_LAN_IP;

    val prettyName: String
        get() = when (this) {
            AUTOMATIC -> "Auto"
            EMULATOR_BRIDGE -> "Emulator"
            PHYSICAL_ADB -> "ADB Rev"
            LAN_IP_CONFIG -> "From Config"
            CUSTOM_LAN_IP -> "Custom IP"
        }
}

/**
 * The developer switches that move the app between the hosted and local backends.
 * Emits on [settingsChanged] whenever one of them is written.
 */
interface DevSettingsService {
    val settingsChanged: SharedFlow<Unit>
    var useOnlineDatabase: Boolean
    var localConnectionMode: LocalConnectionMode
    var localIpOverride: String
    var forceEmulatorIp: Boolean
    fun toggleDatabase()
}
