package com.sanibonani.save.data.repository.admin

import com.sanibonani.save.data.local.SanibonaniDatabase
import com.sanibonani.save.domain.model.MemberBehaviorInsight
import com.sanibonani.save.domain.repository.admin.ActuarialRepository
import com.sanibonani.save.domain.repository.system.DataConnectionProtocol
import com.sanibonani.save.domain.repository.system.NotificationRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.result.PostgrestResult
import io.github.jan.supabase.serializer.KotlinXSerializer
import io.ktor.http.Headers
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The platform console's member search is the only way to find a person across groups, and it read
 * `platform_member_behavior_insights_v1` - a relation no schema in this repo defines. The server does
 * have the answer: `search_members_v1` joins the behaviour track and returns the insight the card
 * renders, so the read belongs on that function.
 *
 * Two things are pinned here: the request goes to that function carrying the term, and a failed read
 * stays a failure. The old `getOrElse { emptyList() }` made "the server is down" and "nobody is
 * called Thandi" the same screen.
 */
class PlatformMemberSearchTest {

    private val protocol = mockk<DataConnectionProtocol>()
    private val supabase = mockk<SupabaseClient>()
    private val postgrest = mockk<Postgrest>(relaxed = true)

    private lateinit var repository: PlatformRepositoryImpl

    @Before
    fun setUp() {
        mockkStatic("io.github.jan.supabase.postgrest.PostgrestKt")
        every { supabase.postgrest } returns postgrest
        every { postgrest.serializer } returns KotlinXSerializer(Json { ignoreUnknownKeys = true })
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

    /**
     * The rows `search_members_v1` returns once it also selects the behaviour score, which the track
     * carries and the function left out of its jsonb. The other aggregate keys stay in the payload -
     * the console's serializer ignores what the card does not show.
     */
    private fun insightsBody(vararg members: Pair<String, String>): PostgrestResult {
        val rows = members.joinToString(",") { (name, score) ->
            """{"member_id":"m-$name","group_id":"g1","member_name":"$name","risk_band":"high",""" +
                """"overdue_loans":2,"outstanding_amount":150.0,"completion_ratio":0.4,""" +
                """"total_loan_requests":5,"behavior_score":$score}"""
        }
        return PostgrestResult("[$rows]", Headers.Empty, postgrest)
    }

    @Test
    fun `the member search asks the server search function for the term`() = runBlocking {
        coEvery { postgrest.rpc(any<String>(), any<JsonObject>(), any()) } returns
            insightsBody("Thandi M" to "72.5", "Sipho K" to "91.0")

        val insights: List<MemberBehaviorInsight> = repository.searchMembers("thandi").getOrThrow()

        val sent = slot<JsonObject>()
        coVerify(exactly = 1) {
            postgrest.rpc("search_members_v1", capture(sent), any())
        }
        assertEquals("thandi", sent.captured["p_query"]?.jsonPrimitive?.content)
        assertEquals(listOf("Thandi M", "Sipho K"), insights.map { it.memberName })
        assertEquals(72.5, insights.first().behaviorScore, 0.001)
        assertEquals("high", insights.first().riskBand)
    }

    @Test
    fun `an unreadable search is a failure, never a quiet no results`() = runBlocking {
        coEvery {
            postgrest.rpc(any<String>(), any<JsonObject>(), any())
        } throws IllegalStateException("HTTP 404 Could not find the function public.search_members_v1")

        val outcome = repository.searchMembers("thandi")

        assertTrue("a missing or broken endpoint must be distinguishable from an empty roster", outcome.isFailure)
        assertNull(outcome.getOrNull())
    }
}
