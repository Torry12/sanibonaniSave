package com.sanibonani.save.domain.usecase

import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.GroupType
import com.sanibonani.save.domain.model.Member
import com.sanibonani.save.domain.model.MemberStatus
import com.sanibonani.save.domain.model.RoscaRotationMethod
import com.sanibonani.save.domain.usecase.rosca.CalculateRoscaRotationUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalculateRoscaRotationUseCaseTest {

    private val useCase = CalculateRoscaRotationUseCase()

    @Test
    fun `invoke excludes suspended members from schedule and pot`() {
        val group = Group(
            id = "g-1",
            name = "ROSCA One",
            type = GroupType.ROSCA,
            monthlyContribution = 500.0,
            createdAt = "2026-01-01T00:00:00Z"
        )
        val members = listOf(
            Member(id = "m1", fullName = "Alice", status = MemberStatus.ACTIVE, joinedAt = "2026-01-01T00:00:00Z"),
            Member(id = "m2", fullName = "Bob", status = MemberStatus.SUSPENDED, joinedAt = "2026-01-02T00:00:00Z"),
            Member(id = "m3", fullName = "Cathy", status = MemberStatus.PROBATION, joinedAt = "2026-01-03T00:00:00Z")
        )

        val result = useCase(group, members).getOrThrow()

        assertEquals(2, result.cycleMonths)
        assertEquals(1000.0, result.totalPot, 0.01)
        assertEquals(2, result.items.size)
    }

    @Test
    fun `invoke random draw order is stable for same group seed`() {
        val group = Group(
            id = "g-seed",
            name = "ROSCA Draw",
            type = GroupType.ROSCA,
            rotationMethod = RoscaRotationMethod.RANDOM_DRAW,
            monthlyContribution = 200.0,
            createdAt = "2026-01-01T00:00:00Z"
        )
        val members = (1..6).map {
            Member(id = "m$it", fullName = "Member $it", status = MemberStatus.ACTIVE, joinedAt = "2026-01-0${it}T00:00:00Z")
        }

        val first = useCase(group, members).getOrThrow().items.map { it.memberId }
        val second = useCase(group, members).getOrThrow().items.map { it.memberId }

        assertEquals(first, second)
        assertTrue(first.isNotEmpty())
    }

    @Test
    fun `invoke uses fixed order even when description mentions random`() {
        val group = Group(
            id = "g-fixed",
            name = "ROSCA Fixed",
            type = GroupType.ROSCA,
            rotationMethod = RoscaRotationMethod.FIXED,
            description = "random draw this cycle",
            monthlyContribution = 200.0,
            createdAt = "2026-01-01T00:00:00Z"
        )
        val members = listOf(
            Member(id = "m1", fullName = "Member 1", status = MemberStatus.ACTIVE, joinedAt = "2026-01-01T00:00:00Z"),
            Member(id = "m2", fullName = "Member 2", status = MemberStatus.ACTIVE, joinedAt = "2026-01-02T00:00:00Z"),
            Member(id = "m3", fullName = "Member 3", status = MemberStatus.ACTIVE, joinedAt = "2026-01-03T00:00:00Z")
        )

        val order = useCase(group, members).getOrThrow().items.map { it.memberId }

        assertEquals(listOf("m1", "m2", "m3"), order)
    }
}

