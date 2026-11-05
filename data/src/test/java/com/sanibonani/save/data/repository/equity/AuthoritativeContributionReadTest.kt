package com.sanibonani.save.data.repository.equity

import com.sanibonani.save.data.datasource.LocalContributionDataSource
import com.sanibonani.save.data.datasource.RemoteContributionDataSource
import com.sanibonani.save.domain.model.Contribution
import com.sanibonani.save.domain.model.ContributionStatus
import com.sanibonani.save.domain.model.Money
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Two things certify money off the contributions book and neither of them may read the cache: the
 * probation promotion, which is proved by a member's joining-fee row, and the ROSCA rotation, whose
 * every pot is derived from the group's paid rows. Both used to read through
 * [ContributionRepository.observeMemberContributions] and `observeGroupContributions`, whose first
 * emission describes the Room cache - the refresh verdict rides behind it - so `isSuccess` on that
 * read says only that Room answered. The `sync*` reads are the ones that cannot answer without the
 * server, which is what certifying money needs.
 */
class AuthoritativeContributionReadTest {

    private val memberId = "m-1"
    private val groupId = "g-1"

    private fun contribution(type: String, status: ContributionStatus) = Contribution(
        id = "c-1",
        groupId = groupId,
        memberId = memberId,
        amount = Money("50.00"),
        status = status,
        type = type
    )

    private class Fixture {
        val remote = mockk<RemoteContributionDataSource>(relaxed = true)
        val local = mockk<LocalContributionDataSource>(relaxed = true)

        fun repository(): ContributionRepositoryImpl = ContributionRepositoryImpl(
            remoteSource = remote,
            localSource = local,
            groupDao = mockk(relaxed = true),
            json = Json { ignoreUnknownKeys = true },
            pendingOpDao = mockk(relaxed = true)
        )
    }

    @Test
    fun `a contribution refresh the server refused is a refusal and not the cached fee row`() = runTest {
        val fixture = Fixture()
        val cachedFeeRow = contribution("joining_fee", ContributionStatus.PAID)
        every { fixture.local.observeMemberContributions(memberId) } returns flowOf(listOf(cachedFeeRow))
        coEvery { fixture.remote.getMemberContributions(memberId, groupId) } returns
            Result.failure(IOException("gateway unreachable"))
        val repo = fixture.repository()

        val authoritative = repo.syncMemberContributions(memberId, groupId)

        assertTrue(
            "the promotion cycle has to be able to defer on a book it could not read",
            authoritative.isFailure
        )
        assertTrue(
            "the flow the cycle used before answers success from Room for the same refused refresh",
            repo.observeMemberContributions(memberId, groupId).first().isSuccess
        )
        coVerify(exactly = 0) { fixture.local.syncMemberContributions(any(), any()) }
    }

    @Test
    fun `the authoritative member read returns the server rows and writes them to the cache`() = runTest {
        val fixture = Fixture()
        val serverRows = listOf(contribution("joining_fee", ContributionStatus.PAID))
        every { fixture.local.observeMemberContributions(memberId) } returns flowOf(emptyList())
        coEvery { fixture.remote.getMemberContributions(memberId, groupId) } returns Result.success(serverRows)

        val result = fixture.repository().syncMemberContributions(memberId, groupId)

        assertEquals(serverRows, result.getOrNull())
        coVerify(exactly = 1) { fixture.local.syncMemberContributions(memberId, serverRows) }
    }

    @Test
    fun `an answered book with no joining fee row is an answer rather than a missing read`() = runTest {
        // The distinction the worker branches on: empty means the fee is genuinely unpaid, and only a
        // failure means the cycle cannot say either way.
        val fixture = Fixture()
        every { fixture.local.observeMemberContributions(memberId) } returns flowOf(emptyList())
        coEvery { fixture.remote.getMemberContributions(memberId, groupId) } returns Result.success(emptyList())

        val result = fixture.repository().syncMemberContributions(memberId, groupId)

        assertTrue(result.isSuccess)
        assertEquals(emptyList<Contribution>(), result.getOrNull())
    }

    @Test
    fun `a group book the server refused is a refusal and not the cached paid rows`() = runTest {
        val fixture = Fixture()
        val cachedPaidRow = contribution("monthly", ContributionStatus.PAID)
        every { fixture.local.observeGroupContributions(groupId) } returns flowOf(listOf(cachedPaidRow))
        coEvery { fixture.remote.getGroupContributions(groupId) } returns
            Result.failure(IOException("gateway unreachable"))
        val repo = fixture.repository()

        val authoritative = repo.syncGroupContributions(groupId)

        assertTrue(
            "a rotation cannot size a pot from a book it could not re-read",
            authoritative.isFailure
        )
        assertTrue(
            "the flow the rotation used before answers success from Room for the same refused refresh",
            repo.observeGroupContributions(groupId).first().isSuccess
        )
        coVerify(exactly = 0) { fixture.local.syncGroupContributions(any(), any()) }
    }

    @Test
    fun `the authoritative group read returns the server rows and writes them to the cache`() = runTest {
        val fixture = Fixture()
        val serverRows = listOf(contribution("monthly", ContributionStatus.PAID))
        every { fixture.local.observeGroupContributions(groupId) } returns flowOf(emptyList())
        coEvery { fixture.remote.getGroupContributions(groupId) } returns Result.success(serverRows)

        val result = fixture.repository().syncGroupContributions(groupId)

        assertEquals(serverRows, result.getOrNull())
        coVerify(exactly = 1) { fixture.local.syncGroupContributions(groupId, serverRows) }
    }

    @Test
    fun `an answered group book with no paid row is an answer rather than a missing read`() = runTest {
        // The rotation treats this as "nobody has funded the pot" and holds every payoutee, which is a
        // different decision from the one it makes when the server refused to answer at all.
        val fixture = Fixture()
        every { fixture.local.observeGroupContributions(groupId) } returns flowOf(emptyList())
        coEvery { fixture.remote.getGroupContributions(groupId) } returns Result.success(emptyList())

        val result = fixture.repository().syncGroupContributions(groupId)

        assertTrue(result.isSuccess)
        assertEquals(emptyList<Contribution>(), result.getOrNull())
    }
}
