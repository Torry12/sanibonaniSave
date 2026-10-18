package com.sanibonani.save.domain.usecase.group

import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.admin.*

import com.sanibonani.save.domain.repository.group.*
import com.sanibonani.save.domain.repository.member.*
import com.sanibonani.save.domain.repository.system.*
import com.sanibonani.save.domain.usecase.disbursement.ExecuteRoscaPayoutUseCase
import com.sanibonani.save.domain.usecase.disbursement.RequestPayoutUseCase
import com.sanibonani.save.domain.usecase.credit.ValidateLoanEligibilityUseCase
import com.sanibonani.save.domain.usecase.member.ProcessBurialClaimUseCase
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import com.sanibonani.save.domain.repository.credit.LoanRepository

class AutomateGovernanceUseCaseTest {
    private val groupRepo = mockk<GroupRepository>()
    private val memberRepo = mockk<MemberRepository>()
    private val loanRepo = mockk<LoanRepository>()
    private val platformRepo = mockk<PlatformRepository>()
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
            groupRepo, memberRepo, loanRepo, platformRepo, claimRepo,
            validateLoanEligibilityUseCase, processBurialClaimUseCase,
            executeRoscaPayoutUseCase, requestPayoutUseCase, auditLogRepo
        )
    }

    @Test
    fun `invoke processes all automated governance tasks`() = runTest {
        // Given
        val group = Group(id = "g1", type = GroupType.BURIAL_SOCIETY, goalAmount = Money("20000.0"))
        coEvery { platformRepo.getAllGroups() } returns Result.success(listOf(group))
        coEvery { executeRoscaPayoutUseCase(any()) } returns Result.success("payout-executed")

        // When
        val result = useCase()

        // Then
        assert(result.isSuccess)
        coVerify { platformRepo.getAllGroups() }
    }
}
