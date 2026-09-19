package com.sanibonani.save.data.remote

import android.content.Context
import androidx.room.Room
import com.sanibonani.save.data.BuildConfig
import com.sanibonani.save.data.local.ALL_MIGRATIONS
import com.sanibonani.save.data.local.SanibonaniDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalDatabaseProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun createDatabase(useOnline: Boolean): SanibonaniDatabase {
        val dbName = if (useOnline) "sanibonani_online.db" else "sanibonani_local.db"
        return Room.databaseBuilder(
            context,
            SanibonaniDatabase::class.java,
            dbName
        ).addMigrations(*ALL_MIGRATIONS).apply {
            if (BuildConfig.DEBUG) {
                fallbackToDestructiveMigration()
            }
        }.build()
    }
}
