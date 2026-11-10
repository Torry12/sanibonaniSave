package com.sanibonani.save.domain.usecase.group

import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.admin.*
import com.sanibonani.save.domain.usecase.disbursement.ExecuteRoscaPayoutUseCase
import io.mockk.*
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class AutomateGovernanceUseCaseTest {
    private val platformRepo = mockk<PlatformRepository>()
    private val executeRoscaPayoutUseCase = mockk<ExecuteRoscaPayoutUseCase>()
    private lateinit var useCase: AutomateGovernanceUseCase

    @Before
    fun setup() {
        useCase = AutomateGovernanceUseCase(
            platformRepo,
            executeRoscaPayoutUseCase
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

    // The roster is the only list of groups this sweep has, so reading nothing from it is not the same
    // as a platform with nothing to govern: reporting success here is how a whole day of ROSCA
    // rotations goes missing while the work log reads green.
    @Test
    fun `a refused roster fails the sweep instead of reporting a governed platform`() = runTest {
        coEvery { platformRepo.getAllGroups() } returns Result.failure(IOException("HTTP 503 Service Unavailable"))

        val result = useCase()

        assert(result.isFailure)
        coVerify(exactly = 0) { executeRoscaPayoutUseCase(any()) }
    }
}
