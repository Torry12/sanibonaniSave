package com.sanibonani.save.domain.usecase

import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.*
import com.sanibonani.save.domain.usecase.rosca.ExecuteRoscaPayoutUseCase
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class AutomateGovernanceUseCaseTest {

    private val groupRepo = mockk<GroupRepository>()
    private val memberRepo = mockk<MemberRepository>()
    private val loanRepo = mockk<LoanRepository>()
    private val claimRepo = mockk<BeneficiaryClaimRepository>()
    private val validateLoanEligibilityUseCase = mockk<ValidateLoanEligibilityUseCase>()
    private val processBurialClaimUseCase = mockk<ProcessBurialClaimUseCase>()
    private val executeRoscaPayoutUseCase = mockk<ExecuteRoscaPayoutUseCase>()
    private val requestPayoutUseCase = mockk<RequestPayoutUseCase>()
    private val auditLogRepo = mockk<AuditLogRepository>()

    private lateinit var useCase: AutomateGovernanceUseCase

    @Before
    fun setup() {
        useCase = AutomateGovernanceUseCase(
            groupRepo, memberRepo, loanRepo, claimRepo,
            validateLoanEligibilityUseCase, processBurialClaimUseCase,
            executeRoscaPayoutUseCase, requestPayoutUseCase, auditLogRepo
        )
    }

    @Test
    fun `invoke processes all automated governance tasks`() = runTest {
        // Given
        val group = Group(id = "g1", type = GroupType.BURIAL_SOCIETY, goalAmount = Money(20000.0))
        coEvery { groupRepo.getAllGroups() } returns Result.success(listOf(group))
        coEvery { claimRepo.getEscalatedClaimsSync() } returns Result.success(emptyList())
        coEvery { loanRepo.getAllPendingLoansSync() } returns Result.success(emptyList())

        // When
        val result = useCase()

        // Then
        assert(result.isSuccess)
        coVerify { groupRepo.getAllGroups() }
    }
}
