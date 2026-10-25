package com.sanibonani.save.domain.usecase.member

import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.admin.BehaviorTrackingRepository
import com.sanibonani.save.domain.repository.auth.SupabaseRepository
import com.sanibonani.save.domain.repository.equity.GroupFinancialRepository
import com.sanibonani.save.domain.repository.group.GroupRepository
import com.sanibonani.save.domain.repository.member.BeneficiaryClaimRepository
import com.sanibonani.save.domain.repository.member.MemberRepository
import com.sanibonani.save.domain.service.FinancialSecurityService
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

class ProcessBurialClaimUseCaseTest {

    private val claimRepository: BeneficiaryClaimRepository = mockk()
    private val groupRepository: GroupRepository = mockk()
    private val memberRepository: MemberRepository = mockk()
    private val supabaseRepository: SupabaseRepository = mockk()
    private val financialRepository: GroupFinancialRepository = mockk()
    private val behaviorRepository: BehaviorTrackingRepository = mockk()

    private lateinit var useCase: ProcessBurialClaimUseCase

    private val group = Group(
        id = "g1",
        name = "Saru Burial Society",
        type = GroupType.BURIAL_SOCIETY,
        burialClaimAmount = Money("10000.00"),
        balance = Money("50000.00")
    )
    private val member = Member(
        id = "m1",
        groupId = "g1",
        fullName = "Nomsa Dlamini",
        status = MemberStatus.ACTIVE,
        joinedAt = "2020-01-01T00:00:00"
    )

    @Before
    fun setUp() {
        useCase = ProcessBurialClaimUseCase(
            claimRepository,
            groupRepository,
            memberRepository,
            FinancialSecurityService(supabaseRepository, financialRepository),
            ValidateBurialClaimEligibilityUseCase(),
            behaviorRepository
        )
        coEvery { behaviorRepository.getMemberBehavior(any()) } returns Result.success(null)
        coEvery { groupRepository.getGroupById("g1") } returns Result.success(group)
        coEvery { memberRepository.getMemberById("m1") } returns Result.success(member)
        coEvery { claimRepository.updateClaimStatus(any(), any(), any(), any(), any()) } returns Result.success(Unit)
        coEvery { claimRepository.payClaimAtomic(any(), any(), any()) } returns Result.success(Unit)
        every { supabaseRepository.isMfaVerified } returns true
        coEvery { financialRepository.getVerifiedBalance("g1") } returns Result.success(Money("50000.00"))
    }

    @Test
    fun `a rejected claim cannot be paid afterwards`() = runBlocking {
        coEvery { claimRepository.getClaimById("c1") } returns
            Result.success(claim(BeneficiaryClaimStatus.REJECTED))

        val result = useCase("c1", BeneficiaryClaimStatus.PAID, "admin1")

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { claimRepository.payClaimAtomic(any(), any(), any()) }
        coVerify(exactly = 0) { claimRepository.updateClaimStatus(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `an already-paid claim is never paid twice`() = runBlocking {
        coEvery { claimRepository.getClaimById("c1") } returns
            Result.success(claim(BeneficiaryClaimStatus.PAID))

        val result = useCase("c1", BeneficiaryClaimStatus.PAID, "admin1")

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { claimRepository.payClaimAtomic(any(), any(), any()) }
    }

    @Test
    fun `a cancelled claim cannot be resurrected as approved`() = runBlocking {
        coEvery { claimRepository.getClaimById("c1") } returns
            Result.success(claim(BeneficiaryClaimStatus.CANCELLED))

        val result = useCase("c1", BeneficiaryClaimStatus.APPROVED, "admin1")

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { claimRepository.updateClaimStatus(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `a claim decision with no signed-in reviewer is refused`() = runBlocking {
        coEvery { claimRepository.getClaimById("c1") } returns
            Result.success(claim(BeneficiaryClaimStatus.APPROVED))

        val result = useCase("c1", BeneficiaryClaimStatus.PAID, "   ")

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { claimRepository.payClaimAtomic(any(), any(), any()) }
    }

    @Test
    fun `an escalated claim can be approved by the platform`() = runBlocking {
        coEvery { claimRepository.getClaimById("c1") } returns
            Result.success(claim(BeneficiaryClaimStatus.ESCALATED))

        val result = useCase("c1", BeneficiaryClaimStatus.APPROVED, "platform-admin")

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) {
            claimRepository.updateClaimStatus("c1", BeneficiaryClaimStatus.APPROVED, "platform-admin", null, null)
        }
    }

    @Test
    fun `a group admin can still approve and pay a submitted claim in one step`() = runBlocking {
        coEvery { claimRepository.getClaimById("c1") } returns
            Result.success(claim(BeneficiaryClaimStatus.SUBMITTED))

        val result = useCase("c1", BeneficiaryClaimStatus.PAID, "group-admin")

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { claimRepository.payClaimAtomic("c1", "group-admin", null) }
        coVerify(exactly = 0) { claimRepository.updateClaimStatus(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `the persisted claim drives the returned result`() = runBlocking {
        coEvery { claimRepository.getClaimById("c1") } returnsMany listOf(
            Result.success(claim(BeneficiaryClaimStatus.UNDER_REVIEW)),
            Result.success(claim(BeneficiaryClaimStatus.APPROVED))
        )

        val result = useCase("c1", BeneficiaryClaimStatus.APPROVED, "admin1")

        assertTrue(result.isSuccess)
        assertEquals(BeneficiaryClaimStatus.APPROVED, result.getOrThrow().status)
        assertEquals("c1", result.getOrThrow().id)
    }

    @Test
    fun `a claim is blocked while the member has outstanding arrears`() = runBlocking {
        // The eligibility rule accepts an overdue amount, but the caller never supplied one, so a
        // member in arrears could still have the funeral cover paid out. The claim decision must
        // read the member's track and pass it through.
        coEvery { claimRepository.getClaimById("c1") } returns
            Result.success(claim(BeneficiaryClaimStatus.UNDER_REVIEW))
        coEvery { behaviorRepository.getMemberBehavior("m1") } returns Result.success(
            MemberBehaviorTrack(
                memberId = "m1",
                groupId = "g1",
                totalOutstandingAmount = Money("250.00")
            )
        )

        val result = useCase("c1", BeneficiaryClaimStatus.APPROVED, "admin1")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("Arrears"))
        coVerify(exactly = 0) { claimRepository.updateClaimStatus(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `a claim payment is blocked while arrears stand`() = runBlocking {
        coEvery { claimRepository.getClaimById("c1") } returns
            Result.success(claim(BeneficiaryClaimStatus.APPROVED))
        coEvery { behaviorRepository.getMemberBehavior("m1") } returns Result.success(
            MemberBehaviorTrack(
                memberId = "m1",
                groupId = "g1",
                totalOutstandingAmount = Money("250.00")
            )
        )

        val result = useCase("c1", BeneficiaryClaimStatus.PAID, "admin1")

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { claimRepository.payClaimAtomic(any(), any(), any()) }
    }

    @Test
    fun `an unreadable behavior history blocks the decision`() = runBlocking {
        // Approving blind would repeat the original defect: a failed read is not an absence of arrears.
        coEvery { claimRepository.getClaimById("c1") } returns
            Result.success(claim(BeneficiaryClaimStatus.UNDER_REVIEW))
        coEvery { behaviorRepository.getMemberBehavior("m1") } returns
            Result.failure(IOException("network down"))

        val result = useCase("c1", BeneficiaryClaimStatus.APPROVED, "admin1")

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { claimRepository.updateClaimStatus(any(), any(), any(), any(), any()) }
    }

    private fun claim(status: BeneficiaryClaimStatus) = BeneficiaryPayoutClaim(
        id = "c1",
        groupId = "g1",
        memberId = "m1",
        beneficiaryName = "Thabo Nkosi",
        claimAmount = Money("10000.00"),
        causeOfDeath = "Natural causes",
        dateOfDeath = "2025-06-01",
        status = status
    )
}
