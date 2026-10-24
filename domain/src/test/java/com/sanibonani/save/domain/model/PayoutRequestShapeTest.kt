package com.sanibonani.save.domain.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * public.payouts stores the discriminator in a column named payout_type, and the tier writes
 * values outside the enum's original surface. Both mismatches are silent: an unknown JSON key
 * is dropped by ignoreUnknownKeys and an unknown enum value is coerced to the property default,
 * so a payout can decode as STANDARD without anything raising an error.
 */
class PayoutRequestShapeTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        decodeEnumsCaseInsensitive = true
        encodeDefaults = true
    }

    private fun row(payoutType: String) = """
        {"id":"0a9efb4c-1f4d-4e0a-9b1f-7d0c8f3e5a11","group_id":"3f2d1e55-6d4c-4f4f-9b4a-2c2f1e0a7b88",
         "member_id":null,"amount":"150.00","bank_name":"Capitec","account_no":"1234567890",
         "branch_code":"210115","status":"pending","payout_type":"$payoutType"}
    """.trimIndent()

    private fun request(type: PayoutType) = PayoutRequest(
        groupId = "3f2d1e55-6d4c-4f4f-9b4a-2c2f1e0a7b88",
        bankName = "Capitec",
        accountNo = "1234567890",
        branchCode = "210115",
        type = type,
    )

    // 'rosca' is inserted by process_rosca_payout_v2/v3; 'custom' is the table's own DEFAULT.
    private val literalsFromTier = mapOf(
        "standard"      to PayoutType.STANDARD,
        "annual"        to PayoutType.ANNUAL,
        "emergency"     to PayoutType.EMERGENCY,
        "disinvestment" to PayoutType.DISINVESTMENT,
        "claim"         to PayoutType.CLAIM,
        "rotation"      to PayoutType.ROTATION,
        "rosca"         to PayoutType.ROSCA,
        "custom"        to PayoutType.CUSTOM,
    )

    @Test
    fun `writes the discriminator under the column name the table actually has`() {
        val encoded = json.encodeToJsonElement(PayoutRequest.serializer(), request(PayoutType.CLAIM)).jsonObject

        assertTrue("payload must carry payout_type, that is the payouts column",
            encoded.containsKey("payout_type"))
        assertFalse("a 'type' key is not a column, PostgREST rejects it on insert",
            encoded.containsKey("type"))
        assertEquals("claim", encoded.getValue("payout_type").jsonPrimitive.content)
    }

    @Test
    fun `every payout_type the tier can return decodes to its own variant`() {
        val misclassified = literalsFromTier
            .filter { (literal, expected) ->
                json.decodeFromString(PayoutRequest.serializer(), row(literal)).type != expected
            }
            .map { (literal, expected) -> "$literal -> $expected" }

        assertTrue("payouts mislabelled by the remote model: $misclassified", misclassified.isEmpty())
    }

    @Test
    fun `the model round-trips every variant without losing the type`() {
        PayoutType.entries.forEach { type ->
            val literal = json.encodeToJsonElement(PayoutRequest.serializer(), request(type))
                .jsonObject.getValue("payout_type").jsonPrimitive.content

            assertEquals("type $type must survive encode/decode",
                type, json.decodeFromString(PayoutRequest.serializer(), row(literal)).type)
        }
    }

    @Test
    fun `an unrecognised payout type falls back to standard`() {
        // Pinning the consequence of coerceInputValues: a value neither the schema default nor
        // any RPC produces decodes as STANDARD rather than failing.
        assertEquals(PayoutType.STANDARD,
            json.decodeFromString(PayoutRequest.serializer(), row("something_new")).type)
    }
}
