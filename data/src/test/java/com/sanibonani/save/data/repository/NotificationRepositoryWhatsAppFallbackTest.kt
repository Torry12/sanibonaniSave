package com.sanibonani.save.data.repository

import com.sanibonani.save.data.remote.EdgeFunctionGateway
import com.sanibonani.save.data.repository.system.NotificationRouter
import com.sanibonani.save.data.repository.system.WhatsAppChannel
import com.sanibonani.save.domain.model.NotifEvent
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Tests for WhatsApp delivery fallback logic and notification routing.
 *
 * These features were originally in NotificationRepositoryImpl but have been refactored
 * into [WhatsAppChannel] and [NotificationRouter] respectively.
 */
class NotificationRepositoryWhatsAppFallbackTest {

    private val edgeFunctionGateway = mockk<EdgeFunctionGateway>()
    private val whatsAppChannel = WhatsAppChannel(edgeFunctionGateway)
    private val router = NotificationRouter()

    @Test
    fun `WhatsAppChannel retries as plain text when template delivery fails`() = runTest {
        val payloads = mutableListOf<JsonObject>()
        coEvery {
            edgeFunctionGateway.invoke("send-whatsapp", any(), any())
        } coAnswers {
            val payload = secondArg<JsonObject>()
            payloads += payload
            if (payload.containsKey("template")) {
                // Simulate template rejection error
                Result.failure(IllegalStateException("Template parameter rejected"))
            } else {
                Result.success(buildJsonObject { put("success", true) })
            }
        }

        whatsAppChannel.deliver("0821234567", "Hello team")

        // Should call twice: once with template, then fallback with plain text
        assertEquals(2, payloads.size)
        assertNotNull(payloads.first()["template"])
        assertEquals("0821234567", (payloads.last()["to"] as JsonPrimitive).content)
        assertEquals("Hello team", (payloads.last()["message"] as JsonPrimitive).content)
        assertEquals(false, payloads.last().containsKey("template"))

        coVerify(exactly = 2) { edgeFunctionGateway.invoke("send-whatsapp", any(), any()) }
    }

    @Test
    fun `WhatsAppChannel propagates non template failures without fallback`() = runTest {
        coEvery {
            edgeFunctionGateway.invoke("send-whatsapp", any(), any())
        } returns Result.failure(IllegalStateException("Unauthorized"))

        val result = whatsAppChannel.deliver("0821234567", "Hello team")

        assertEquals(true, result.isFailure)
        // Should only call once because it's not a template delivery failure
        coVerify(exactly = 1) { edgeFunctionGateway.invoke("send-whatsapp", any(), any()) }
    }

    @Test
    fun `NotificationRouter shouldRouteToAdmin includes payout requested`() {
        assertEquals(true, router.shouldRouteToAdmin(NotifEvent.PAYOUT_REQUESTED))
        assertEquals(true, router.shouldRouteToAdmin(NotifEvent.MEMBER_MESSAGE))
        assertEquals(true, router.shouldRouteToAdmin(NotifEvent.LOAN_REQUESTED))
    }

    @Test
    fun `NotificationRouter shouldRouteToAdmin excludes unrelated events`() {
        assertEquals(false, router.shouldRouteToAdmin(NotifEvent.PAYMENT_CONFIRMED))
        assertEquals(false, router.shouldRouteToAdmin(NotifEvent.CUSTOM))
    }
}
