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
}
