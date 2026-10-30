package com.sanibonani.save.service

import com.sanibonani.save.domain.model.ActuarialMetrics
import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.Member
import com.sanibonani.save.domain.repository.admin.ActuarialRepository
import com.sanibonani.save.domain.repository.disbursement.PayoutRepository
import com.sanibonani.save.domain.repository.group.GroupRepository
import com.sanibonani.save.domain.repository.member.MemberRepository
import com.sanibonani.save.domain.repository.system.NotificationRepository
import com.sanibonani.save.domain.repository.system.SyncRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdminGroupContextCacheServiceTest {

    private val groupId = "group-1"

    private val groupRepo = mockk<GroupRepository>()
    private val memberRepo = mockk<MemberRepository>()
    private val notificationRepo = mockk<NotificationRepository>()
    private val payoutRepo = mockk<PayoutRepository>()
    private val actuarialRepo = mockk<ActuarialRepository>()
    private val syncRepo = mockk<SyncRepository>()

    private fun service() = AdminGroupContextCacheService(
        groupRepo = groupRepo,
        memberRepo = memberRepo,
        notificationRepo = notificationRepo,
        payoutRepo = payoutRepo,
        actuarialRepo = actuarialRepo,
        syncRepo = syncRepo
    )

    private fun stubHydration(groupName: String) {
        coEvery { groupRepo.getGroupById(groupId) } returns
            Result.success(Group(id = groupId, name = groupName))
        every { memberRepo.getGroupMembers(groupId) } returns
            flowOf(Result.success(listOf(Member(id = "m1", groupId = groupId))))
        coEvery { notificationRepo.syncNotifications(groupId) } returns Result.success(Unit)
        every { notificationRepo.observeNotifications(groupId) } returns flowOf(Result.success(emptyList()))
        every { payoutRepo.observePayouts(groupId) } returns flowOf(Result.success(emptyList()))
        coEvery { actuarialRepo.computeMetrics(groupId) } returns Result.success(ActuarialMetrics())
    }

    private fun hydratedContext(name: String) = CachedAdminGroupContext(
        group = Group(id = groupId, name = name),
        isFullyHydrated = true
    )

    @Test
    fun `refreshGroup syncs before reading and rebuilds the snapshot`() = runBlocking {
        val service = service()
        service.updateContext(groupId) { hydratedContext("Old") }

        var synced = false
        coEvery { syncRepo.syncGroupData(groupId) } coAnswers {
            synced = true
            Result.success(Unit)
        }
        stubHydration("Fresh")
        every { memberRepo.getGroupMembers(groupId) } answers {
            assertTrue("members were read before the sync finished", synced)
            flowOf(Result.success(listOf(Member(id = "m1", groupId = groupId))))
        }

        val result = service.refreshGroup(groupId)

        assertTrue(result.isSuccess)
        val cached = service.getContext(groupId)!!
        assertEquals("Fresh", cached.group?.name)
        assertEquals(listOf("m1"), cached.members.map { it.id })
        assertTrue(cached.isFullyHydrated)
        coVerify(exactly = 1) { syncRepo.syncGroupData(groupId) }
    }

    @Test
    fun `a forced warmup re-reads the group row`() = runBlocking {
        val service = service()
        service.updateContext(groupId) { hydratedContext("Old") }
        stubHydration("Fresh")

        service.warmUpGroup(groupId, force = true)

        withTimeout(5_000) {
            while (service.getContext(groupId)?.group?.name != "Fresh") delay(20)
        }
        coVerify(exactly = 1) { groupRepo.getGroupById(groupId) }
    }

    @Test
    fun `a non-forced warmup keeps an already hydrated snapshot`() = runBlocking {
        val service = service()
        service.updateContext(groupId) { hydratedContext("Old") }
        stubHydration("Fresh")

        service.warmUpGroup(groupId)

        assertEquals("Old", service.getContext(groupId)?.group?.name)
        coVerify(exactly = 0) { groupRepo.getGroupById(groupId) }
    }

    @Test
    fun `warmUpForUser rehydrates an already hydrated group`() = runBlocking {
        val service = service()
        service.ensureUserSession("admin")
        service.updateContext(groupId) { hydratedContext("Old") }
        coEvery { groupRepo.getGroupsByAdmin("admin") } returns
            Result.success(listOf(Group(id = groupId, name = "Fresh")))
        stubHydration("Fresh")

        service.warmUpForUser("admin")

        withTimeout(5_000) {
            while (service.getContext(groupId)?.group?.name != "Fresh") delay(20)
        }
    }

    @Test
    fun `a failed read keeps the previous section and clears the hydrated flag`() = runBlocking {
        val service = service()
        service.updateContext(groupId) {
            hydratedContext("Old").copy(members = listOf(Member(id = "m1", groupId = groupId)))
        }
        coEvery { groupRepo.getGroupById(groupId) } returns
            Result.success(Group(id = groupId, name = "Old"))
        every { memberRepo.getGroupMembers(groupId) } returns
            flowOf(Result.failure(Exception("cache read failed")))
        coEvery { notificationRepo.syncNotifications(groupId) } returns Result.success(Unit)
        every { notificationRepo.observeNotifications(groupId) } returns flowOf(Result.success(emptyList()))
        every { payoutRepo.observePayouts(groupId) } returns flowOf(Result.success(emptyList()))
        coEvery { actuarialRepo.computeMetrics(groupId) } returns Result.success(ActuarialMetrics())

        service.warmUpGroup(groupId, force = true)

        withTimeout(5_000) {
            while (service.getContext(groupId)?.isFullyHydrated != false) delay(20)
        }
        val cached = service.getContext(groupId)!!
        assertEquals(listOf("m1"), cached.members.map { it.id })
    }

    @Test
    fun `a background warmup cannot double-hydrate while refreshGroup is running`() = runBlocking {
        val service = service()
        service.ensureUserSession("admin")
        service.updateContext(groupId) { hydratedContext("Old") }
        stubHydration("Fresh")
        coEvery { syncRepo.syncGroupData(groupId) } coAnswers {
            // The startup sweep runs while the refresh is mid-flight; it must not start a
            // second hydration for the same group.
            service.warmManagedGroupsInBackground("admin", listOf(Group(id = groupId, name = "Fresh")))
            Result.success(Unit)
        }

        val result = service.refreshGroup(groupId)

        assertTrue(result.isSuccess)
        withTimeout(5_000) {
            while (service.getContext(groupId)?.group?.name != "Fresh") delay(20)
        }
        verify(exactly = 1) { memberRepo.getGroupMembers(groupId) }
    }
}
