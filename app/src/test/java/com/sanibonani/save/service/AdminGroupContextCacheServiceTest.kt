package com.sanibonani.save.service

import com.sanibonani.save.domain.logging.AppLogger
import com.sanibonani.save.domain.model.ActuarialMetrics
import com.sanibonani.save.domain.model.AppNotification
import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.Member
import com.sanibonani.save.domain.model.Money
import com.sanibonani.save.domain.model.PayoutRequest
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

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
    fun `refreshGroup reports the sync that failed even though the cache rebuilt`() = runBlocking {
        // The refresh used to throw the sync Result away, so a pull-to-refresh during an outage
        // returned success and the dashboard kept showing the last cached book as if it were live.
        val service = service()
        service.updateContext(groupId) { hydratedContext("Old") }
        coEvery { syncRepo.syncGroupData(groupId) } returns Result.failure(IOException("offline"))
        stubHydration("Fresh")

        val result = service.refreshGroup(groupId)

        assertTrue("an outage is not a completed refresh", result.isFailure)
        assertEquals(
            "the cached data is still worth showing, as long as the caller knows it did not refresh",
            "Fresh",
            service.getContext(groupId)?.group?.name
        )
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

    // The dashboard's own collectors dedupe on snapshot equality. A wall-clock stamp inside every
    // snapshot defeated that, so each warm-up of the selected group re-subscribed the ledger,
    // bank, payout, loan and claim flows - a Supabase read and a Room write per flow - even when
    // the warm-up had read nothing new. Nothing on this side ever read the stamp.
    @Test
    fun `a warmup that read the same book leaves the snapshot equal`() {
        val service = service()
        service.updateContext(groupId) { hydratedContext("Fresh") }
        val first = service.getContext(groupId)!!

        // Far enough apart that a re-stamp cannot hide inside one millisecond.
        Thread.sleep(5)
        service.updateContext(groupId) { prev -> prev.copy(members = prev.members) }

        assertEquals(first, service.getContext(groupId))
    }

    // One warm-up pass read every group the account administers at the same time, so a platform
    // admin with eight groups presented eight payouts reads, eight member reads and eight metric
    // reads to the same host inside two seconds - the burst the device log shows - and the group
    // actually on screen waited behind all of them in a queue no timeout in the app can see.
    @Test
    fun `warming several groups never reads them all at once`() = runBlocking {
        val service = service()
        val groupIds = (1..4).map { "g$it" }
        val groups = groupIds.map { Group(id = it, name = it) }

        val inFlight = AtomicInteger()
        val peak = AtomicInteger()

        // The increment stays outside the compare-and-set: updateAndGet re-runs its lambda on every
        // retry, so incrementing inside it counts one read twice under contention and reports a peak
        // the budget never actually allowed.
        suspend fun <T> counted(block: suspend () -> T): T = try {
            val inFlightNow = inFlight.incrementAndGet()
            peak.updateAndGet { current -> maxOf(current, inFlightNow) }
            delay(30)
            block()
        } finally {
            inFlight.decrementAndGet()
        }

        groupIds.forEach { gid ->
            every { memberRepo.getGroupMembers(gid) } answers {
                flow {
                    counted { }
                    emit(Result.success(listOf(Member(id = "m-$gid", groupId = gid))))
                }
            }
            coEvery { notificationRepo.syncNotifications(gid) } coAnswers {
                counted { Result.success(Unit) }
            }
            every { notificationRepo.observeNotifications(gid) } returns flowOf(Result.success(emptyList()))
            every { payoutRepo.observePayouts(gid) } answers {
                flow {
                    counted { }
                    emit(Result.success(emptyList()))
                }
            }
            coEvery { actuarialRepo.computeMetrics(gid) } coAnswers {
                counted { Result.success(ActuarialMetrics()) }
            }
        }

        service.warmManagedGroupsInBackground("admin", groups)

        withTimeout(15_000) {
            while (service.contexts.value.values.count { it.members.isNotEmpty() } < groupIds.size) delay(20)
        }
        assertTrue("a bounded warm-up still has to overlap, and it peaked at ${peak.get()}", peak.get() > 1)
        assertTrue("hydration peaked at ${peak.get()} reads at once", peak.get() <= 3)
    }

    @Test
    fun `a refused group row still hydrates the pillars it can read`() = runBlocking {
        // The warm-up stopped at the row: an admin who opened a group while that one read was
        // refused got no members, no notifications and no payouts either, because the whole run was
        // abandoned, and the dashboard's only signal was its own guess that the load had timed out.
        val service = service()
        service.updateContext(groupId) {
            hydratedContext("Old").copy(members = listOf(Member(id = "stale", groupId = groupId)))
        }
        coEvery { groupRepo.getGroupById(groupId) } returns Result.failure(IOException("row read failed"))
        every { memberRepo.getGroupMembers(groupId) } returns
            flowOf(Result.success(listOf(Member(id = "m1", groupId = groupId))))
        coEvery { notificationRepo.syncNotifications(groupId) } returns Result.success(Unit)
        every { notificationRepo.observeNotifications(groupId) } returns flowOf(Result.success(emptyList()))
        every { payoutRepo.observePayouts(groupId) } returns flowOf(Result.success(emptyList()))
        coEvery { actuarialRepo.computeMetrics(groupId) } returns Result.success(ActuarialMetrics())

        service.warmUpGroup(groupId, force = true)

        withTimeout(5_000) {
            while (service.getContext(groupId)?.members?.map { it.id } != listOf("m1")) delay(20)
        }
        val cached = service.getContext(groupId)!!
        assertEquals("the row the snapshot held beats no row at all", "Old", cached.group?.name)
        assertFalse("a group whose row was never re-read is not a verified book", cached.isFullyHydrated)
    }

    @Test
    fun `refreshGroup reports the group row it could not re-read`() = runBlocking {
        // Guard for moving the row read inside the hydration: a refresh that never saw the group's
        // row must not come back as a completed refresh.
        val service = service()
        service.updateContext(groupId) { hydratedContext("Old") }
        coEvery { syncRepo.syncGroupData(groupId) } returns Result.success(Unit)
        coEvery { groupRepo.getGroupById(groupId) } returns Result.failure(IOException("row read failed"))
        every { memberRepo.getGroupMembers(groupId) } returns flowOf(Result.success(emptyList()))
        coEvery { notificationRepo.syncNotifications(groupId) } returns Result.success(Unit)
        every { notificationRepo.observeNotifications(groupId) } returns flowOf(Result.success(emptyList()))
        every { payoutRepo.observePayouts(groupId) } returns flowOf(Result.success(emptyList()))
        coEvery { actuarialRepo.computeMetrics(groupId) } returns Result.success(ActuarialMetrics())

        val result = service.refreshGroup(groupId)

        assertTrue("a refresh that never saw the group's row did not bring it current", result.isFailure)
    }

    // The five legs of one group are independent reads, and the portal's service runs its legs
    // together. This service ran them one after another while it held a whole-sweep permit, so the
    // dashboard paid the sum of five round-trips for a book it could have had in about one.
    @Test
    fun `warming one group reads its independent pillars together`() = runBlocking {
        val service = service()
        val inFlight = AtomicInteger()
        val peak = AtomicInteger()

        suspend fun <T> counted(block: suspend () -> T): T = try {
            val inFlightNow = inFlight.incrementAndGet()
            peak.updateAndGet { current -> maxOf(current, inFlightNow) }
            delay(40)
            block()
        } finally {
            inFlight.decrementAndGet()
        }

        coEvery { groupRepo.getGroupById(groupId) } coAnswers {
            counted { Result.success(Group(id = groupId, name = "Fresh")) }
        }
        every { memberRepo.getGroupMembers(groupId) } answers {
            flow { emit(counted { Result.success(listOf(Member(id = "m1", groupId = groupId))) }) }
        }
        coEvery { notificationRepo.syncNotifications(groupId) } coAnswers {
            counted { Result.success(Unit) }
        }
        every { notificationRepo.observeNotifications(groupId) } answers {
            flow { emit(counted { Result.success(emptyList<AppNotification>()) }) }
        }
        every { payoutRepo.observePayouts(groupId) } answers {
            flow { emit(counted { Result.success(emptyList<PayoutRequest>()) }) }
        }
        coEvery { actuarialRepo.computeMetrics(groupId) } coAnswers {
            counted { Result.success(ActuarialMetrics()) }
        }

        service.warmUpGroup(groupId, force = true)

        withTimeout(10_000) {
            while (service.getContext(groupId)?.isFullyHydrated != true) delay(10)
        }
        assertTrue(
            "a single warm-up kept its own independent reads in a queue, peaking at ${peak.get()}",
            peak.get() > 1
        )
        assertTrue("hydration peaked at ${peak.get()} reads at once", peak.get() <= 3)
    }

    // The snapshot keeps whatever it held, so a pillar that did not answer is invisible on screen:
    // the admin reads cached payouts next to fresh members and nothing records which part of the
    // book is stale. The portal's service names them; this one only ever logged a refused group row.
    @Test
    fun `a pillar the warm-up could not read is named in the log`() = runBlocking {
        val service = service()
        stubHydration("Fresh")
        every { payoutRepo.observePayouts(groupId) } returns
            flowOf(Result.failure(IOException("payouts unavailable")))

        service.warmUpGroup(groupId, force = true)

        withTimeout(5_000) { while (service.getContext(groupId) == null) delay(20) }
        assertFalse(service.getContext(groupId)!!.isFullyHydrated)
        assertTrue(
            "the refusal has to say which pillar stayed cached",
            AppLogger.getBreadcrumbs().any { it.contains("kept the cached payouts") }
        )
    }
}
