package com.sanibonani.save.simulation

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.ktor.client.*
import io.ktor.client.engine.android.*
import io.ktor.client.request.*
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InternetTest {
    @Test
    fun testInternet() = runBlocking {
        val client = HttpClient(Android)
        try {
            val response = client.get("https://www.google.com")
            println("Response: ${response.status}")
        } catch (e: Exception) {
            println("Internet failure: ${e.message}")
            throw e
        } finally {
            client.close()
        }
    }
}
