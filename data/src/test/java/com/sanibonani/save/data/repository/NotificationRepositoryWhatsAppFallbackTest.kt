package com.sanibonani.save.data.repository

import com.sanibonani.save.data.local.SanibonaniDatabase
import com.sanibonani.save.data.remote.EdgeFunctionGateway
import io.github.jan.supabase.SupabaseClient
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

class NotificationRepositoryWhatsAppFallbackTest {

    private val supabase = mockk<SupabaseClient>(relaxed = true)
    private val edgeFunctionGateway = mockk<EdgeFunctionGateway>()
    private val db = mockk<SanibonaniDatabase>(relaxed = true)

    @Test
    fun `sendWhatsAppViaEdge retries as plain text when template delivery fails`() = runTest {
        val payloads = mutableListOf<JsonObject>()
        coEvery {
            edgeFunctionGateway.invoke("send-whatsapp", any(), any())
        } coAnswers {
            val payload = secondArg<JsonObject>()
            payloads += payload
            if (payload.containsKey("template")) {
                Result.failure(IllegalStateException("Template parameter rejected"))
            } else {
                Result.success(buildJsonObject { put("success", true) })
            }
        }

        val repo = NotificationRepositoryImpl(supabase, edgeFunctionGateway, db)
        repo.sendWhatsAppViaEdge("0821234567", "Hello team")

        assertEquals(2, payloads.size)
        assertNotNull(payloads.first()["template"])
        assertEquals("0821234567", (payloads.last()["to"] as JsonPrimitive).content)
        assertEquals("Hello team", (payloads.last()["message"] as JsonPrimitive).content)
        assertEquals(false, payloads.last().containsKey("template"))

        coVerify(exactly = 2) { edgeFunctionGateway.invoke("send-whatsapp", any(), any()) }
    }

    @Test
    fun `sendWhatsAppViaEdge propagates non template failures without fallback`() = runTest {
        coEvery {
            edgeFunctionGateway.invoke("send-whatsapp", any(), any())
        } returns Result.failure(IllegalStateException("Unauthorized"))

        val repo = NotificationRepositoryImpl(supabase, edgeFunctionGateway, db)
        val result = runCatching { repo.sendWhatsAppViaEdge("0821234567", "Hello team") }

        assertEquals(true, result.isFailure)
        coVerify(exactly = 1) { edgeFunctionGateway.invoke("send-whatsapp", any(), any()) }
    }

    @Test
    fun `routesToGroupAdmin includes payout requested`() {
        val repo = NotificationRepositoryImpl(supabase, edgeFunctionGateway, db)

        assertEquals(true, repo.routesToGroupAdmin(com.sanibonani.save.domain.model.NotifEvent.PAYOUT_REQUESTED))
        assertEquals(true, repo.routesToGroupAdmin(com.sanibonani.save.domain.model.NotifEvent.MEMBER_MESSAGE))
        assertEquals(true, repo.routesToGroupAdmin(com.sanibonani.save.domain.model.NotifEvent.LOAN_REQUESTED))
    }

    @Test
    fun `routesToGroupAdmin excludes unrelated events`() {
        val repo = NotificationRepositoryImpl(supabase, edgeFunctionGateway, db)

        assertEquals(false, repo.routesToGroupAdmin(com.sanibonani.save.domain.model.NotifEvent.PAYMENT_CONFIRMED))
        assertEquals(false, repo.routesToGroupAdmin(com.sanibonani.save.domain.model.NotifEvent.CUSTOM))
    }
}

