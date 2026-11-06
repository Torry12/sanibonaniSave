package com.sanibonani.save.worker

import com.sanibonani.save.domain.model.AdminFeeState
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

    @Test
    fun `a fee status the server refused to write is not announced`() {
        // The group row still says DUE, so a GROUP_SUSPENDED push tells every member the group
        // is locked while the books say it is not - and the old code returned success, so the
        // escalation never ran again.
        val outcome = feeWriteOutcome(
            statusChanged = true,
            writeResult = Result.failure<Unit>(IOException("HTTP 500"))
        )
        assertEquals(FeeWriteOutcome.Refused, outcome)
    }

    @Test
    fun `a fee status that landed is safe to announce`() {
        val outcome = feeWriteOutcome(statusChanged = true, writeResult = Result.success(Unit))
        assertEquals(FeeWriteOutcome.Committed, outcome)
    }

    @Test
    fun `an unchanged standing needs no write to announce`() {
        // No write happened, but the standing the cycle is reporting is the one already on the row.
        val outcome = feeWriteOutcome(statusChanged = false, writeResult = null)
        assertEquals(FeeWriteOutcome.NoChange, outcome)
    }

    // ---- Who the cycle is allowed to tell, and the kill switch behind the standing ----

    @Test
    fun `a standing that just moved is announced`() {
        val verdict = feeNotificationVerdict(FeeWriteOutcome.Committed, standingFlagRepaired = false)
        assertEquals(FeeNotificationVerdict.Announce, verdict)
    }

    @Test
    fun `a group parked in a standing is not re-broadcast on every daily run`() {
        // WARNING and SUSPENDED are terminal until the fee is paid, so a group sitting in either
        // used to push the same escalation to every member once a day, forever.
        val verdict = feeNotificationVerdict(FeeWriteOutcome.NoChange, standingFlagRepaired = false)
        assertEquals(FeeNotificationVerdict.StayQuiet, verdict)
    }

    @Test
    fun `a suspension the flag write has only now made real is still announced`() {
        // The cycle that committed fee_status='suspended' died before the flag, so the run that
        // heals it sees no status change. The lock is brand new as far as the member is concerned.
        val verdict = feeNotificationVerdict(FeeWriteOutcome.NoChange, standingFlagRepaired = true)
        assertEquals(FeeNotificationVerdict.Announce, verdict)
    }

    @Test
    fun `a standing the server refused to write is never announced`() {
        val verdict = feeNotificationVerdict(FeeWriteOutcome.Refused, standingFlagRepaired = true)
        assertEquals(FeeNotificationVerdict.StayQuiet, verdict)
    }

    @Test
    fun `a suspended standing whose row flag is still clear needs the flag written`() {
        assertTrue(suspensionFlagWriteNeeded(rowSaysSuspended = false, standing = AdminFeeState.SUSPENDED))
    }

    @Test
    fun `a suspended standing already flagged on the row needs no second write`() {
        assertFalse(suspensionFlagWriteNeeded(rowSaysSuspended = true, standing = AdminFeeState.SUSPENDED))
    }

    @Test
    fun `a standing short of suspension never sets the flag`() {
        for (standing in listOf(
            AdminFeeState.PAID,
            AdminFeeState.PENDING_ACTIVATION,
            AdminFeeState.DUE,
            AdminFeeState.OVERDUE,
            AdminFeeState.WARNING
        )) {
            assertFalse("flags a suspended group in $standing", suspensionFlagWriteNeeded(false, standing))
        }
    }

    @Test
    fun `the fee cycle never lifts a flag the row already carries`() {
        // Clearing is unsuspendGroup's job. A client-side cycle that auto-cleared the flag could
        // release a kill switch a Platform Admin imposed for a reason the fee books do not know.
        assertFalse(suspensionFlagWriteNeeded(rowSaysSuspended = true, standing = AdminFeeState.PAID))
    }
}
