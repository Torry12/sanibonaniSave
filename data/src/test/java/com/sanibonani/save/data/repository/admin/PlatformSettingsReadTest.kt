package com.sanibonani.save.data.repository.admin

import com.sanibonani.save.data.local.SanibonaniDatabase
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
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * `platform_settings` is the only place the published fees live, so a read that never reached the
 * server must not be reported as a set of fees. If it is, an admin who lowered a fee still gets the
 * app's fallback amount charged, and the platform books revenue the group never agreed to.
 *
 * The PostgREST request builders are inline reified functions, so the server is stubbed where the
 * table handle is resolved: that is enough to observe which tables an action touches.
 */
class PlatformSettingsReadTest {

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
            notifRepo = notifRepo
        )
    }

    @After
    fun tearDown() {
        unmockkStatic("io.github.jan.supabase.postgrest.PostgrestKt")
    }

    @Test
    fun `an unreachable settings table is a failure, not a set of invented fees`() = runBlocking {
        every { postgrest["platform_settings"] } throws IOException("HTTP 503 Service Unavailable")

        val outcome = repository.getPlatformSettings()

        assertTrue("callers must be able to tell an outage from an empty table", outcome.isFailure)
    }

    @Test
    fun `lifting a suspension with unreadable settings posts no fee and frees no group`() = runBlocking {
        every { postgrest["platform_settings"] } throws IOException("HTTP 503 Service Unavailable")

        val outcome = repository.unsuspendGroup("group-1")

        assertTrue("the admin must be told the fee could not be priced", outcome.isFailure)
        verify(exactly = 0) { postgrest["payments"] }
        verify(exactly = 0) { postgrest["groups"] }
        coVerify(exactly = 0) { notifRepo.sendNotification(any()) }
    }
}
