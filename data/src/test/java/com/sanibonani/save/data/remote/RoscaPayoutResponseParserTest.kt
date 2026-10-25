package com.sanibonani.save.data.remote

import com.sanibonani.save.data.remote.supabase.SupabasePayoutDataSourceImpl
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * process_rosca_payout_v3 answers with the id of an already-executed rotation when the period was
 * claimed before. That id names money requested by an earlier run, not by this one: returning it
 * as if this call created the payout would re-announce the rotation and write a second initiation
 * line for the same period.
 */
class RoscaPayoutResponseParserTest {

    @Test
    fun `a fresh rotation reports the payout it created`() {
        val response = buildJsonObject {
            put("success", true)
            put("payout_id", "p-new")
            put("duplicate", false)
        }

        assertEquals("p-new", SupabasePayoutDataSourceImpl.parseRoscaPayoutId(response))
    }

    @Test
    fun `an already-executed period does not report a new payout`() {
        val response = buildJsonObject {
            put("success", true)
            put("payout_id", "p-existing")
            put("duplicate", true)
        }

        assertNull(SupabasePayoutDataSourceImpl.parseRoscaPayoutId(response))
    }

    @Test
    fun `a rotation whose offsets absorb the pot reports no payout`() {
        val response = buildJsonObject {
            put("success", true)
            put("payout_id", JsonNull)
            put("duplicate", false)
        }

        assertNull(SupabasePayoutDataSourceImpl.parseRoscaPayoutId(response))
    }

    @Test
    fun `a postgrest array wrapper is read as the single result row`() {
        val response = buildJsonArray {
            add(JsonObject(mapOf(
                "payout_id" to kotlinx.serialization.json.JsonPrimitive("p-array"),
                "duplicate" to kotlinx.serialization.json.JsonPrimitive(false)
            )))
        }

        assertEquals("p-array", SupabasePayoutDataSourceImpl.parseRoscaPayoutId(response))
    }

    @Test
    fun `a blank payout id is not a payout`() {
        val response = buildJsonObject {
            put("payout_id", "   ")
            put("duplicate", false)
        }

        assertNull(SupabasePayoutDataSourceImpl.parseRoscaPayoutId(response))
    }

    @Test
    fun `a legacy response without the duplicate flag still reports its payout`() {
        val response = buildJsonObject {
            put("payout_id", "p-legacy")
        }

        assertEquals("p-legacy", SupabasePayoutDataSourceImpl.parseRoscaPayoutId(response))
    }
}
