package com.sanibonani.save.data.repository.payment
import io.github.jan.supabase.SupabaseClient
import io.mockk.*
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SupabaseEdgeBankAdapterTest {

    private val supabaseClient = mockk<SupabaseClient>(relaxed = true)
    private val json = Json { ignoreUnknownKeys = true }

    private lateinit var adapter: SupabaseEdgeBankAdapter

    @Before
    fun setup() {
        adapter = SupabaseEdgeBankAdapter(supabaseClient, json)
    }

    @Test
    fun `adapter initializes correctly`() {
        assertNotNull(adapter)
    }
}
