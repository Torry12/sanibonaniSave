package com.sanibonani.save.service

import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.Loan
import com.sanibonani.save.domain.model.Member
import com.sanibonani.save.domain.model.Money
import com.sanibonani.save.domain.repository.credit.LoanRepository
import com.sanibonani.save.domain.repository.equity.MemberContributionRepository
import com.sanibonani.save.domain.repository.group.GroupRepository
import com.sanibonani.save.domain.repository.member.BeneficiaryRepository
import com.sanibonani.save.domain.repository.member.MemberRepository
import com.sanibonani.save.domain.repository.system.NotificationRepository
import com.sanibonani.save.domain.repository.system.SyncRepository
import com.sanibonani.save.domain.utils.PaymentCalculator
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

class MemberGroupContextCacheServiceTest {

    private val groupId = "group-1"

    private val memberRepo = mockk<MemberRepository>()
    private val groupRepo = mockk<GroupRepository>()
    private val beneficiaryRepo = mockk<BeneficiaryRepository>()
    private val contributionRepo = mockk<MemberContributionRepository>()
    private val notificationRepo = mockk<NotificationRepository>()
    private val loanRepo = mockk<LoanRepository>()
    private val paymentCalculator = mockk<PaymentCalculator>(relaxed = true)
    private val syncRepo = mockk<SyncRepository>()

    private val member = Member(id = "m1", groupId = groupId, userId = "u1")

    private fun service() = MemberGroupContextCacheService(
        memberRepo = memberRepo,
        groupRepo = groupRepo,
        beneficiaryRepo = beneficiaryRepo,
        contributionRepo = contributionRepo,
        notificationRepo = notificationRepo,
        loanRepo = loanRepo,
        paymentCalculator = paymentCalculator,
        syncRepo = syncRepo
    )

    private fun stubHydration(groupName: String, hydratedMember: Member = member) {
        coEvery { groupRepo.getGroupById(groupId) } returns
            Result.success(Group(id = groupId, name = groupName))
        hydratedMember.userId?.let { userId ->
            coEvery { memberRepo.getMemberByUserId(userId, groupId) } returns
                Result.success(hydratedMember)
        }
        coEvery { memberRepo.getMemberById(hydratedMember.id!!) } returns Result.success(hydratedMember)
        every { contributionRepo.getMemberContributions(hydratedMember.id!!, groupId) } returns
            flowOf(Result.success(emptyList()))
        coEvery { beneficiaryRepo.syncBeneficiaries(hydratedMember.id!!) } returns
            Result.success(emptyList())
        every { loanRepo.getMemberLoans(hydratedMember.id!!) } returns
            flowOf(Result.success(emptyList()))
        coEvery { notificationRepo.syncNotifications(groupId) } returns Result.success(Unit)
        every { notificationRepo.observeNotifications(groupId) } returns flowOf(Result.success(emptyList()))
    }

    private fun hydratedContext(name: String) = CachedGroupContext(
        member = member,
        group = Group(id = groupId, name = name),
        isFullyHydrated = true
    )

    @Test
    fun `refreshGroup syncs and rebuilds an already hydrated context`() = runBlocking {
        val service = service()
        service.updateContext(groupId) { hydratedContext("Old") }

        var synced = false
        coEvery { syncRepo.syncGroupData(groupId) } coAnswers {
            synced = true
            Result.success(Unit)
        }
        stubHydration("Fresh")
        every { contributionRepo.getMemberContributions("m1", groupId) } answers {
            assertTrue("contributions were read before the sync finished", synced)
            flowOf(Result.success(emptyList()))
        }

        val result = service.refreshGroup("u1", groupId)

        assertTrue(result.isSuccess)
        val cached = service.getContext(groupId)!!
        assertEquals("Fresh", cached.group?.name)
        assertEquals("m1", cached.member?.id)
        assertTrue(cached.isFullyHydrated)
        coVerify(exactly = 1) { syncRepo.syncGroupData(groupId) }
    }

    @Test
    fun `refreshGroup reports the sync that failed even though the cache rebuilt`() = runBlocking {
        val service = service()
        service.updateContext(groupId) { hydratedContext("Old") }
        coEvery { syncRepo.syncGroupData(groupId) } returns Result.failure(IOException("offline"))
        stubHydration("Fresh")

        val result = service.refreshGroup("u1", groupId)

        assertTrue("an outage is not a completed refresh", result.isFailure)
        assertEquals(
            "the cached portal is still worth showing, as long as the caller knows it did not refresh",
            "Fresh",
            service.getContext(groupId)?.group?.name
        )
    }

    @Test
    fun `refreshImpersonatedMember refuses to present a portal that never refreshed`() = runBlocking {
        // Support Mode is where an admin judges another member's money, so a sync that pulled
        // nothing has to reach the operator instead of showing a stale book as live.
        val service = service()
        val unlinked = Member(id = "m-target", groupId = groupId, userId = null)
        coEvery { syncRepo.syncGroupData(groupId) } returns Result.failure(IOException("offline"))
        stubHydration("Group", hydratedMember = unlinked)

        val result = service.refreshImpersonatedMember("m-target", groupId)

        assertTrue(result.isFailure)
    }

    @Test
    fun `refreshImpersonatedMember refreshes the impersonated member`() = runBlocking {
        val service = service()
        val unlinked = Member(id = "m-target", groupId = groupId, userId = null)
        coEvery { syncRepo.syncGroupData(groupId) } returns Result.success(Unit)
        stubHydration("Group", hydratedMember = unlinked)

        val result = service.refreshImpersonatedMember("m-target", groupId)

        assertTrue(result.isSuccess)
        assertEquals("m-target", service.getContext(groupId)?.member?.id)
        coVerify(exactly = 1) { syncRepo.syncGroupData(groupId) }
        coVerify(exactly = 1) { memberRepo.getMemberById("m-target") }
        coVerify(exactly = 0) { memberRepo.getMemberByUserId(any(), any()) }
    }

    @Test
    fun `impersonateMember keeps the selected member when its user id resolves elsewhere`() = runBlocking {
        val service = service()
        val selected = Member(id = "m-target", groupId = groupId, userId = "u1")
        val operatorMembership = Member(id = "m-operator", groupId = groupId, userId = "u1")
        stubHydration("Group", hydratedMember = selected)
        coEvery { memberRepo.getMemberById("m-target") } returns Result.success(selected)
        coEvery { memberRepo.getMemberByUserId("u1", groupId) } returns Result.success(operatorMembership)

        val result = service.impersonateMember("m-target", groupId)

        assertTrue(result.isSuccess)
        assertEquals("m-target", result.getOrThrow().member?.id)
        coVerify(exactly = 0) { memberRepo.getMemberByUserId(any(), any()) }
    }

    @Test
    fun `warmUpForUser rehydrates an already hydrated context`() = runBlocking {
        val service = service()
        service.ensureUserSession("u1")
        service.updateContext(groupId) { hydratedContext("Old") }
        coEvery { memberRepo.getMemberships("u1") } returns Result.success(listOf(member))
        stubHydration("Fresh")

        service.warmUpForUser("u1")

        withTimeout(5_000) {
            while (service.getContext(groupId)?.group?.name != "Fresh") delay(20)
        }
    }

    @Test
    fun `a failed loan read keeps the previous loans and clears the hydrated flag`() = runBlocking {
        val service = service()
        service.ensureUserSession("u1")
        service.updateContext(groupId) {
            hydratedContext("Old").copy(
                loans = listOf(Loan(id = "l1", memberId = "m1", groupId = groupId, amount = Money("100.0")))
            )
        }
        stubHydration("Old")
        coEvery { syncRepo.syncGroupData(groupId) } returns Result.success(Unit)
        every { loanRepo.getMemberLoans("m1") } returns
            flowOf(Result.failure(Exception("cache read failed")))

        val result = service.refreshGroup("u1", groupId)

        assertTrue(result.isSuccess)
        val cached = service.getContext(groupId)!!
        assertEquals(listOf("l1"), cached.loans.map { it.id })
        assertFalse(cached.isFullyHydrated)
    }

    @Test
    fun `a background sweep cannot double-hydrate while refreshGroup is running`() = runBlocking {
        val service = service()
        service.ensureUserSession("u1")
        service.updateContext(groupId) { hydratedContext("Old") }
        stubHydration("Fresh")
        coEvery { syncRepo.syncGroupData(groupId) } coAnswers {
            // The startup sweep runs while the refresh is mid-flight; it must not start a
            // second hydration for the same group.
            service.warmMembershipsInBackground("u1", listOf(member))
            Result.success(Unit)
        }

        val result = service.refreshGroup("u1", groupId)

        assertTrue(result.isSuccess)
        withTimeout(5_000) {
            while (service.getContext(groupId)?.group?.name != "Fresh") delay(20)
        }
        verify(exactly = 1) { contributionRepo.getMemberContributions("m1", groupId) }
    }

    @Test
    fun `a membership read that fails on both tiers keeps the cached contexts`() = runBlocking {
        val service = service()
        service.ensureUserSession("u1")
        service.updateContext(groupId) { hydratedContext("Old") }
        coEvery { memberRepo.getMemberships("u1") } returns Result.failure(Exception("offline"))
        every { memberRepo.observeMemberships("u1") } returns
            flowOf(Result.failure(Exception("cache unavailable")))

        service.warmUpForUser("u1")

        assertEquals("Old", service.getContext(groupId)?.group?.name)
    }

    @Test
    fun `a member with no memberships prunes the cached contexts`() = runBlocking {
        val service = service()
        service.ensureUserSession("u1")
        service.updateContext(groupId) { hydratedContext("Old") }
        coEvery { memberRepo.getMemberships("u1") } returns Result.success(emptyList())

        service.warmUpForUser("u1")

        assertNull(service.getContext(groupId))
    }

    @Test
    fun `a warmup that arrives after the reads are underway still gets its own run`() = runBlocking {
        val service = service()
        service.ensureUserSession("u1")
        service.updateContext(groupId) { hydratedContext("Old") }
        stubHydration("Fresh")
        coEvery { groupRepo.getGroupById(groupId) } returnsMany listOf(
            Result.success(Group(id = groupId, name = "Fresh")),
            Result.success(Group(id = groupId, name = "Swept"))
        )
        coEvery { syncRepo.syncGroupData(groupId) } returns Result.success(Unit)
        val insideRead = CompletableDeferred<Unit>()
        val holdRead = CompletableDeferred<Unit>()
        every { contributionRepo.getMemberContributions("m1", groupId) } answers {
            flow {
                insideRead.complete(Unit)
                holdRead.await()
                emit(Result.success(emptyList()))
            }
        }

        val refresh = async { service.refreshGroup("u1", groupId) }
        insideRead.await()

        // The sweep arrives while the refresh is mid-read, so its run can no longer answer it.
        service.warmMembershipsInBackground("u1", listOf(member))
        holdRead.complete(Unit)
        assertTrue(refresh.await().isSuccess)

        withTimeout(5_000) {
            while (service.getContext(groupId)?.group?.name != "Swept") delay(20)
        }
        verify(exactly = 2) { contributionRepo.getMemberContributions("m1", groupId) }
    }

    // Every warm-up stamped the wall clock into the snapshot, so the StateFlow's own equality
    // dedupe never fired and a pass that read exactly the same book still reached the portal as
    // new data - which answered by re-subscribing all eight of its observation flows, each with a
    // Supabase read and a Room write behind it.
    @Test
    fun `a warmup that read the same book leaves the snapshot equal`() {
        val service = service()
        service.updateContext(groupId) { hydratedContext("Fresh") }
        val first = service.getContext(groupId)!!

        // Far enough apart that a re-stamp cannot hide inside one millisecond.
        Thread.sleep(5)
        service.updateContext(groupId) { hydratedContext("Fresh") }

        assertEquals(first, service.getContext(groupId))
    }

    // The reducer a warm-up runs is `prev.copy(...)`, so an all-cache-hit pass hands back the same
    // content it was given. That has to be the identity case, not a new snapshot.
    @Test
    fun `a warmup that changed nothing in the previous snapshot leaves it equal`() {
        val service = service()
        service.updateContext(groupId) { hydratedContext("Fresh") }
        val first = service.getContext(groupId)!!

        Thread.sleep(5)
        service.updateContext(groupId) { prev -> prev.copy(member = member) }

        assertEquals(first, service.getContext(groupId))
    }

    @Test
    fun `a snapshot whose book really moved carries a fresh stamp`() {
        val service = service()
        service.updateContext(groupId) { hydratedContext("Old") }
        val first = service.getContext(groupId)!!

        Thread.sleep(5)
        service.updateContext(groupId) { hydratedContext("New") }
        val second = service.getContext(groupId)!!

        // The portal renders lastUpdatedMillis as the group's "synced" time, so a pass that moved
        // the book still has to advance it.
        assertEquals("New", second.group?.name)
        assertTrue(second.lastUpdatedMillis > first.lastUpdatedMillis)
    }

    // A warm-up read every pillar of every membership at once: four memberships measured sixteen
    // reads in flight together, out of the twenty-four the pass presents to one host. The calls past
    // its per-host dispatch cap waited in a queue that no timeout in the app can see, which is how
    // the portal the user was actually looking at sat behind a hydration of groups they had not
    // opened.
    @Test
    fun `warming several memberships never reads them all at once`() = runBlocking {
        val service = service()
        val groupIds = listOf("g1", "g2", "g3", "g4")
        val memberships = groupIds.map { Member(id = "m-$it", groupId = it, userId = "u1") }

        val inFlight = AtomicInteger()
        val peak = AtomicInteger()

        // The increment stays outside the compare-and-set: updateAndGet re-runs its lambda on every
        // retry, so incrementing inside it counts one leg twice under contention and reports a peak
        // the budget never actually allowed.
        suspend fun <T> counted(block: suspend () -> T): T = try {
            val inFlightNow = inFlight.incrementAndGet()
            peak.updateAndGet { current -> maxOf(current, inFlightNow) }
            block()
        } finally {
            inFlight.decrementAndGet()
        }

        groupIds.forEach { gid ->
            val memberId = "m-$gid"
            coEvery { groupRepo.getGroupById(gid) } coAnswers { counted { delay(30); Result.success(Group(id = gid, name = gid)) } }
            coEvery { memberRepo.getMemberByUserId("u1", gid) } coAnswers { counted { delay(30); Result.success(memberships.first { it.groupId == gid }) } }
            every { contributionRepo.getMemberContributions(memberId, gid) } answers {
                flow { counted { delay(30) }; emit(Result.success(emptyList())) }
            }
            coEvery { beneficiaryRepo.syncBeneficiaries(memberId) } coAnswers { counted { delay(30); Result.success(emptyList()) } }
            every { loanRepo.getMemberLoans(memberId) } answers {
                flow { counted { delay(30) }; emit(Result.success(emptyList())) }
            }
            coEvery { notificationRepo.syncNotifications(gid) } coAnswers { counted { delay(30); Result.success(Unit) } }
            every { notificationRepo.observeNotifications(gid) } answers {
                flow { counted { delay(30) }; emit(Result.success(emptyList())) }
            }
        }

        service.warmMembershipsInBackground("u1", memberships)

        withTimeout(15_000) {
            while (service.contexts.value.values.count { it.group?.id != null } < groupIds.size) delay(20)
        }
        assertTrue(
            "a bounded warm-up still has to overlap, and it peaked at ${peak.get()}",
            peak.get() > 1
        )
        assertTrue("hydration peaked at ${peak.get()} reads at once", peak.get() <= HYDRATION_READS_AT_ONCE)
    }

    @Test
    fun `invalidateForDatabaseChange drops every snapshot read from the replaced tier`() = runBlocking {
        // Switching the developer database points the app at a different Supabase project and a
        // different SQLite file, so a portal built from the old one is not a fallback - it is money
        // from a book this member may not even have on the tier now in front.
        val service = service()
        coEvery { memberRepo.getMemberships("u1") } returns Result.success(listOf(member))
        stubHydration("Old tier")
        service.warmUpForUser("u1")
        withTimeout(5_000) {
            while (service.getContext(groupId)?.group?.name != "Old tier") delay(20)
        }

        service.invalidateForDatabaseChange()

        assertNull(service.getContext(groupId))
        assertTrue(service.contexts.value.isEmpty())
    }

    @Test
    fun `invalidateForDatabaseChange abandons a warm-up still reading the tier being replaced`() = runBlocking {
        val service = service()
        val readStarted = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        coEvery { memberRepo.getMemberships("u1") } returns Result.success(listOf(member))
        stubHydration("Old tier")
        // The pillars are read after the group row, so the cancelled run has a suspension left to
        // observe before it could write - which makes this a verdict about the cancellation.
        every { contributionRepo.getMemberContributions("m1", groupId) } returns
            flow { delay(50); emit(Result.success(emptyList())) }
        coEvery { groupRepo.getGroupById(groupId) } coAnswers {
            readStarted.complete(Unit)
            release.await()
            Result.success(Group(id = groupId, name = "Old tier"))
        }

        service.warmMembershipsInBackground("u1", listOf(member))
        withTimeout(5_000) { readStarted.await() }

        service.invalidateForDatabaseChange()
        release.complete(Unit)
        delay(500)

        assertNull(
            "a snapshot half-built from the database that has just been replaced must not land after the switch",
            service.getContext(groupId)
        )
    }

    private companion object {
        /** What one bounded warm-up is allowed to have in flight, for every group together. */
        const val HYDRATION_READS_AT_ONCE = 3
    }
}
