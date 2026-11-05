package com.sanibonani.save.data.repository.admin

import com.sanibonani.save.data.local.GroupDao
import com.sanibonani.save.data.local.SanibonaniDatabase
import com.sanibonani.save.data.local.toEntity
import com.sanibonani.save.domain.model.Group
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * The group roster is read once and then used as though the server had answered: three platform-wide
 * money sweeps and the admin dashboard each call `getAllGroups().getOrThrow()` and treat any success
 * as the complete list of groups. The old block could not fail — it closed with a Room fallback and a
 * `getOrElse { emptyList() }` — so a refused read reached those callers as a platform with no groups,
 * which they reported as a completed sweep.
 *
 * The PostgREST request builders are inline reified functions, so the server is stubbed where the table
 * handle is resolved: that is enough to observe a refusal.
 */
class PlatformGroupRosterReadTest {

    private val protocol = mockk<DataConnectionProtocol>()
    private val supabase = mockk<SupabaseClient>()
    private val postgrest = mockk<Postgrest>(relaxed = true)
    private val notifRepo = mockk<NotificationRepository>(relaxed = true)
    private val groupDao = mockk<GroupDao>(relaxed = true)

    private lateinit var repository: PlatformRepositoryImpl

    @Before
    fun setUp() {
        mockkStatic("io.github.jan.supabase.postgrest.PostgrestKt")
        every { supabase.postgrest } returns postgrest
        every { protocol.getRemoteClient(SupabaseClient::class.java) } returns supabase
        val db = mockk<SanibonaniDatabase>(relaxed = true)
        every { db.groupDao() } returns groupDao
        every { groupDao.observeAllGroups() } returns flowOf(emptyList())
        coEvery { notifRepo.sendNotification(any()) } returns Result.success(Unit)

        repository = PlatformRepositoryImpl(
            protocol = protocol,
            db = db,
            actuarialRepo = mockk<ActuarialRepository>(relaxed = true),
            notifRepo = notifRepo
        )
    }

    @After
    fun tearDown() {
        unmockkStatic("io.github.jan.supabase.postgrest.PostgrestKt")
    }

    private fun refuseGroupsTable() {
        every { postgrest["groups"] } throws IOException("HTTP 503 Service Unavailable")
    }

    @Test
    fun `a roster the server refused is a failure, not a platform with no groups`() = runBlocking {
        refuseGroupsTable()

        val outcome = repository.getAllGroups()

        assertTrue("callers must be able to tell an outage from an empty platform", outcome.isFailure)
    }

    // The cache is where the old block hid the refusal: it answered with whatever the device last
    // held, and every caller reads a success as the whole platform. A sweep run on that list silently
    // skips the groups that joined since the device was last online.
    @Test
    fun `cached rows do not certify a roster the server refused`() = runBlocking {
        refuseGroupsTable()
        every { groupDao.observeAllGroups() } returns
            flowOf(listOf(Group(id = "group-cached", name = "Cached").toEntity()))

        val outcome = repository.getAllGroups()

        assertTrue("a stale roster must not be handed to the sweeps as the complete platform", outcome.isFailure)
    }

    @Test
    fun `a broadcast with an unreadable roster notifies nobody and says so`() = runBlocking {
        refuseGroupsTable()

        val outcome = repository.broadcastPlatformMessage("Fees change on the 1st.")

        assertTrue("the admin must not be told members were reached", outcome.isFailure)
        coVerify(exactly = 0) { notifRepo.sendNotification(any()) }
    }
}
