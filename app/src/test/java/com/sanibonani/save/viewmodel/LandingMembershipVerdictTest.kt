package com.sanibonani.save.viewmodel

import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.Member
import com.sanibonani.save.features.landing.presentation.landingMembershipVerdict
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The landing hero acts on these two verdicts: `isMemberOrAdmin = false` puts "Join a group" in
 * front of someone who is already in one, and a null `pendingGroup` hides the prompt to finish a
 * registration whose joining fee is still owed. Neither may be asserted from a read the server
 * refused.
 */
class LandingMembershipVerdictTest {

    private val memberRow = Member(id = "m-1", groupId = "g-1")
    private val paidGroup = Group(id = "g-1", name = "Paid group", registrationPaid = true)
    private val unpaidGroup = Group(id = "g-2", name = "Unpaid group", registrationPaid = false)

    private fun verdict(
        memberships: Result<List<Member>>?,
        managed: Result<List<Group>>?,
        previouslyMember: Boolean = false,
        previouslyPending: Group? = null
    ) = landingMembershipVerdict(memberships, managed, previouslyMember, previouslyPending)

    @Test
    fun `a refusal of both reads cannot claim the user belongs to nothing`() {
        val result = verdict(
            memberships = Result.failure(IOException("HTTP 500")),
            managed = Result.failure(IOException("HTTP 500")),
            previouslyMember = true,
            previouslyPending = unpaidGroup
        )

        assertTrue("the last proven standing has to survive the refusal", result.isMemberOrAdmin)
        assertEquals(unpaidGroup, result.pendingGroup)
        assertNotNull("and the refusal itself has to be reported", result.failure)
    }

    // One leg answering is enough to prove membership, but not to clear the unpaid-registration
    // prompt: only the managed-groups read can say the fee was paid.
    @Test
    fun `a refused managed-groups read keeps the pending registration prompt`() {
        val result = verdict(
            memberships = Result.success(listOf(memberRow)),
            managed = Result.failure(IOException("HTTP 503")),
            previouslyPending = unpaidGroup
        )

        assertTrue(result.isMemberOrAdmin)
        assertEquals(
            "a group whose joining fee is owed must not disappear on a failed read",
            unpaidGroup,
            result.pendingGroup
        )
        assertNotNull(result.failure)
    }

    @Test
    fun `a refused membership read cannot say the user is not a member`() {
        val result = verdict(
            memberships = Result.failure(IOException("HTTP 500")),
            managed = Result.success(emptyList()),
            previouslyMember = true
        )

        // The managed leg answered and was empty, but the membership leg did not, so the standing
        // the books last proved stays.
        assertTrue(result.isMemberOrAdmin)
        assertNotNull(result.failure)
    }

    @Test
    fun `both reads answering empty does clear the standing`() {
        val result = verdict(
            memberships = Result.success(emptyList()),
            managed = Result.success(emptyList()),
            previouslyMember = true,
            previouslyPending = unpaidGroup
        )

        // Someone who left every group is not a member any more, and nothing is pending. A verdict
        // that only ever turns true would leave the dashboard CTA up forever.
        assertEquals(false, result.isMemberOrAdmin)
        assertNull(result.pendingGroup)
        assertNull(result.failure)
    }

    @Test
    fun `an unpaid managed group is the pending registration`() {
        val result = verdict(
            memberships = Result.success(emptyList()),
            managed = Result.success(listOf(paidGroup, unpaidGroup))
        )

        assertTrue(result.isMemberOrAdmin)
        assertEquals(unpaidGroup, result.pendingGroup)
        assertNull(result.failure)
    }

    @Test
    fun `a paid set of managed groups clears a stale pending registration`() {
        val result = verdict(
            memberships = Result.success(listOf(memberRow)),
            managed = Result.success(listOf(paidGroup)),
            previouslyPending = unpaidGroup
        )

        assertNull(
            "the read says the fee is settled, so the prompt must go",
            result.pendingGroup
        )
        assertNull(result.failure)
    }

    @Test
    fun `a membership alone makes the user a member with nothing pending`() {
        val result = verdict(
            memberships = Result.success(listOf(memberRow)),
            managed = Result.success(emptyList())
        )

        assertTrue(result.isMemberOrAdmin)
        assertNull(result.pendingGroup)
    }

    // Not signed in is a different shape from a refused read: both deferred legs are absent, and
    // that is a proven answer, not a failure to answer.
    @Test
    fun `a signed-out visitor is proven rather than unknown`() {
        val result = verdict(memberships = null, managed = null, previouslyMember = true, previouslyPending = unpaidGroup)

        assertNull("a visitor has nothing to report", result.failure)
    }
}
