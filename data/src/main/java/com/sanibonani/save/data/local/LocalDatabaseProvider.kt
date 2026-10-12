package com.sanibonani.save.data.local

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.room.Room
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Builds the Room database used as the local-first cache.
 *
 * Lives in `data.local` (not `data.remote`) because it constructs SQLite/Room state:
 * `data.remote` is for network transports. It previously sat in `data.remote`, which
 * put a local-DB builder in the network package and forced same-layer imports.
 */
@Singleton
class LocalDatabaseProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val seedScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun createDatabase(useOnline: Boolean): SanibonaniDatabase {
        val dbName = if (useOnline) "sanibonani_online.db" else "sanibonani_local.db"
        val db = Room.databaseBuilder(
            context,
            SanibonaniDatabase::class.java,
            dbName
        )
            .addMigrations(*ALL_MIGRATIONS)
            .fallbackToDestructiveMigration()
            .build()

        // Offline-first demo: prepopulate the local cache (only) in debuggable builds so a
        // fresh offline install is not blank. Idempotent and non-fatal inside DemoDataSeeder.
        val isDebuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (!useOnline && isDebuggable) {
            seedScope.launch { DemoDataSeeder.seedIfEmpty(db) }
        }
        return db
    }
}
