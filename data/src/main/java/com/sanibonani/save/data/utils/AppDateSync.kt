 package com.sanibonani.save.data.utils

import android.content.Context
import android.content.SharedPreferences
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * AppDateSync provides synchronized date handling for the application.
 *
 * This utility ensures that:
 * 1. The app uses the device's system clock for date calculations
 * 2. Date consistency is maintained across app sessions
 * 3. Payment calculations use accurate, current dates
 *
 * Usage:
 * - Call syncOnLaunch() when the app starts
 * - Call syncOnExit() when the app goes to background
 * - Use getCurrentDate() for all date-based calculations
 */
object AppDateSync {

    private const val PREFS_NAME = "app_date_sync"
    private const val KEY_LAST_SYNC_TIME = "last_sync_time"
    private const val KEY_LAST_KNOWN_DATE = "last_known_date"

    @Volatile
    private var currentSystemDate: LocalDate = LocalDate.now()

    @Volatile
    private var lastSyncTimestamp: Long = System.currentTimeMillis()

    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    /**
     * Returns the current date synchronized with the system clock.
     * This should be used for all date-sensitive operations.
     */
    fun getCurrentDate(): LocalDate {
        // Always refresh from system clock to ensure accuracy
        currentSystemDate = LocalDate.now()
        return currentSystemDate
    }

    /**
     * Returns the current date-time synchronized with the system clock.
     */
    fun getCurrentDateTime(): LocalDateTime {
        return LocalDateTime.now()
    }

    /**
     * Returns the current instant (UTC timestamp).
     */
    fun getCurrentInstant(): Instant {
        return Instant.now()
    }

    /**
     * Sync the app's date state on application launch.
     * Should be called in Application.onCreate() or MainActivity.onCreate()
     */
    fun syncOnLaunch(context: Context) {
        val prefs = getPrefs(context)

        // Update current date from system clock
        currentSystemDate = LocalDate.now()
        lastSyncTimestamp = System.currentTimeMillis()

        // Store the sync event
        prefs.edit()
            .putLong(KEY_LAST_SYNC_TIME, lastSyncTimestamp)
            .putString(KEY_LAST_KNOWN_DATE, currentSystemDate.format(dateFormatter))
            .apply()

        android.util.Log.d("AppDateSync", "📅 Date synced on launch: $currentSystemDate")
    }

    /**
     * Sync the app's date state when going to background or exiting.
     * Should be called in Activity.onPause() or Activity.onStop()
     */
    fun syncOnExit(context: Context) {
        val prefs = getPrefs(context)

        // Capture current state before exiting
        currentSystemDate = LocalDate.now()
        lastSyncTimestamp = System.currentTimeMillis()

        // Store the state for next session
        prefs.edit()
            .putLong(KEY_LAST_SYNC_TIME, lastSyncTimestamp)
            .putString(KEY_LAST_KNOWN_DATE, currentSystemDate.format(dateFormatter))
            .apply()

        android.util.Log.d("AppDateSync", "📅 Date synced on exit: $currentSystemDate")
    }

    /**
     * Called when app resumes to verify date hasn't changed (e.g., device was off overnight)
     */
    fun syncOnResume(context: Context) {
        val prefs = getPrefs(context)
        val previousDate = prefs.getString(KEY_LAST_KNOWN_DATE, null)

        // Update to current system date
        currentSystemDate = LocalDate.now()
        lastSyncTimestamp = System.currentTimeMillis()

        val currentDateStr = currentSystemDate.format(dateFormatter)

        // Check if date changed while app was paused
        if (previousDate != null && previousDate != currentDateStr) {
            android.util.Log.i("AppDateSync", "📅 Date changed during pause: $previousDate -> $currentDateStr")
        }

        // Update stored date
        prefs.edit()
            .putLong(KEY_LAST_SYNC_TIME, lastSyncTimestamp)
            .putString(KEY_LAST_KNOWN_DATE, currentDateStr)
            .apply()
    }

    /**
     * Get the timestamp of the last sync operation.
     */
    fun getLastSyncTimestamp(): Long = lastSyncTimestamp

    /**
     * Check if a date sync has occurred recently (within the last minute).
     */
    fun isSyncRecent(): Boolean {
        return System.currentTimeMillis() - lastSyncTimestamp < 60_000
    }

    /**
     * Force a sync with the system clock.
     * Use when you need to ensure the most current date/time.
     */
    fun forceSync(context: Context) {
        syncOnLaunch(context)
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
}

