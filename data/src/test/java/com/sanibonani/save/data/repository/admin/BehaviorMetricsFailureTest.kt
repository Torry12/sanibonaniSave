package com.sanibonani.save.data.repository.admin

import com.sanibonani.save.data.local.MemberBehaviorTrackDao
import com.sanibonani.save.data.local.SanibonaniDatabase
import com.sanibonani.save.domain.model.Member
import com.sanibonani.save.domain.model.MemberStatus
import com.sanibonani.save.domain.repository.credit.LoanRepository
import com.sanibonani.save.domain.repository.equity.MemberContributionRepository
import com.sanibonani.save.domain.repository.member.MemberRepository
import io.github.jan.supabase.SupabaseClient
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * A behaviour track is the evidence an admin acts on: contributions recorded, amounts paid, loans
 * outstanding. A history read that never reached the server has no amounts in it, so treating it as
 * an empty one writes a member off as someone who has never contributed and parks R0.00 in the row.
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
        coEvery { loanRepository.getMemberLoans("m1") } returns flowOf(Result.success(emptyList()))
        coEvery { contributionRepository.getMemberContributions("m1", "g1") } returns
            flowOf(Result.success(emptyList()))

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
        coEvery { contributionRepository.getMemberContributions("m1", "g1") } returns
            flowOf(Result.failure(IOException("offline")))

        val outcome = repository.calculateAndUpdateMemberBehavior("m1", "g1")

        assertTrue("the caller must be told the metrics were not computed", outcome.isFailure)
        coVerify(exactly = 0) { dao.upsert(any()) }
    }

    @Test
    fun `an unreadable loan history does not record a member as having no loans`() = runBlocking {
        coEvery { loanRepository.getMemberLoans("m1") } returns flowOf(Result.failure(IOException("offline")))

        val outcome = repository.calculateAndUpdateMemberBehavior("m1", "g1")

        assertTrue(outcome.isFailure)
        coVerify(exactly = 0) { dao.upsert(any()) }
    }
}
