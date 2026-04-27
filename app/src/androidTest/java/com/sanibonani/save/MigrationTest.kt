package com.sanibonani.save

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sanibonani.save.data.local.ALL_MIGRATIONS
import com.sanibonani.save.data.local.SanibonaniDatabase
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val TEST_DB = "migration-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        SanibonaniDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    @Throws(IOException::class)
    fun migrateAll() {
        // Create earliest version of the database if schema exists
        // Given we have schemas from 1 to 15, let's start from 15 (or any existing schema)
        // Since we have 15.json, let's start from 15 to latest
        helper.createDatabase(TEST_DB, 15).apply {
            close()
        }

        // Open latest version and run all migrations
        val db = helper.runMigrationsAndValidate(TEST_DB, 30, true, *ALL_MIGRATIONS)
        
        // Verify specifically the new 'payouts' table exists in version 27+
        val cursor = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='payouts'")
        assert(cursor.moveToFirst()) { "Payouts table should exist in version 30" }
        cursor.close()

        // Verify 'type' column in 'contributions' table from version 29
        val contribCursor = db.query("PRAGMA table_info(contributions)")
        var hasTypeColumn = false
        while (contribCursor.moveToNext()) {
            if (contribCursor.getString(contribCursor.getColumnIndexOrThrow("name")) == "type") {
                hasTypeColumn = true
                break
            }
        }
        contribCursor.close()
        assert(hasTypeColumn) { "Contributions table should have 'type' column in version 30" }
    }
}
