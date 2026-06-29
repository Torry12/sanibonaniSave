package com.sanibonani.save.domain.usecase

import com.sanibonani.save.domain.model.GroupSettings
import com.sanibonani.save.domain.model.RoscaRotationMethod
import com.sanibonani.save.domain.repository.GroupRepository
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateGroupSettingsUseCaseTest {

    private val groupRepo = mockk<GroupRepository>(relaxed = true)
    private val useCase = UpdateGroupSettingsUseCase(groupRepo)

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
}

