package com.sanibonani.save.service

import com.sanibonani.save.domain.model.ActuarialMetrics
import com.sanibonani.save.domain.model.AppNotification
import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.Member
import com.sanibonani.save.domain.model.Money
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test
    fun `a group read that fails on both tiers keeps the cached contexts`() = runBlocking {
        val service = service()
        service.ensureUserSession("admin")
        service.updateContext(groupId) { hydratedContext("Old") }
        coEvery { groupRepo.getGroupsByAdmin("admin") } returns
            Result.failure(Exception("offline"))
        every { groupRepo.observeGroupsByAdmin("admin") } returns
            flowOf(Result.failure(Exception("cache unavailable")))

        service.warmUpForUser("admin")

        assertEquals("Old", service.getContext(groupId)?.group?.name)
    }

    @Test
    fun `a group admin with no groups prunes the cached contexts`() = runBlocking {
        val service = service()
        service.ensureUserSession("admin")
        service.updateContext(groupId) { hydratedContext("Old") }
        coEvery { groupRepo.getGroupsByAdmin("admin") } returns Result.success(emptyList())

        service.warmUpForUser("admin")

        assertNull(service.getContext(groupId))
    }

    @Test
    fun `refreshGroup orders the notification feed newest first`() = runBlocking {
        val service = service()
        val older = AppNotification(
            id = "b-first-issued",
            groupId = groupId,
            message = "older",
            createdAt = "2026-01-01T00:00:00Z"
        )
        val newer = AppNotification(
            id = "a-issued-later",
            groupId = groupId,
            message = "newer",
            createdAt = "2026-06-01T00:00:00Z"
        )
        coEvery { syncRepo.syncGroupData(groupId) } returns Result.success(Unit)
        stubHydration("Fresh")
        every { notificationRepo.observeNotifications(groupId) } returns
            flowOf(Result.success(listOf(older, newer)))

        service.refreshGroup(groupId)

        val cached = service.getContext(groupId)!!
        // The uuid ids are in the opposite order to the timestamps, so an id sort puts "older" first.
        assertEquals(listOf("newer", "older"), cached.notifications.map { it.message })
    }

    @Test
    fun `a warmup that arrives after the reads are underway still gets its own run`() = runBlocking {
        val service = service()
        service.ensureUserSession("admin")
        service.updateContext(groupId) { hydratedContext("Old") }
        stubHydration("Fresh")
        coEvery { syncRepo.syncGroupData(groupId) } returns Result.success(Unit)
        val insideRead = CompletableDeferred<Unit>()
        val holdRead = CompletableDeferred<Unit>()
        every { memberRepo.getGroupMembers(groupId) } answers {
            flow {
                insideRead.complete(Unit)
                holdRead.await()
                emit(Result.success(listOf(Member(id = "m1", groupId = groupId))))
            }
        }

        val refresh = async { service.refreshGroup(groupId) }
        insideRead.await()

        // The sweep arrives while the refresh is mid-read, so its run can no longer answer it.
        service.warmManagedGroupsInBackground("admin", listOf(Group(id = groupId, name = "Swept")))
        holdRead.complete(Unit)
        assertTrue(refresh.await().isSuccess)

        withTimeout(5_000) {
            while (service.getContext(groupId)?.group?.name != "Swept") delay(20)
        }
        verify(exactly = 2) { memberRepo.getGroupMembers(groupId) }
    }

    @Test
    fun `selecting a group re-reads its balance instead of rendering the listing's`() = runBlocking {
        val service = service()
        service.ensureUserSession("admin")
        // The listing that opened this screen cached the group row before its money moved, and
        // the members pillar hydrates from a separate read: the dashboard then showed the
        // listing's R0.00 next to a real member count.
        service.updateContext(groupId) {
            CachedAdminGroupContext(
                group = Group(id = groupId, name = "Old", balance = Money.ZERO),
                isFullyHydrated = true
            )
        }
        stubHydration("Fresh")
        coEvery { groupRepo.getGroupById(groupId) } returns
            Result.success(Group(id = groupId, name = "Fresh", balance = Money("2500.00")))

        service.selectGroup(groupId)

        withTimeout(5_000) {
            while (service.getContext(groupId)?.group?.balance?.toPlainString() != "2500.00") delay(20)
        }
        coVerify(exactly = 1) { groupRepo.getGroupById(groupId) }
    }
}
