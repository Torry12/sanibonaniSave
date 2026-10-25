package com.sanibonani.save.data.repository.admin

import com.sanibonani.save.data.datasource.RemoteAuditDataSource
import com.sanibonani.save.data.local.SanibonaniDatabase
import com.sanibonani.save.domain.model.Money
import com.sanibonani.save.domain.repository.admin.ActuarialRepository
import com.sanibonani.save.domain.repository.system.DataConnectionProtocol
import com.sanibonani.save.domain.repository.system.NotificationRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * Lifting a suspension is a revenue event: the platform fee has to be recorded before the group is
 * freed, and a fee the server refused must not leave the group active. `payments` carries no unique
 * key, so the client is the only thing standing between a retried write and a group charged twice.
 *
 * The PostgREST request builders are inline reified functions, so the server is stubbed where the
 * table handle is resolved: that is enough to observe which tables an action touches, and in what order.
 */
class PlatformSuspensionFeeTest {

    private val protocol = mockk<DataConnectionProtocol>()
    private val supabase = mockk<SupabaseClient>()
    private val postgrest = mockk<Postgrest>(relaxed = true)
    private val notifRepo = mockk<NotificationRepository>()

    private lateinit var repository: PlatformRepositoryImpl

    @Before
    fun setUp() {
        mockkStatic("io.github.jan.supabase.postgrest.PostgrestKt")
        every { supabase.postgrest } returns postgrest
        every { protocol.getRemoteClient(SupabaseClient::class.java) } returns supabase
        coEvery { notifRepo.sendNotification(any()) } returns Result.success(Unit)

        repository = PlatformRepositoryImpl(
            protocol = protocol,
            db = mockk<SanibonaniDatabase>(relaxed = true),
            actuarialRepo = mockk<ActuarialRepository>(relaxed = true),
            notifRepo = notifRepo,
            auditRemoteSource = mockk<RemoteAuditDataSource>(relaxed = true)
        )
    }

    @After
    fun tearDown() {
        unmockkStatic("io.github.jan.supabase.postgrest.PostgrestKt")
    }

    @Test
    fun `a refused lifting fee leaves the group suspended and notifies nobody`() = runBlocking {
        every { postgrest["payments"] } throws IOException("HTTP 500 Internal Server Error")

        val outcome = repository.unsuspendGroup("group-1")

        assertTrue("the admin must be told the fee was not recorded", outcome.isFailure)
        verify(exactly = 0) { postgrest["groups"] }
        coVerify(exactly = 0) { notifRepo.sendNotification(any()) }
    }

    @Test
    fun `suspending a group never posts a platform fee`() = runBlocking {
        every { postgrest["groups"] } throws IOException("HTTP 500 Internal Server Error")

        val outcome = repository.suspendGroup("group-1", "unpaid subscription")

        assertTrue(outcome.isFailure)
        verify(exactly = 0) { postgrest["payments"] }
        coVerify(exactly = 0) { notifRepo.sendNotification(any()) }
    }

    @Test
    fun `the lifting fee is described entirely by its idempotency key`() {
        val fee = Money("250.00")
        val payload = PlatformRepositoryImpl.liftingFeePayload("group-1", fee, "unsuspend_group_group-1_1700000000000")

        assertEquals("250.00", payload["amount"]?.jsonPrimitive?.content)
        assertEquals("group-1", payload["group_id"]?.jsonPrimitive?.content)
        assertEquals("platform_fee", payload["payment_type"]?.jsonPrimitive?.content)
        assertEquals("completed", payload["status"]?.jsonPrimitive?.content)
        assertEquals(
            "the same key must describe the same fee, so a replay cannot invent a second one",
            payload,
            PlatformRepositoryImpl.liftingFeePayload("group-1", fee, "unsuspend_group_group-1_1700000000000")
        )
    }

    @Test
    fun `the lifting fee key names the group it was raised for`() {
        assertTrue(
            PlatformRepositoryImpl.buildLiftingFeeTransactionId("group-9")
                .startsWith("unsuspend_group_group-9_")
        )
    }
}
