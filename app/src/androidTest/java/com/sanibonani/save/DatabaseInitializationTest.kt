package com.sanibonani.save

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sanibonani.save.data.local.SanibonaniDatabase
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
        runBlocking {
            database.groupDao().observeAllGroups().first()
        }
    }
}
