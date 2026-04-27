package com.sanibonani.save

import com.sanibonani.save.data.remote.GeoapifyService
import com.sanibonani.save.data.remote.GeoapifyResponse
import com.sanibonani.save.data.remote.Feature
import com.sanibonani.save.data.remote.Properties
import com.sanibonani.save.data.remote.Geometry
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType

class GeoapifyServiceTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var service: GeoapifyService

    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        val contentType = "application/json".toMediaType()
        service = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(GeoapifyService::class.java)
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `autocomplete returns results correctly`() = runBlocking {
        val mockResponseBody = """
            {
              "features": [
                {
                  "properties": {
                    "formatted": "Soweto, Johannesburg, South Africa",
                    "city": "Johannesburg",
                    "suburb": "Soweto",
                    "country": "South Africa"
                  },
                  "geometry": {
                    "coordinates": [27.864, -26.237]
                  }
                }
              ]
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setBody(mockResponseBody).setResponseCode(200))

        val response = service.autocomplete("Soweto", "fake_key")

        assertEquals(1, response.features.size)
        assertEquals("Johannesburg", response.features[0].properties.city)
        assertEquals("Soweto", response.features[0].properties.suburb)
        assertEquals(27.864, response.features[0].geometry.coordinates[0], 0.001)
        assertEquals(-26.237, response.features[0].geometry.coordinates[1], 0.001)
    }
}
