package com.sanibonani.save.worker

import com.sanibonani.save.domain.model.Contribution
import com.sanibonani.save.domain.model.ContributionStatus
import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.GroupType
import com.sanibonani.save.domain.model.Money
import com.sanibonani.save.domain.model.PlatformConfig
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Three background cycles read the books they then act on, and all three folded a refused read
 * into a benign answer: [AutomatedRoscaWorker] and [FeeEnforcementWorker] returned
 * `Result.success()` for `getGroupById(groupId).getOrNull() ?: ...`, and
 * [ProbationCompletionWorker] treated a null group as "no joining fee owed".
 *
 * [com.sanibonani.save.domain.repository.group.GroupRepository.getGroupById] returns
 * `Result<Group>` and throws when neither the server nor Room has the row, so the null those
 * three read is only ever produced by a *refused* read - never by a deleted group. The verdict
 * helpers below are the part of each cycle that can be decided from a pair of Results, and they
 * are what the workers now branch on.
 */
class WorkerCycleVerdictTest {

    private fun group(type: GroupType, joiningFee: Money = Money("50.00")) = Group(
        id = "g-1",
        name = "Alpha",
        type = type,
        balance = Money("1000.00"),
        joiningFee = joiningFee
    )

    private fun contribution(type: String, status: ContributionStatus) = Contribution(
        id = "c-1",
        groupId = "g-1",
        memberId = "m-1",
        amount = Money("50.00"),
        status = status,
        type = type
    )

    // ---- ROSCA payout automation ----

    @Test
    fun `a ROSCA group the cycle actually read runs the payout`() {
        val verdict = roscaPayoutCycleVerdict(Result.success(group(GroupType.ROSCA)))
        assertEquals(RoscaCycleVerdict.RunPayout, verdict)
    }

    @Test
    fun `a group that is not a ROSCA has nothing to automate`() {
        val verdict = roscaPayoutCycleVerdict(Result.success(group(GroupType.BURIAL_SOCIETY)))
        assertEquals(RoscaCycleVerdict.NotRosca, verdict)
    }

    @Test
    fun `a refused group read retries instead of skipping the pot`() {
        // The old code answered this Result.success(), and the schedule waits a full 24h cycle
        // before it looks at the group again.
        val verdict = roscaPayoutCycleVerdict(Result.failure<Group>(IOException("group read failed")))
        assertEquals(RoscaCycleVerdict.ReadRefused, verdict)
    }

    // ---- Probation promotion ----

    @Test
    fun `the joining fee rule is enforceable when both books answered`() {
        assertTrue(
            joiningFeeRuleIsReadable(
                Result.success(group(GroupType.BURIAL_SOCIETY)),
                Result.success(listOf(contribution("joining_fee", ContributionStatus.PAID)))
            )
        )
    }

    @Test
    fun `a refused group read cannot be read as no joining fee owed`() {
        assertFalse(
            joiningFeeRuleIsReadable(
                Result.failure<Group>(IOException("group read failed")),
                Result.success(emptyList())
            )
        )
    }

    @Test
    fun `a refused contribution read cannot be read as an unpaid fee either`() {
        // Deferring is the honest answer: assuming "unpaid" silently blocks the member, and
        // assuming "no group" silently promotes them with the fee still owed.
        assertFalse(
            joiningFeeRuleIsReadable(
                Result.success(group(GroupType.BURIAL_SOCIETY)),
                Result.failure<List<Contribution>>(IOException("contribution read failed"))
            )
        )
    }

    // ---- Platform fee enforcement ----

    @Test
    fun `a cycle with live config and a live group enforces`() {
        val verdict = feeCycleVerdict(
            configRefresh = Result.success(PlatformConfig()),
            groupResult = Result.success(group(GroupType.STOKVEL))
        )
        assertEquals(FeeCycleVerdict.Enforce, verdict)
    }

    @Test
    fun `a refused config refresh cannot be enforced against the in-code defaults`() {
        val verdict = feeCycleVerdict(
            configRefresh = Result.failure<PlatformConfig>(IOException("config read failed")),
            groupResult = Result.success(group(GroupType.STOKVEL))
        )
        assertEquals(FeeCycleVerdict.ReadsRefused, verdict)
    }

    @Test
    fun `a refused group read is not a clean fee cycle`() {
        val verdict = feeCycleVerdict(
            configRefresh = Result.success(PlatformConfig()),
            groupResult = Result.failure<Group>(IOException("group read failed"))
        )
        assertEquals(FeeCycleVerdict.ReadsRefused, verdict)
    }
}
