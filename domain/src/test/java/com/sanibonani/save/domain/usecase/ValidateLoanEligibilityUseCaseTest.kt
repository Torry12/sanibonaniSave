package com.sanibonani.save.domain.usecase

import com.sanibonani.save.data.utils.DateProvider
import com.sanibonani.save.domain.model.Contribution
import com.sanibonani.save.domain.model.ContributionStatus
import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.Member
import com.sanibonani.save.domain.model.MemberStatus
import com.sanibonani.save.domain.repository.MemberRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class ValidateLoanEligibilityUseCaseTest {

    private val memberRepository = mockk<MemberRepository>()
    private val useCase = ValidateLoanEligibilityUseCase(memberRepository)
    private val fixedNow = LocalDate.of(2026, 4, 29)

    @Before
    fun setup() {
        mockkObject(DateProvider)
        every { DateProvider.getCurrentDate() } returns fixedNow
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `invoke - ineligible if member is not active`() = runTest {
        val member = Member(status = MemberStatus.PROBATION)
        val group = Group()

        val result = useCase(member, group)

        assertTrue(result is ValidateLoanEligibilityUseCase.EligibilityResult.Ineligible)
        assertEquals("Only active members can request loans.", (result as ValidateLoanEligibilityUseCase.EligibilityResult.Ineligible).reason)
    }

    @Test
    fun `invoke - ineligible if member joined less than 6 months ago`() = runTest {
        // joined 3 months before fixedNow
        val joinedDate = fixedNow.minusMonths(3).toString()
        val member = Member(status = MemberStatus.ACTIVE, joinedAt = "${joinedDate}T00:00:00Z")
        val group = Group()

        val result = useCase(member, group)

        assertTrue(result is ValidateLoanEligibilityUseCase.EligibilityResult.Ineligible)
        assertTrue((result as ValidateLoanEligibilityUseCase.EligibilityResult.Ineligible).reason.contains("at least 6 months"))
    }

    @Test
    fun `invoke - ineligible if contributions are overdue`() = runTest {
        // Joined 7 months before fixedNow
        val joinedDate = fixedNow.minusMonths(7).toString()
        val member = Member(id = "m1", status = MemberStatus.ACTIVE, joinedAt = "${joinedDate}T00:00:00Z")
        val group = Group(id = "g1", monthlyContribution = 100.0, paymentDueDay = 1)

        val overdueContribution = Contribution(
            memberId = "m1",
            groupId = "g1",
            amount = 100.0,
            status = ContributionStatus.OVERDUE,
            dueDate = fixedNow.minusDays(10).toString()
        )

        coEvery { memberRepository.getMemberContributions("m1", "g1") } returns flowOf(Result.success(listOf(overdueContribution)))

        val result = useCase(member, group)

        assertTrue(result is ValidateLoanEligibilityUseCase.EligibilityResult.Ineligible)
        val reason = (result as ValidateLoanEligibilityUseCase.EligibilityResult.Ineligible).reason
        assertTrue(reason.contains("up to date with your monthly contributions"))
    }

    @Test
    fun `invoke - eligible if all criteria met`() = runTest {
        // Joined 6 months before fixedNow (exact minimum)
        val joinedDate = fixedNow.minusMonths(6).toString()
        val member = Member(id = "m1", status = MemberStatus.ACTIVE, joinedAt = "${joinedDate}T00:00:00Z")
        val group = Group(id = "g1", monthlyContribution = 100.0, paymentDueDay = 1)

        // Mock contributions for each month from joined month to current month
        // 6 months ago to now = 7 months total (e.g. Oct, Nov, Dec, Jan, Feb, Mar, Apr)
        val contributions = (0..6).map { i ->
            Contribution(
                memberId = "m1",
                groupId = "g1",
                amount = 100.0,
                status = ContributionStatus.PAID,
                dueDate = fixedNow.minusMonths(i.toLong()).withDayOfMonth(1).toString()
            )
        }

        coEvery { memberRepository.getMemberContributions("m1", "g1") } returns flowOf(Result.success(contributions))

        val result = useCase(member, group)

        assertTrue(result is ValidateLoanEligibilityUseCase.EligibilityResult.Eligible)
    }
}
