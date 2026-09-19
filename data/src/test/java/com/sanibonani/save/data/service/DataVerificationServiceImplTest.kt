package com.sanibonani.save.data.service

import com.sanibonani.save.domain.model.*
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DataVerificationServiceImplTest {

    private lateinit var service: DataVerificationServiceImpl

    @Before
    fun setup() {
        service = DataVerificationServiceImpl()
    }

    @Test
    fun `verifyMemberData should handle loan settlement extractions correctly`() {
        // Given: A member with R3,000 total paid
        val member = Member(
            id = "m1",
            fullName = "Settlement Target",
            totalPaid = Money(3000.0),
            status = MemberStatus.ACTIVE
        )
        val group = Group(id = "g1", probationMonths = 3)

        // And: Transactions summing to R3,000 (R5,000 contrib - R2,000 extraction)
        val contributions = listOf(
            Contribution(
                id = "c1",
                amount = Money(5000.0),
                type = "contribution",
                status = ContributionStatus.PAID
            ),
            Contribution(
                id = "c2",
                amount = Money(-2000.0),
                type = "loan_settlement_extraction",
                status = ContributionStatus.PAID
            )
        )

        // When: Verifying data
        val result = service.verifyMemberData(member, group, contributions)

        // Then: Result should be valid (no discrepancy warning)
        assertTrue("Data should be valid when extractions are included in calculation", result.isValid)
        assertTrue("There should be no issues found", result.issues.isEmpty())
    }

    @Test
    fun `verifyMemberData should report discrepancy when transactions do not match total paid`() {
        // Given: A member with R4,000 total paid but R5,000 in transactions
        val member = Member(
            id = "m1",
            totalPaid = Money(4000.0),
            status = MemberStatus.ACTIVE
        )
        val group = Group(id = "g1", probationMonths = 3)
        val contributions = listOf(
            Contribution(
                id = "c1",
                amount = Money(5000.0),
                type = "contribution",
                status = ContributionStatus.PAID
            )
        )

        // When: Verifying data
        val result = service.verifyMemberData(member, group, contributions)

        // Then: Result should be invalid
        assertFalse("Data should be invalid when transactions do not match", result.isValid)
        assertTrue(result.issues.any { it.category == "Financial" && it.message.contains("does not match sum") })
    }
}
