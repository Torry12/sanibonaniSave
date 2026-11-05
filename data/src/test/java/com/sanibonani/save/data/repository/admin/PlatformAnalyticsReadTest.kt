package com.sanibonani.save.data.repository.admin

import com.sanibonani.save.data.local.GroupDao
import com.sanibonani.save.data.local.SanibonaniDatabase
import com.sanibonani.save.data.local.toEntity
import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.Money
import com.sanibonani.save.domain.model.PlatformAnalyticsSummary
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * The landing hero and the admin dashboard each render an `AsyncResult.Error` for the platform
 * counters, and neither leg could ever be reached: `getPlatformAnalytics` swallowed a refused summary
 * view into counters derived from a roster that swallowed its own refusal into the Room cache and then
 * into an empty list, and priced platform fees in Kotlin (R10 a member a month plus an R150
 * registration at 80% capture) whenever the view reported no revenue. So an outage answered as a
 * platform with no groups holding R0.00 while the revenue chart displayed money the app had invented.
 *
 * `platform_summary_stats` aggregates `platform_ledger`, so the revenue it answers - including zero -
 * is the platform's real revenue and needs no estimate. The PostgREST request builders are inline
 * reified functions, so the server is stubbed where the table handle is resolved; the counter mapping
 * is asserted directly for the same reason.
 */
class PlatformAnalyticsReadTest {

    private val protocol = mockk<DataConnectionProtocol>()
    private val supabase = mockk<SupabaseClient>()
    private val postgrest = mockk<Postgrest>(relaxed = true)
    private val groupDao = mockk<GroupDao>(relaxed = true)

    private lateinit var repository: PlatformRepositoryImpl

    private fun summary(
        totalGroups: Int = 19,
        totalMembers: Int = 240,
        totalBalance: Money = Money("702500.00"),
        totalProvinces: Int = 5,
        platformRevenue: Money = Money("21100.00"),
        averageRiskScore: Double = 41.5
    ) = PlatformAnalyticsSummary(
        totalGroups = totalGroups,
        totalMembers = totalMembers,
        totalBalance = totalBalance,
        totalProvinces = totalProvinces,
        platformRevenue = platformRevenue,
        averageRiskScore = averageRiskScore
    )

    @Before
    fun setUp() {
        mockkStatic("io.github.jan.supabase.postgrest.PostgrestKt")
        every { supabase.postgrest } returns postgrest
        every { protocol.getRemoteClient(SupabaseClient::class.java) } returns supabase
        val db = mockk<SanibonaniDatabase>(relaxed = true)
        every { db.groupDao() } returns groupDao
        every { groupDao.observeAllGroups() } returns flowOf(emptyList())

        repository = PlatformRepositoryImpl(
            protocol = protocol,
            db = db,
            actuarialRepo = mockk<ActuarialRepository>(relaxed = true),
            notifRepo = mockk<NotificationRepository>(relaxed = true)
        )
    }

    @After
    fun tearDown() {
        unmockkStatic("io.github.jan.supabase.postgrest.PostgrestKt")
    }

    private fun refuseSummaryTable() {
        every { postgrest["platform_summary_stats"] } throws IOException("HTTP 503 Service Unavailable")
    }

    @Test
    fun `a refused summary read is a failure and not counters worked out elsewhere`() = runBlocking {
        refuseSummaryTable()

        val outcome = repository.getPlatformAnalytics()

        assertTrue("the screens must be able to name an unread platform", outcome.isFailure)
        // The roster cannot answer for the two counters only the view holds - platform revenue and the
        // average risk score - so a refused view is not papered over by reading the groups instead.
        verify(exactly = 0) { postgrest["groups"] }
    }

    @Test
    fun `cached groups do not answer the platform counters`() = runBlocking {
        refuseSummaryTable()
        every { groupDao.observeAllGroups() } returns flowOf(
            listOf(
                Group(id = "group-cached", name = "Cached", province = "Gauteng", currentMembers = 7).toEntity()
            )
        )

        val outcome = repository.getPlatformAnalytics()

        assertTrue("a cached roster must not stand in for a platform summary the server refused", outcome.isFailure)
        verify(exactly = 0) { groupDao.observeAllGroups() }
    }

    @Test
    fun `counters come from the summary view and the charts from the roster the server answered`() {
        val groups = listOf(
            Group(id = "g-1", name = "Alpha", province = "Gauteng"),
            Group(id = "g-2", name = "Beta", province = "Gauteng"),
            Group(id = "g-3", name = "Gamma", province = "KwaZulu-Natal")
        )

        val analytics = PlatformRepositoryImpl.platformAnalyticsOf(summary(), groups)

        assertEquals(19, analytics.totalGroups)
        assertEquals(240, analytics.totalMembers)
        assertEquals(5, analytics.totalProvinces)
        assertEquals(Money("702500.00"), analytics.totalBalance)
        assertEquals(41.5, analytics.averageRiskScore, 0.001)
        assertEquals(mapOf("Gauteng" to 2, "KwaZulu-Natal" to 1), analytics.provinceDistribution)
    }

    @Test
    fun `a platform that has earned nothing reports nothing rather than an estimate`() {
        // The old block priced this as R10 per member per month for six months plus an R150 group
        // registration fee at 80% capture - R26,490.00 for these counters, shown as revenue the
        // platform had collected.
        val analytics = PlatformRepositoryImpl.platformAnalyticsOf(summary(platformRevenue = Money.ZERO), emptyList())

        assertEquals(Money.ZERO, analytics.totalPlatformFees)
    }
}
