package com.sanibonani.save

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sanibonani.save.data.local.SanibonaniDatabase
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class DatabaseInitializationTest {

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var database: SanibonaniDatabase

    @Test
    fun testDatabaseInitialization() {
        hiltRule.inject()
        // Try to run a simple query to ensure the database can be opened and the schema is valid
        database.groupDao().getAllGroups()
    }
}
