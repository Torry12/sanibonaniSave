package com.sanibonani.save.domain.utils

import android.os.Build

/**
 * Shared utility for identifying if the app is running on an Android Emulator.
 * Used to resolve the correct local host IP (10.0.2.2 for emulators vs 127.0.0.1 for adb reverse).
 */
object EmulatorUtils {

    fun isEmulator(): Boolean {
        // Refined detection logic to avoid false positives on some physical devices (like Honor/MediaTek)
        return (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
                || Build.FINGERPRINT.contains("generic")
                || Build.HARDWARE.contains("goldfish")
                || Build.HARDWARE.contains("ranchu")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || Build.PRODUCT.contains("sdk_google")
                || Build.PRODUCT.contains("google_sdk")
                || Build.PRODUCT.contains("sdk")
                || Build.PRODUCT.contains("sdk_x86")
                || Build.PRODUCT.contains("vbox86p")
                || Build.PRODUCT.contains("emulator")
                || Build.PRODUCT.contains("simulator")
                || (Build.BOARD.lowercase().contains("nox"))
                || (Build.BOOTLOADER.lowercase().contains("nox"))
                || (Build.HARDWARE.lowercase().contains("nox"))
                || (Build.PRODUCT.lowercase().contains("nox"))
                || (Build.SERIAL.lowercase().contains("nox"))
    }
}
