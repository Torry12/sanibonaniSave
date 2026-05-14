package com.sanibonani.save.whatsapp

import android.util.Log
import com.sanibonani.save.data.local.SanibonaniDatabase
import com.sanibonani.save.data.remote.EdgeFunctionGateway
import com.sanibonani.save.data.repository.NotificationRepositoryImpl
import com.sanibonani.save.domain.model.*
import io.github.jan.supabase.SupabaseClient
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for WhatsApp notification delivery through [NotificationRepositoryImpl].
 *
 * Strategy: mock [EdgeFunctionGateway] to capture the JSON payload forwarded to the
 * `send-whatsapp` Supabase Edge Function and assert its structure / content.
 *
 * These tests cover the three code paths that call sendWhatsAppViaEdge:
 *   1. sendPasswordResetWhatsApp            — direct, no Supabase DB reads
 *   2. sendFeeEnforcementNotification       — reads group + member from DB
 *   3. sendNotification with WHATSAPP/BOTH  — reads member phone from DB
 *
 * Paths (2) and (3) require Supabase PostgREST calls which cannot be trivially mocked
 * at the unit-test level without an integration harness. Those paths are covered by
 * integration tests in [NotificationRepositoryIntegrationTest] (androidTest). Here we
 * test only path (1) plus edge-cases that do not require DB interaction.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WhatsAppNotificationTest {

    private val testDispatcher = StandardTestDispatcher()

    private val edgeFunctionGateway = mockk<EdgeFunctionGateway>()
    private val supabase            = mockk<SupabaseClient>(relaxed = true)
    private val db                  = mockk<SanibonaniDatabase>(relaxed = true)

    private lateinit var repo: NotificationRepositoryImpl

    /** Captures the payload forwarded to the `send-whatsapp` Edge Function. */
    private val payloadSlot = slot<JsonObject>()

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) }                           returns 0
        every { Log.i(any<String>(), any<String>()) }                           returns 0
        every { Log.w(any<String>(), any<String>()) }                           returns 0
        every { Log.w(any<String>(), any<String>(), any<Throwable>()) }         returns 0
        every { Log.e(any<String>(), any<String>()) }                           returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) }         returns 0

        Dispatchers.setMain(testDispatcher)

        // Default: edge function succeeds
        coEvery {
            edgeFunctionGateway.invoke("send-whatsapp", capture(payloadSlot), any())
        } returns Result.success(buildJsonObject { put("success", true) })

        repo = NotificationRepositoryImpl(supabase, edgeFunctionGateway, db)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    // ══════════════════════════════════════════════════════════════════════
    // 1. sendPasswordResetWhatsApp — function name & template verification
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun `sendPasswordResetWhatsApp calls send-whatsapp edge function exactly once`() = runTest {
        repo.sendPasswordResetWhatsApp("0821234567")
        advanceUntilIdle()

        coVerify(exactly = 1) {
            edgeFunctionGateway.invoke("send-whatsapp", any(), any())
        }
    }

    @Test
    fun `sendPasswordResetWhatsApp forwards phone number in payload`() = runTest {
        val phone = "0821234567"

        repo.sendPasswordResetWhatsApp(phone)
        advanceUntilIdle()

        val sentPhone = (payloadSlot.captured["to"] as? JsonPrimitive)?.content
        assertEquals(
            "Phone must be forwarded as-is; E.164 normalisation is done in the Edge Function",
            phone,
            sentPhone
        )
    }

    @Test
    fun `sendPasswordResetWhatsApp uses password_reset template name`() = runTest {
        repo.sendPasswordResetWhatsApp("0721112233")
        advanceUntilIdle()

        val templateObj = payloadSlot.captured["template"] as? JsonObject
        assertNotNull("Template object must be present in payload", templateObj)

        val templateName = (templateObj!!["name"] as? JsonPrimitive)?.content
        assertEquals("password_reset", templateName)
    }

    @Test
    fun `sendPasswordResetWhatsApp uses en_US language code`() = runTest {
        repo.sendPasswordResetWhatsApp("0721112233")
        advanceUntilIdle()

        val templateObj = payloadSlot.captured["template"] as? JsonObject
        val langObj     = templateObj?.get("language") as? JsonObject
        val langCode    = (langObj?.get("code") as? JsonPrimitive)?.content

        assertEquals("en_US", langCode)
    }

    @Test
    fun `sendPasswordResetWhatsApp returns success when edge function succeeds`() = runTest {
        val result = repo.sendPasswordResetWhatsApp("0821234567")
        advanceUntilIdle()

        assertTrue("Result must be success", result.isSuccess)
    }

    @Test
    fun `sendPasswordResetWhatsApp returns failure when edge function throws`() = runTest {
        coEvery {
            edgeFunctionGateway.invoke("send-whatsapp", any(), any())
        } returns Result.failure(RuntimeException("Network timeout"))

        val result = repo.sendPasswordResetWhatsApp("0821234567")
        advanceUntilIdle()

        assertTrue("Should propagate gateway failure", result.isFailure)
        assertEquals("Network timeout", result.exceptionOrNull()?.message)
    }

    @Test
    fun `sendDirectWhatsAppMessage uses general notification template payload`() = runTest {
        repo.sendDirectWhatsAppMessage("0713459563", "Edge smoke test")
        advanceUntilIdle()

        val sentPhone = (payloadSlot.captured["to"] as? JsonPrimitive)?.content
        val sentMessage = (payloadSlot.captured["message"] as? JsonPrimitive)?.content
        val templateObj = payloadSlot.captured["template"] as? JsonObject
        val templateName = (templateObj?.get("name") as? JsonPrimitive)?.content

        assertEquals("0713459563", sentPhone)
        assertEquals("Edge smoke test", sentMessage)
        assertEquals("general_notification", templateName)
    }

    // ══════════════════════════════════════════════════════════════════════
    // 2. Phone number normalisation expectations
    //    (The Edge Function handles 0→27 conversion; Kotlin forwards raw phone)
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun `local 0-prefix phone forwarded unchanged — Edge Function will normalise to 27`() = runTest {
        val localPhone = "0821234567"

        repo.sendPasswordResetWhatsApp(localPhone)
        advanceUntilIdle()

        val sentPhone = (payloadSlot.captured["to"] as? JsonPrimitive)?.content
        assertEquals(
            "Kotlin layer forwards phone as-is; Edge Function converts 0→27",
            localPhone, sentPhone
        )
    }

    @Test
    fun `E164 phone starting with 27 is forwarded unchanged`() = runTest {
        val e164Phone = "27821234567"

        repo.sendPasswordResetWhatsApp(e164Phone)
        advanceUntilIdle()

        val sentPhone = (payloadSlot.captured["to"] as? JsonPrimitive)?.content
        assertEquals(e164Phone, sentPhone)
    }

    // ══════════════════════════════════════════════════════════════════════
    // 3. sendFeeEnforcementNotification — edge function for PLATFORM_FEE_DUE
    //    NOTE: full integration with DB reads is tested in androidTest suite.
    //    Here we only verify the function is NOT called when no admin exists.
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun `sendFeeEnforcementNotification with PLATFORM_FEE_WARNING does not send template`() = runTest {
        // No DB stubs → supabase is fully relaxed → decodeSingleOrNull returns null
        // For non-PLATFORM_FEE_DUE events, the dedicated template path is skipped
        repo.sendFeeEnforcementNotification("g1", NotifEvent.PLATFORM_FEE_WARNING, 5, 500.0)
        advanceUntilIdle()

        // In this relaxed-mock setup, no WhatsApp edge call is expected for WARNING.
        coVerify(exactly = 0) {
            edgeFunctionGateway.invoke("send-whatsapp", any(), any())
        }
    }
}
