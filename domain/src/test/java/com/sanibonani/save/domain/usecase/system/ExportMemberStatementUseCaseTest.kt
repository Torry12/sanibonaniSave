package com.sanibonani.save.domain.usecase.system

import com.sanibonani.save.domain.model.Contribution
import com.sanibonani.save.domain.model.ContributionStatus
import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.Member
import com.sanibonani.save.domain.model.Money
import com.sanibonani.save.domain.repository.equity.ContributionRepository
import com.sanibonani.save.domain.repository.system.ExportRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A statement is handed over as proof of what a member paid, so the rows behind it have to be the rows
 * the server holds. `observeMemberContributions` answers from Room first and sends the refresh verdict
 * behind those rows, so a caller that takes its first emission gets the cache and cannot tell that the
 * server never answered: the document would drop a contribution booked after the last sync and keep one
 * the outbox never delivered, with nothing on the paper saying so.
 */
class ExportMemberStatementUseCaseTest {

    private val exportRepository: ExportRepository = mockk()
    private val contributionRepository: ContributionRepository = mockk()

    private val useCase = ExportMemberStatementUseCase(exportRepository, contributionRepository)

    private val group = Group(id = "g-1", name = "Alpha Savings")
    private val member = Member(id = "m-1", groupId = "g-1", fullName = "Thabo Mokoena")
    private val serverRows = listOf(
        Contribution(
            id = "c-1",
            memberId = "m-1",
            groupId = "g-1",
            amount = Money("200.00"),
            status = ContributionStatus.PAID
        )
    )

    @Test
    fun `the document is built from the rows the server answered`() = runTest {
        coEvery { contributionRepository.syncMemberContributions("m-1", "g-1") } returns
            Result.success(serverRows)
        coEvery { exportRepository.exportContributionsToCsv(any(), any(), any()) } returns
            Result.success(File("statement.csv"))

        assertTrue(useCase(group, member, asPdf = false).isSuccess)

        coVerify { exportRepository.exportContributionsToCsv(group, member, serverRows) }
    }

    @Test
    fun `a contribution book the server refused writes no document`() = runTest {
        coEvery { contributionRepository.syncMemberContributions("m-1", "g-1") } returns
            Result.failure(IOException("gateway unreachable"))

        val outcome = useCase(group, member, asPdf = true)

        assertTrue("an unreadable book must not become a statement", outcome.isFailure)
        coVerify(exactly = 0) { exportRepository.exportContributionsToPdf(any(), any(), any()) }
        coVerify(exactly = 0) { exportRepository.exportContributionsToCsv(any(), any(), any()) }
    }

    // The cache is the one source that can answer during an outage, and the one source that must not
    // be used here: reaching for it is what made an offline export look like a completed one.
    @Test
    fun `a refused export does not fall back to the rows cached on the device`() = runTest {
        coEvery { contributionRepository.syncMemberContributions("m-1", "g-1") } returns
            Result.failure(IOException("gateway unreachable"))
        every { contributionRepository.observeMemberContributions("m-1", "g-1") } returns
            kotlinx.coroutines.flow.flowOf(Result.success(serverRows))

        assertTrue(useCase(group, member, asPdf = false).isFailure)

        coVerify(exactly = 0) { contributionRepository.observeMemberContributions(any(), any()) }
    }

    @Test
    fun `a member the server has not saved is reported instead of exported`() = runTest {
        val unsaved = Member(groupId = "g-1", fullName = "Unsaved Member")

        val outcome = useCase(group, unsaved, asPdf = false)

        assertTrue(outcome.isFailure)
        coVerify(exactly = 0) { contributionRepository.syncMemberContributions(any(), any()) }
        coVerify(exactly = 0) { exportRepository.exportContributionsToCsv(any(), any(), any()) }
    }
}
