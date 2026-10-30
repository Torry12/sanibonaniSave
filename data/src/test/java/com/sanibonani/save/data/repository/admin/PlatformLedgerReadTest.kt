package com.sanibonani.save.data.repository.admin

import com.sanibonani.save.data.local.SanibonaniDatabase
import com.sanibonani.save.domain.repository.admin.ActuarialRepository
import com.sanibonani.save.domain.repository.system.DataConnectionProtocol
import com.sanibonani.save.domain.repository.system.NotificationRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
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

class PlatformLedgerReadTest {

    private val protocol = mockk<DataConnectionProtocol>()
    private val supabase = mockk<SupabaseClient>()
    private val postgrest = mockk<Postgrest>(relaxed = true)

    private lateinit var repository: PlatformRepositoryImpl

    @Before
    fun setUp() {
        mockkStatic("io.github.jan.supabase.postgrest.PostgrestKt")
        every { supabase.postgrest } returns postgrest
        every { protocol.getRemoteClient(SupabaseClient::class.java) } returns supabase

        repository = PlatformRepositoryImpl(
            protocol = protocol,
            db = mockk<SanibonaniDatabase>(relaxed = true),
            actuarialRepo = mockk<ActuarialRepository>(relaxed = true),
            notifRepo = mockk<NotificationRepository>(relaxed = true)
        )
    }

    @After
    fun tearDown() {
        unmockkStatic("io.github.jan.supabase.postgrest.PostgrestKt")
    }

    @Test
    fun `an unreachable platform ledger remains a failure instead of an empty ledger`() = runBlocking {
        every { postgrest["platform_ledger"] } throws IllegalStateException("Platform ledger unavailable")

        val outcome = repository.getPlatformLedger()

        assertTrue("a ledger outage must not look like a genuinely empty ledger", outcome.isFailure)
        verify(exactly = 1) { postgrest["platform_ledger"] }
    }
}
