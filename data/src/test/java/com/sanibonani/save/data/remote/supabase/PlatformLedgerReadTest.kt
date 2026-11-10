package com.sanibonani.save.data.remote.supabase

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

/**
 * The platform ledger is the admin's view of the money the platform itself took, so an outage and a
 * genuinely empty ledger have to stay distinguishable. This is the only route that table reaches the
 * screen through (`GetPlatformLedgerUseCase` -> `PlatformLedgerViewModel`); a second copy of the same
 * query on `PlatformRepository` used to answer it and was deleted, so the guard moved here.
 */
class PlatformLedgerReadTest {

    private val supabase = mockk<SupabaseClient>()
    private val postgrest = mockk<Postgrest>(relaxed = true)

    private lateinit var source: SupabaseLedgerDataSourceImpl

    @Before
    fun setUp() {
        mockkStatic("io.github.jan.supabase.postgrest.PostgrestKt")
        every { supabase.postgrest } returns postgrest
        source = SupabaseLedgerDataSourceImpl(supabase)
    }

    @After
    fun tearDown() {
        unmockkStatic("io.github.jan.supabase.postgrest.PostgrestKt")
    }

    @Test
    fun `an unreachable platform ledger remains a failure instead of an empty ledger`() = runBlocking {
        every { postgrest["platform_ledger"] } throws IllegalStateException("Platform ledger unavailable")

        val outcome = source.getPlatformLedger()

        assertTrue("a ledger outage must not look like a genuinely empty ledger", outcome.isFailure)
        verify(exactly = 1) { postgrest["platform_ledger"] }
    }
}
