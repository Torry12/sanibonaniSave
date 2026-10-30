package com.sanibonani.save.data.remote

import com.sanibonani.save.data.repository.group.VotingRepositoryImpl
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

/**
 * The voting screen's Close button calls `close_poll_v1`, a function no schema declared until
 * SYNC_DELTA_20261029B, so the only way a poll could end was its own deadline. The delta also
 * puts the entitlement in the database - the screen renders the button for every viewer - which
 * means a refusal is a normal answer the caller has to be able to receive. A blank poll id is
 * not: PostgREST would fail the uuid cast before the function ran, so it is refused here first.
 */
class ClosePollRpcParamsTest {

    @Test
    fun `the close call targets the declared rpc name`() {
        assertEquals("close_poll_v1", VotingRepositoryImpl.CLOSE_POLL_RPC)
    }

    @Test
    fun `the close call carries the poll id under the declared parameter name`() {
        val params = VotingRepositoryImpl.buildClosePollRpcParams(
            "22222222-2222-2222-2222-222222222222"
        )

        assertEquals(1, params.size)
        assertEquals(
            "22222222-2222-2222-2222-222222222222",
            params["p_poll_id"]?.jsonPrimitive?.content
        )
    }

    @Test
    fun `a poll without an id is refused before it reaches the wire`() {
        listOf("", "   ").forEach { blank ->
            try {
                VotingRepositoryImpl.buildClosePollRpcParams(blank)
                fail("expected a refusal for [${blank}]")
            } catch (expected: IllegalArgumentException) {
                assertEquals("This poll cannot be closed: it has no identifier.", expected.message)
            }
        }
    }
}
