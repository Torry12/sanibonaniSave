package com.sanibonani.save.data.service

import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.system.SyncRepository
import com.sanibonani.save.domain.utils.PaymentCalculator
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DataVerificationServiceImplTest {

    private lateinit var service: DataVerificationServiceImpl
    private lateinit var paymentCalculator: PaymentCalculator
    private lateinit var outstandingMisses: MutableStateFlow<Map<String, Set<String>>>

    @Before
    fun setup() {
        paymentCalculator = mockk(relaxed = true)
        outstandingMisses = MutableStateFlow(emptyMap())
        val syncRepo = mockk<SyncRepository>()
        every { syncRepo.staleGroups } returns outstandingMisses
        service = DataVerificationServiceImpl(paymentCalculator, syncRepo)
    }

    private fun ledgerEntry(amount: String, balanceAfter: String) = LedgerEntry(
        id = "ledger-$amount",
        groupId = "g1",
        amount = Money(amount),
        balanceAfter = Money(balanceAfter),
        category = "contribution",
        createdAt = "2026-10-18T00:00:00Z"
    )

    @Test
    fun `a group whose ledger never arrived is reported unverifiable instead of valid`() {
        // A cached-but-never-synced ledger is not a clean book: comparing whatever rows the cache
        // happens to hold against the cached balance is how a sync outage became an all-clear.
        outstandingMisses.value = mapOf("g1" to setOf("the ledger"))

        val result = runBlocking {
            service.verifyGroupAdminData(
                group = Group(id = "g1", balance = Money("1000.0")),
                members = emptyList(),
                ledger = emptyList()
            )
        }

        assertFalse("a book the app never received cannot be reported as verified", result.isValid)
        val issue = result.issues.single()
        assertEquals("Sync", issue.category)
        assertTrue(result.issues.single().message.contains("the ledger"))
        assertNull("a sync outage offers no server-side repair", issue.suggestedAction)
    }

    @Test
    fun `another group's miss does not stop this group being audited`() {
        outstandingMisses.value = mapOf("other-group" to setOf("the ledger"))

        val result = runBlocking {
            service.verifyGroupAdminData(
                group = Group(id = "g1", balance = Money("1000.0"), currentMembers = 0),
                members = emptyList(),
                ledger = listOf(ledgerEntry("400.00", "400.00"), ledgerEntry("500.00", "900.00"))
            )
        }

        assertTrue(
            "the balance rules still run for a group the sync brought current",
            result.issues.any { it.category == "Financial" }
        )
    }

    @Test
    fun `a member's standing is not verified against a book that never arrived`() {
        outstandingMisses.value = mapOf("g1" to setOf("contributions"))

        val member = Member(id = "m1", groupId = "g1", totalPaid = Money("4000.0"), status = MemberStatus.ACTIVE)
        val result = runBlocking {
            service.verifyMemberData(
                member = member,
                group = Group(id = "g1", probationMonths = 3),
                contributions = listOf(
                    Contribution(
                        id = "c1",
                        memberId = "m1",
                        groupId = "g1",
                        amount = Money("5000.0"),
                        type = "contribution",
                        status = ContributionStatus.PAID
                    )
                )
            )
        }

        assertEquals("Sync", result.issues.single().category)
        assertTrue("the gap must not be reported as the member's own discrepancy", result.issues.none { it.category == "Financial" })
    }

    @Test
    fun `a platform audit does not certify a group whose book never arrived`() {
        // The platform rules compare cached analytics against cached group rows - the same
        // comparison the member and group guards exist to stop. Without it a platform admin reads
        // an all-clear over totals the sync never brought current.
        outstandingMisses.value = mapOf("g2" to setOf("the ledger"))

        val result = runBlocking {
            service.verifyPlatformAdminData(
                analytics = PlatformAnalytics(totalGroups = 2, totalBalance = Money("3000.0")),
                groups = listOf(
                    Group(id = "g1", balance = Money("1000.0")),
                    Group(id = "g2", balance = Money("2000.0"))
                )
            )
        }

        assertFalse("an unverified group cannot be certified platform-wide", result.isValid)
        val issue = result.issues.single()
        assertEquals("Sync", issue.category)
        assertTrue("the audit must name the group it could not check", issue.message.contains("g2"))
        assertTrue("a sync gap is not a platform discrepancy", result.issues.none { it.category == "Financial" })
    }

    @Test
    fun `verifyMemberData should handle loan settlement extractions correctly`() {
        // Given: A member with R3,000 total paid
        val member = Member(
            id = "m1",
            fullName = "Settlement Target",
            totalPaid = Money("3000.0"),
            status = MemberStatus.ACTIVE
        )
        val group = Group(id = "g1", probationMonths = 3)

        // And: Transactions summing to R3,000 (R5,000 contrib - R2,000 extraction)
        val contributions = listOf(
            Contribution(
                id = "c1",
                amount = Money("5000.0"),
                type = "contribution",
                status = ContributionStatus.PAID
            ),
            Contribution(
                id = "c2",
                amount = Money("-2000.0"),
                type = "loan_settlement_extraction",
                status = ContributionStatus.PAID
            )
        )

        // When: Verifying data
        val result = runBlocking { service.verifyMemberData(member, group, contributions) }

        // Then: Result should be valid (no discrepancy warning)
        assertTrue("Data should be valid when extractions are included in calculation", result.isValid)
        assertTrue("There should be no issues found", result.issues.isEmpty())
    }

    @Test
    fun `verifyMemberData should report discrepancy when transactions do not match total paid`() {
        // Given: A member with R4,000 total paid but R5,000 in transactions
        val member = Member(
            id = "m1",
            totalPaid = Money("4000.0"),
            status = MemberStatus.ACTIVE
        )
        val group = Group(id = "g1", probationMonths = 3)
        val contributions = listOf(
            Contribution(
                id = "c1",
                amount = Money("5000.0"),
                type = "contribution",
                status = ContributionStatus.PAID
            )
        )

        // When: Verifying data
        val result = runBlocking { service.verifyMemberData(member, group, contributions) }

        // Then: Result should be invalid
        assertFalse("Data should be invalid when transactions do not match", result.isValid)
        assertTrue(result.issues.any { it.category == "Financial" && it.message.contains("does not match sum") })
    }
}
