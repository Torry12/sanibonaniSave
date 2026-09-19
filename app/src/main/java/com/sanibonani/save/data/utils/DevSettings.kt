package com.sanibonani.save.data.utils

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DevSettings @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("dev_settings", Context.MODE_PRIVATE)

    var useOnlineDatabase: Boolean
        get() = prefs.getBoolean("use_online_database", false)
        set(value) = prefs.edit().putBoolean("use_online_database", value).apply()

    fun toggleDatabase() {
        useOnlineDatabase = !useOnlineDatabase
    }
}
