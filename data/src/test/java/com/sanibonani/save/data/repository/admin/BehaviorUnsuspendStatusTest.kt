package com.sanibonani.save.data.repository.admin

import com.sanibonani.save.data.local.MemberBehaviorTrackDao
import com.sanibonani.save.data.local.MemberBehaviorTrackEntity
import com.sanibonani.save.data.local.SanibonaniDatabase
import com.sanibonani.save.data.local.toEntity
import com.sanibonani.save.domain.model.BehaviorStatus
import com.sanibonani.save.domain.model.MemberBehaviorTrack
import com.sanibonani.save.domain.repository.credit.LoanRepository
import com.sanibonani.save.domain.repository.equity.MemberContributionRepository
import com.sanibonani.save.domain.repository.member.MemberRepository
import io.github.jan.supabase.SupabaseClient
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.coVerify
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * `member_behavior_track.member_status` is the ground-truth label the finetuning dataset trains on,
 * and it is what the behaviour screen shows. Clearing `is_suspended` while leaving the label at
 * SUSPENDED kept training a member who had been reinstated as a suspended one.
 */
class BehaviorUnsuspendStatusTest {

    private val dao = mockk<MemberBehaviorTrackDao>(relaxed = true)
    private val db = mockk<SanibonaniDatabase>()
    private val memberRepository = mockk<MemberRepository>(relaxed = true)
    private val contributionRepository = mockk<MemberContributionRepository>(relaxed = true)
    private val loanRepository = mockk<LoanRepository>(relaxed = true)

    private lateinit var repository: BehaviorTrackingRepositoryImpl

    @Before
    fun setUp() {
        every { db.memberBehaviorTrackDao() } returns dao
        repository = BehaviorTrackingRepositoryImpl(
            supabaseProvider = mockk<javax.inject.Provider<SupabaseClient>>(relaxed = true),
            dbProvider = { db },
            memberRepository = memberRepository,
            contributionRepository = contributionRepository,
            loanRepository = loanRepository
        )
    }

    private fun suspendedTrack(behaviorScore: Double, fraudScore: Double) = MemberBehaviorTrack(
        id = "track-1",
        memberId = "m1",
        memberIdNumber = "9001015000008",
        groupId = "g1",
        memberStatus = BehaviorStatus.SUSPENDED,
        behaviorScore = behaviorScore,
        fraudScore = fraudScore,
        isSuspended = true,
        suspensionReason = "Duplicate contribution claims"
    )

    private fun unsuspend(behaviorScore: Double, fraudScore: Double): MemberBehaviorTrackEntity {
        coEvery { dao.getByMemberId("m1") } returns suspendedTrack(behaviorScore, fraudScore).toEntity()
        val captured = slot<MemberBehaviorTrackEntity>()

        val outcome = runBlocking { repository.unsuspendMember("m1") }

        assertTrue("the reinstated row must be written", outcome.isSuccess)
        coVerify { dao.upsert(capture(captured)) }
        return captured.captured
    }

    @Test
    fun `unsuspending clears the suspension marker`() {
        val written = unsuspend(behaviorScore = 92.0, fraudScore = 10.0)

        assertFalse(written.isSuspended)
        assertNull(written.suspensionReason)
    }

    @Test
    fun `a reinstated member is re-classified from the scores instead of keeping the suspended label`() {
        val written = unsuspend(behaviorScore = 92.0, fraudScore = 10.0)

        assertEquals(BehaviorStatus.EXCELLENT, written.memberStatus)
    }

    @Test
    fun `a reinstated member with weak scores is classified poor, not suspended`() {
        val written = unsuspend(behaviorScore = 40.0, fraudScore = 60.0)

        assertEquals(BehaviorStatus.POOR, written.memberStatus)
    }

    @Test
    fun `suspending still labels the member as suspended`() = runBlocking {
        coEvery { dao.getByMemberId("m1") } returns suspendedTrack(92.0, 10.0).toEntity()
        val captured = slot<MemberBehaviorTrackEntity>()

        repository.suspendMember("m1", "Loan default")
        coVerify { dao.upsert(capture(captured)) }

        assertEquals(BehaviorStatus.SUSPENDED, captured.captured.memberStatus)
    }
}
