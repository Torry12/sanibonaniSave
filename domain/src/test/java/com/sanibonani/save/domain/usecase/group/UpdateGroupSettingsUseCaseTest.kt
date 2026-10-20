package com.sanibonani.save.domain.usecase.group

import com.sanibonani.save.domain.model.GroupSettings
import com.sanibonani.save.domain.model.RoscaRotationMethod
import com.sanibonani.save.domain.repository.group.GroupRepository
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class UpdateGroupSettingsUseCaseTest {

    private val groupRepo = mockk<GroupRepository>(relaxed = true)
    private val supabaseRepo = mockk<com.sanibonani.save.domain.repository.auth.SupabaseRepository>(relaxed = true)
    private val useCase = UpdateGroupSettingsUseCase(groupRepo, supabaseRepo)

    @Before
    fun setup() {
        coEvery { groupRepo.getGroupById(any()) } returns Result.success(com.sanibonani.save.domain.model.Group(id = "g1"))
    }

    @Suppress("UNCHECKED_CAST")
    @Test
    fun `invoke includes rosca_rotation_method with lowercase value`() = runTest {
        val slot = slot<Map<String, Any>>()
        coEvery { groupRepo.updateGroupSettings("g1", capture(slot)) } returns Result.success(Unit)

        val settings = GroupSettings(rotationMethod = RoscaRotationMethod.RANDOM_DRAW)
        useCase("g1", settings)

        assertTrue(slot.isCaptured)
        val updates = slot.captured
        assertEquals("random_draw", updates["rosca_rotation_method"])
    }

    @Test
    fun `invoke rosca_rotation_method fixed stores as fixed`() = runTest {
        val slot = slot<Map<String, Any>>()
        coEvery { groupRepo.updateGroupSettings("g2", capture(slot)) } returns Result.success(Unit)

        val settings = GroupSettings(rotationMethod = RoscaRotationMethod.FIXED)
        useCase("g2", settings)

        assertEquals("fixed", slot.captured["rosca_rotation_method"])
    }

    @Test
    fun `invoke rosca_rotation_method auction stores as auction`() = runTest {
        val slot = slot<Map<String, Any>>()
        coEvery { groupRepo.updateGroupSettings("g3", capture(slot)) } returns Result.success(Unit)

        val settings = GroupSettings(rotationMethod = RoscaRotationMethod.AUCTION)
        useCase("g3", settings)

        assertEquals("auction", slot.captured["rosca_rotation_method"])
    }

    @Test
    fun `invoke rosca_rotation_method need_based stores as need_based`() = runTest {
        val slot = slot<Map<String, Any>>()
        coEvery { groupRepo.updateGroupSettings("g4", capture(slot)) } returns Result.success(Unit)

        val settings = GroupSettings(rotationMethod = RoscaRotationMethod.NEED_BASED)
        useCase("g4", settings)

        assertEquals("need_based", slot.captured["rosca_rotation_method"])
    }

    @Test
    fun `invoke fails when payment due day out of range`() = runTest {
        val settings = GroupSettings(paymentDueDay = "29")
        val result = useCase("g5", settings)
        assertTrue(result.isFailure)
    }

    @Test
    fun `monetary settings are sent as exact 2dp decimal strings, never Double`() = runTest {
        val slot = slot<Map<String, Any>>()
        coEvery { groupRepo.updateGroupSettings("g6", capture(slot)) } returns Result.success(Unit)

        val settings = GroupSettings(
            goalAmount = "1234.567",
            burialClaimAmount = "8000",
            monthlyContribution = "50.005",
            loanMaxAmount = "",
            minReservePercentage = "12.5"
        )
        useCase("g6", settings)

        val updates = slot.captured
        assertEquals("1234.57", updates["goal_amount"])
        assertEquals("8000.00", updates["burial_claim_amount"])
        assertEquals("50.00", updates["monthly_contribution"])
        assertEquals("5000.00", updates["loan_max_amount"])
        assertEquals("12.50", updates["min_reserve_percentage"])
        assertTrue(updates.values.none { it is Double })
    }

    @Test
    fun `invoke rejects a mistyped monetary setting instead of storing the default`() = runTest {
        // '5O0' is a letter O typed instead of a zero. Falling back to the default would post the
        // group's monthly contribution as R0.00 while the admin believes it is R500.
        val result = useCase("g7", GroupSettings(monthlyContribution = "5O0"))

        assertTrue("A mistyped contribution must be rejected, not written as R0.00", result.isFailure)
        io.mockk.coVerify(exactly = 0) { groupRepo.updateGroupSettings(any(), any<Map<String, Any>>()) }
    }

    @Test
    fun `invoke rejects a mistyped whole-number setting instead of storing the default`() = runTest {
        val result = useCase("g8", GroupSettings(maxMembers = "1O"))

        assertTrue("A mistyped member cap must be rejected, not written as 10", result.isFailure)
        io.mockk.coVerify(exactly = 0) { groupRepo.updateGroupSettings(any(), any<Map<String, Any>>()) }
    }

    @Test
    fun `blank settings still fall back to their documented defaults`() = runTest {
        val slot = slot<Map<String, Any>>()
        coEvery { groupRepo.updateGroupSettings("g9", capture(slot)) } returns Result.success(Unit)

        val result = useCase("g9", GroupSettings(monthlyContribution = "", maxMembers = ""))

        assertTrue(result.isSuccess)
        assertEquals("0.00", slot.captured["monthly_contribution"])
        assertEquals(10, slot.captured["max_members"])
    }
}

