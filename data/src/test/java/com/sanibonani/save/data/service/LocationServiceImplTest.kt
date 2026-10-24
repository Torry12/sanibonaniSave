package com.sanibonani.save.data.service

import com.sanibonani.save.data.remote.*
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LocationServiceImplTest {

    private val geoapifyService = mockk<GeoapifyService>()
    private lateinit var locationService: LocationServiceImpl
    private val apiKey = "test_key"

    @Before
    fun setup() {
        locationService = LocationServiceImpl(geoapifyService, apiKey)
    }

    @Test
    fun `searchAddress maps features correctly`() = runTest {
        val mockResponse = GeoapifyResponse(
            features = listOf(
                Feature(
                    properties = Properties(formatted = "Soweto, ZA", city = "Johannesburg", state = "Gauteng"),
                    geometry = Geometry(coordinates = listOf(27.0, -26.0))
                )
            )
        )
        coEvery { geoapifyService.autocomplete(any(), any()) } returns mockResponse

        val result = locationService.searchAddress("Soweto")

        assertTrue(result.isSuccess)
        val addresses = result.getOrThrow()
        assertEquals(1, addresses.size)
        assertEquals("Johannesburg", addresses[0].city)
        assertEquals("Gauteng", addresses[0].province)
        assertEquals(-26.0, addresses[0].latitude!!, 0.0)
    }

    @Test
    fun `geocodeAddress returns coordinates correctly`() = runTest {
        val mockResponse = GeoapifyResponse(
            features = listOf(
                Feature(
                    properties = Properties(formatted = "Pretoria, ZA", city = "Pretoria", state = "Gauteng"),
                    geometry = Geometry(coordinates = listOf(28.0, -25.0))
                )
            )
        )
        coEvery { geoapifyService.autocomplete(any(), any()) } returns mockResponse

        val result = locationService.geocodeAddress("Pretoria", "Gauteng")

        assertTrue(result.isSuccess)
        val coords = result.getOrThrow()
        assertEquals(-25.0, coords.latitude, 0.0)
        assertEquals(28.0, coords.longitude, 0.0)
        assertTrue(coords.geohash.isNotEmpty())
    }
}
