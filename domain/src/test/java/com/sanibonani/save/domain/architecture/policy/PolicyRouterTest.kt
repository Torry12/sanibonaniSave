package com.sanibonani.save.domain.architecture.policy

import com.sanibonani.save.domain.architecture.FinancialGroupModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PolicyRouterTest {

    @Test
    fun `default router allows command when no rule matches`() {
        val router = InMemoryPolicyRouter()

        val decision = router.evaluate(
            PolicyCommand(
                groupId = "g1",
                model = FinancialGroupModel.ROSCA,
                commandType = PolicyCommandType.PAYOUT_REQUEST,
                actorId = "admin1",
                amount = 100.0
            )
        ).getOrThrow()

        assertTrue(decision.allowed)
        assertEquals(1, decision.requiredApprovals)
    }

    @Test
    fun `dual approval rule applies for large amounts`() {
        val router = InMemoryPolicyRouter(
            listOf(LargeAmountDualApprovalRule(threshold = 5000.0))
        )

        val decision = router.evaluate(
            PolicyCommand(
                groupId = "g1",
                model = FinancialGroupModel.BUSINESS_CAPITAL_GROUP,
                commandType = PolicyCommandType.DISBURSEMENT_RELEASE,
                actorId = "admin1",
                amount = 8000.0
            )
        ).getOrThrow()

        assertTrue(decision.allowed)
        assertEquals(2, decision.requiredApprovals)
        assertTrue(decision.obligations.contains("second_approver_required"))
    }

    @Test
    fun `suspended group deny rule blocks operation`() {
        val router = InMemoryPolicyRouter(
            listOf(
                LargeAmountDualApprovalRule(threshold = 5000.0),
                SuspendedGroupBlockRule()
            )
        )

        val decision = router.evaluate(
            PolicyCommand(
                groupId = "g1",
                model = FinancialGroupModel.ROSCA,
                commandType = PolicyCommandType.PAYOUT_REQUEST,
                actorId = "admin1",
                amount = 9000.0,
                metadata = mapOf("is_platform_suspended" to "true")
            )
        ).getOrThrow()

        assertFalse(decision.allowed)
        assertEquals("Group is platform suspended.", decision.reason)
    }
}

