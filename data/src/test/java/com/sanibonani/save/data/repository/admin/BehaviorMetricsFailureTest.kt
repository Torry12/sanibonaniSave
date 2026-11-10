package com.sanibonani.save.data.repository.admin

import com.sanibonani.save.data.local.MemberBehaviorTrackDao
import com.sanibonani.save.data.local.SanibonaniDatabase
import com.sanibonani.save.domain.model.Contribution
import com.sanibonani.save.domain.model.ContributionStatus
import com.sanibonani.save.domain.model.Member
import com.sanibonani.save.domain.model.MemberStatus
import com.sanibonani.save.domain.model.Money
import com.sanibonani.save.domain.repository.credit.LoanRepository
import com.sanibonani.save.domain.repository.equity.MemberContributionRepository
import com.sanibonani.save.domain.repository.member.MemberRepository
import io.github.jan.supabase.SupabaseClient
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * A behaviour track is the evidence an admin acts on: contributions recorded, amounts paid, loans
 * outstanding. A history read that never reached the server has no amounts in it, so treating it as
 * an empty one writes a member off as someone who has never contributed and parks R0.00 in the row -
 * and the row is an upsert, so it overwrites the history the member actually has.
 *
 * That is why both books are read with the one-shot `sync*`/`Sync` ports rather than the observed
 * flows: the first emission of those flows is the Room cache, empty on a device that has never synced
 * this member, and it arrives whether or not the server ever answered.
 */
class BehaviorMetricsFailureTest {

    private val dao = mockk<MemberBehaviorTrackDao>(relaxed = true)
    private val db = mockk<SanibonaniDatabase>()
    private val memberRepository = mockk<MemberRepository>(relaxed = true)
    private val contributionRepository = mockk<MemberContributionRepository>(relaxed = true)
    private val loanRepository = mockk<LoanRepository>(relaxed = true)

    private lateinit var repository: BehaviorTrackingRepositoryImpl

    private val member = Member(
        id = "m1",
        groupId = "g1",
        fullName = "Alpha Member",
        idNumber = "9001015000008",
        status = MemberStatus.ACTIVE
    )

    @Before
    fun setUp() {
        every { db.memberBehaviorTrackDao() } returns dao
        coEvery { memberRepository.getMemberById("m1") } returns Result.success(member)
        coEvery { dao.getByMemberId("m1") } returns null
        coEvery { loanRepository.getMemberLoansSync("m1") } returns Result.success(emptyList())
        coEvery { contributionRepository.syncMemberContributions("m1", "g1") } returns
            Result.success(emptyList())

        repository = BehaviorTrackingRepositoryImpl(
            supabaseProvider = mockk<javax.inject.Provider<SupabaseClient>>(relaxed = true),
            dbProvider = { db },
            memberRepository = memberRepository,
            contributionRepository = contributionRepository,
            loanRepository = loanRepository
        )
    }

    @Test
    fun `an unreadable contribution history does not record a member as having contributed nothing`() = runBlocking {
        coEvery { contributionRepository.syncMemberContributions("m1", "g1") } returns
            Result.failure(IOException("offline"))

        val outcome = repository.calculateAndUpdateMemberBehavior("m1", "g1")

        assertTrue("the caller must be told the metrics were not computed", outcome.isFailure)
        coVerify(exactly = 0) { dao.upsert(any()) }
        // The observed flow is the read that would have answered here: it hands back the Room cache and
        // its first emission is empty on a device that has never synced this member, which is the row
        // that used to be written over a member's real history.
        coVerify(exactly = 0) { contributionRepository.getMemberContributions(any(), any()) }
    }

    @Test
    fun `an unreadable loan history does not record a member as having no loans`() = runBlocking {
        coEvery { loanRepository.getMemberLoansSync("m1") } returns Result.failure(IOException("offline"))

        val outcome = repository.calculateAndUpdateMemberBehavior("m1", "g1")

        assertTrue(outcome.isFailure)
        coVerify(exactly = 0) { dao.upsert(any()) }
        coVerify(exactly = 0) { loanRepository.getMemberLoans(any()) }
    }

    @Test
    fun `a track is written from the rows the server answered`() = runBlocking {
        val paid = Contribution(
            id = "c-1",
            memberId = "m1",
            groupId = "g1",
            amount = Money("200.00"),
            status = ContributionStatus.PAID
        )
        coEvery { contributionRepository.syncMemberContributions("m1", "g1") } returns Result.success(listOf(paid))

        val outcome = repository.calculateAndUpdateMemberBehavior("m1", "g1")

        assertTrue(outcome.isSuccess)
        assertEquals(1, outcome.getOrNull()?.totalContributions)
        assertEquals(Money("200.00"), outcome.getOrNull()?.totalAmountContributed)
        coVerify(exactly = 1) { dao.upsert(any()) }
    }
}
