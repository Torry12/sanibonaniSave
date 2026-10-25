package com.sanibonani.save.whatsapp

import android.util.Log
import com.sanibonani.save.data.local.SanibonaniDatabase
import com.sanibonani.save.data.remote.EdgeFunctionGateway
import com.sanibonani.save.data.repository.system.*
import com.sanibonani.save.data.repository.mappers.DataPayloadFactory
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

@OptIn(ExperimentalCoroutinesApi::class)
class WhatsAppNotificationTest {

    private val testDispatcher = StandardTestDispatcher()

    private val edgeFunctionGateway = mockk<EdgeFunctionGateway>()
    private val supabase            = mockk<SupabaseClient>(relaxed = true)
    private val db                  = mockk<SanibonaniDatabase>(relaxed = true)
    private val router              = mockk<NotificationRouter>(relaxed = true)
    private val taskQueue           = mockk<NotificationTaskQueue>(relaxed = true)
    private val payloadFactory       = mockk<DataPayloadFactory>(relaxed = true)

    private lateinit var whatsAppChannel: WhatsAppChannel
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

        whatsAppChannel = WhatsAppChannel(edgeFunctionGateway)

        repo = NotificationRepositoryImpl(
            supabase,
            db,
            whatsAppChannel,
            router,
            taskQueue,
            edgeFunctionGateway,
            payloadFactory
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

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
        assertEquals(phone, sentPhone)
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
    fun `sendPasswordResetWhatsApp returns success when edge function succeeds`() = runTest {
        val result = repo.sendPasswordResetWhatsApp("0821234567")
        advanceUntilIdle()

        assertTrue("Result must be success", result.isSuccess)
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
}
