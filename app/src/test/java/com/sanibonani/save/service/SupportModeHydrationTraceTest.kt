package com.sanibonani.save.service

import com.sanibonani.save.domain.logging.AppLogger
import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.Member
import com.sanibonani.save.domain.repository.credit.LoanRepository
import com.sanibonani.save.domain.repository.equity.MemberContributionRepository
import com.sanibonani.save.domain.repository.group.GroupRepository
import com.sanibonani.save.domain.repository.member.BeneficiaryRepository
import com.sanibonani.save.domain.repository.member.MemberRepository
import com.sanibonani.save.domain.repository.system.NotificationRepository
import com.sanibonani.save.domain.repository.system.SyncRepository
import com.sanibonani.save.domain.utils.PaymentCalculator
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * A Support Mode session that never opens looks the same from the outside whether the hydration is
 * waiting behind a sweep, lost a pillar read, or was refused outright - the screen only ever showed
 * "Entering Support Mode…". These tests pin the trace that says which stage it stopped at.
 *
 * Breadcrumbs are a process-wide ring buffer, so every case names its own group: a trace left by an
 * earlier test can then neither fake a pass nor fake a failure.
 */
class SupportModeHydrationTraceTest {

    private val memberRepo = mockk<MemberRepository>()
    private val groupRepo = mockk<GroupRepository>()
    private val beneficiaryRepo = mockk<BeneficiaryRepository>()
    private val contributionRepo = mockk<MemberContributionRepository>()
    private val notificationRepo = mockk<NotificationRepository>()
    private val loanRepo = mockk<LoanRepository>()
    private val paymentCalculator = mockk<PaymentCalculator>(relaxed = true)
    private val syncRepo = mockk<SyncRepository>()

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

    private fun stubHydration(groupId: String, member: Member, beneficiariesOffline: Boolean) {
        coEvery { memberRepo.getMemberById(member.id!!) } returns Result.success(member)
        coEvery { groupRepo.getGroupById(groupId) } returns Result.success(Group(id = groupId, name = "LIGHT-Trace"))
        coEvery { contributionRepo.getMemberContributions(member.id!!, groupId) } returns
            flowOf(Result.success(emptyList()))
        coEvery { beneficiaryRepo.syncBeneficiaries(member.id!!) } returns
            if (beneficiariesOffline) Result.failure(IOException("offline")) else Result.success(emptyList())
        coEvery { loanRepo.getMemberLoans(member.id!!) } returns flowOf(Result.success(emptyList()))
        coEvery { notificationRepo.syncNotifications(groupId) } returns Result.success(Unit)
        coEvery { notificationRepo.observeNotifications(groupId) } returns flowOf(Result.success(emptyList()))
    }

    private fun breadcrumbWith(vararg fragments: String): Boolean =
        AppLogger.getBreadcrumbs().any { line -> fragments.all(line::contains) }

    @Test
    fun `a hydration that could not read a pillar names the pillar it lost`() = runBlocking {
        val groupId = "group-missed-pillar"
        val member = Member(id = "m-missed-pillar", groupId = groupId, userId = "u-1")
        stubHydration(groupId, member, beneficiariesOffline = true)

        val context = service().impersonateMember(member.id!!, groupId).getOrThrow()

        assertTrue(
            "the snapshot silently keeps the cache, so the lost section has to reach the log",
            breadcrumbWith(groupId, "beneficiaries")
        )
        assertTrue("a portal with a missing pillar is not verified", context.isFullyHydrated.not())
    }

    @Test
    fun `a fully read hydration reports no missing pillar`() = runBlocking {
        val groupId = "group-clean-hydration"
        val member = Member(id = "m-clean-hydration", groupId = groupId, userId = "u-1")
        stubHydration(groupId, member, beneficiariesOffline = false)

        service().impersonateMember(member.id!!, groupId).getOrThrow()

        assertTrue(
            "a clean read must not leave a trace the triage would chase",
            AppLogger.getBreadcrumbs().none { it.contains(groupId) && it.contains("hydration kept the cached") }
        )
    }

    @Test
    fun `a support entry that never reached the dashboard names the member it stopped on`() = runBlocking {
        val groupId = "group-refused-entry"
        coEvery { memberRepo.getMemberById("m-refused-entry") } returns
            Result.failure(IOException("member row unreadable"))

        val result = service().impersonateMember("m-refused-entry", groupId)

        assertTrue(result.isFailure)
        assertTrue(
            "the refusal has to be findable by the member the admin picked",
            breadcrumbWith("m-refused-entry", groupId)
        )
    }

    @Test
    fun `hydration waiting behind a running warmup says what it waited on and how long`() = runBlocking {
        val groupId = "group-slot-handoff"
        val scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
        val coordinator = GroupContextWarmupCoordinator()
        val unwinds = CompletableDeferred<Unit>()

        coordinator.launch(groupId, scope) { unwinds.await() }
        val entry = scope.launch { coordinator.runExclusive(groupId, scope) { } }

        assertTrue(
            "the wait is the stall, and the screen shows nothing about it",
            breadcrumbWith(groupId, "waiting")
        )
        unwinds.complete(Unit)
        entry.join()

        assertTrue(
            "the number that says how long the predecessor held the slot",
            breadcrumbWith(groupId, "unwound after")
        )
    }
}
