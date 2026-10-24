package com.sanibonani.save.domain.usecase.actuarial
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.admin.*
import com.sanibonani.save.domain.repository.auth.*

import com.sanibonani.save.domain.repository.equity.MemberContributionRepository
import com.sanibonani.save.domain.repository.group.*
import com.sanibonani.save.domain.repository.member.*
import com.sanibonani.save.domain.repository.system.*
import io.mockk.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CalculateGroupHealthScoreUseCaseTest {
    private lateinit var useCase: CalculateGroupHealthScoreUseCase
    private val groupRepository: GroupRepository = mockk()
    private val memberRepository: MemberRepository = mockk()
    private val contributionRepository: MemberContributionRepository = mockk()
    private val healthScoreRepository: HealthScoreRepository = mockk(relaxed = true)

    @Before
    fun setUp() {
        useCase = CalculateGroupHealthScoreUseCase(
            groupRepository, memberRepository, contributionRepository, healthScoreRepository
        )
    }

    @Test
    fun `calculates score for low reserve group`() = runBlocking {
        val group = Group(
            id = "g1",
            name = "Test Group",
            balance = Money("100.0"),
            adminUserId = "admin1",
            registrationPaid = true
        )
        val contributions = listOf(
            Contribution(id = "c1", memberId = "m1", groupId = "g1", amount = Money("1000.0"), dueDate = "2026-04-01"),
            Contribution(id = "c2", memberId = "m1", groupId = "g1", amount = Money("1000.0"), dueDate = "2026-03-01"),
            Contribution(id = "c3", memberId = "m2", groupId = "g1", amount = Money("1000.0"), dueDate = "2026-04-01")
        )
        val members = listOf(
            Member(id = "m1", groupId = "g1", status = MemberStatus.ACTIVE),
            Member(id = "m2", groupId = "g1", status = MemberStatus.ACTIVE)
        )

        coEvery { groupRepository.getGroupById("g1") } returns Result.success(group)
        every { memberRepository.getGroupMembers("g1") } returns flowOf(Result.success(members))
        every { contributionRepository.getGroupContributions("g1") } returns flowOf(Result.success(contributions))

        val result = useCase("g1")
        assertTrue(result.isSuccess)
        val score = result.getOrThrow()
        assertTrue(score.overallScore in 0..100)
        assertTrue(score.recommendations.isNotEmpty())
    }

    @Test
    fun `calculates score for strong reserve group`() = runBlocking {
        val group = Group(
            id = "g1",
            name = "Healthy Group",
            balance = Money("12000.0"),
            adminUserId = "admin1",
            registrationPaid = true
        )
        val contributions = (1..12).map { month ->
            Contribution(
                id = "c$month",
                memberId = "m1",
                groupId = "g1",
                amount = Money("1000.0"),
                dueDate = "2026-${month.toString().padStart(2, '0')}-01"
            )
        }
        val members = (1..20).map { idx ->
            Member(id = "m$idx", groupId = "g1", status = MemberStatus.ACTIVE)
        }

        coEvery { groupRepository.getGroupById("g1") } returns Result.success(group)
        every { memberRepository.getGroupMembers("g1") } returns flowOf(Result.success(members))
        every { contributionRepository.getGroupContributions("g1") } returns flowOf(Result.success(contributions))

        val result = useCase("g1")
        assertTrue(result.isSuccess)
        val score = result.getOrThrow()
        assertTrue(score.components.keys.contains("Reserve Adequacy"))
    }

    @Test
    fun `calculates score for moderate group`() = runBlocking {
        val group = Group(
            id = "g1",
            name = "Moderate Group",
            balance = Money("5000.0"),
            adminUserId = "admin1",
            registrationPaid = true
        )
        val contributions = (1..6).map { month ->
            Contribution(
                id = "c$month",
                memberId = "m1",
                groupId = "g1",
                amount = Money("1000.0"),
                dueDate = "2026-${month.toString().padStart(2, '0')}-01"
            )
        }
        val members = (1..15).map { idx ->
            Member(id = "m$idx", groupId = "g1", status = MemberStatus.ACTIVE)
        }

        coEvery { groupRepository.getGroupById("g1") } returns Result.success(group)
        every { memberRepository.getGroupMembers("g1") } returns flowOf(Result.success(members))
        every { contributionRepository.getGroupContributions("g1") } returns flowOf(Result.success(contributions))

        val result = useCase("g1")
        assertTrue(result.isSuccess)
        val score = result.getOrThrow()
        assertTrue(score.components.isNotEmpty())
    }

    @Test
    fun `score reflects contribution history instead of empty defaults`() = runBlocking {
        val group = Group(
            id = "g1",
            name = "Reserve Group",
            balance = Money("12000.0"),
            adminUserId = "admin1",
            registrationPaid = true
        )
        val members = (1..20).map { idx ->
            Member(id = "m$idx", groupId = "g1", status = MemberStatus.ACTIVE)
        }
        val contributions = (1..12).map { month ->
            Contribution(
                id = "c$month",
                memberId = "m1",
                groupId = "g1",
                amount = Money("1000.0"),
                dueDate = "2026-${month.toString().padStart(2, '0')}-01"
            )
        }

        coEvery { groupRepository.getGroupById("g1") } returns Result.success(group)
        every { memberRepository.getGroupMembers("g1") } returns flowOf(Result.success(members))

        // Twelve months of R1000 income against a R12 000 balance is a 2.0 reserve ratio, which
        // takes the full 20 points.
        every { contributionRepository.getGroupContributions("g1") } returns flowOf(Result.success(contributions))
        val withHistory = useCase("g1").getOrThrow().components.getValue("Reserve Adequacy")

        // An empty contribution feed silently reports 12 instead, because the fallback assumes a
        // one-month reserve rather than admitting there is no data.
        every { contributionRepository.getGroupContributions("g1") } returns flowOf(Result.success(emptyList()))
        val withoutHistory = useCase("g1").getOrThrow().components.getValue("Reserve Adequacy")

        assertEquals(20, withHistory)
        assertEquals(12, withoutHistory)
    }
}
