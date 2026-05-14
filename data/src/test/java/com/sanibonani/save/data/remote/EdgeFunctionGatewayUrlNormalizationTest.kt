package com.sanibonani.save.data.remote

import org.junit.Assert.assertEquals
import org.junit.Test

class EdgeFunctionGatewayUrlNormalizationTest {

    @Test
    fun `normalizeSupabaseUrl keeps valid https URL unchanged`() {
        val normalized = EdgeFunctionGateway.normalizeSupabaseUrl("https://aszmodkqpafwtnfufmow.supabase.co")
        assertEquals("https://aszmodkqpafwtnfufmow.supabase.co", normalized)
    }

    @Test
    fun `normalizeSupabaseUrl adds https when host has no scheme`() {
        val normalized = EdgeFunctionGateway.normalizeSupabaseUrl("aszmodkqpafwtnfufmow.supabase.co")
        assertEquals("https://aszmodkqpafwtnfufmow.supabase.co", normalized)
    }

    @Test
    fun `normalizeSupabaseUrl expands project ref to full supabase domain`() {
        val normalized = EdgeFunctionGateway.normalizeSupabaseUrl("aszmodkqpafwtnfufmow")
        assertEquals("https://aszmodkqpafwtnfufmow.supabase.co", normalized)
    }
}

