package com.sanibonani.save.data.repository.equity

import com.sanibonani.save.data.datasource.RemoteContributionDataSource
import com.sanibonani.save.data.local.ContributionDao
import com.sanibonani.save.data.local.SanibonaniDatabase
import com.sanibonani.save.data.local.toEntity
import com.sanibonani.save.domain.model.Contribution
import com.sanibonani.save.domain.model.ContributionStatus
import com.sanibonani.save.domain.model.Money
import com.sanibonani.save.domain.repository.member.MemberRepository
import io.github.jan.supabase.SupabaseClient
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import javax.inject.Provider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The behaviour track is the second place that read the cache and wrote as though the server had
 * answered. It does not display the rows, it overwrites a stored row with counts taken from them, so a
 * first emission that is simply an empty cache rewrites a member's history as zero contributions and
 * R0.00 - and the next scoring pass treats that as evidence. `syncMemberContributions` is the read that
 * cannot answer without the server, which is what that path needs.
 */
class MemberContributionAuthoritativeReadTest {

    private val memberId = "m-1"
    private val groupId = "g-1"

    private val remote = mockk<RemoteContributionDataSource>(relaxed = true)
    private val dao = mockk<ContributionDao>(relaxed = true)
    private val db = mockk<SanibonaniDatabase>(relaxed = true)

    private lateinit var repository: MemberContributionRepositoryImpl

    private fun contribution(status: ContributionStatus) = Contribution(
        id = "c-1",
        groupId = groupId,
        memberId = memberId,
        amount = Money("50.00"),
        status = status,
        type = "monthly"
    )

    @Before
    fun setUp() {
        every { db.contributionDao() } returns dao
        repository = MemberContributionRepositoryImpl(
            remoteSource = remote,
            supabaseProvider = mockk<Provider<SupabaseClient>>(relaxed = true),
            memberRepo = mockk<Provider<MemberRepository>>(relaxed = true),
            dbProvider = Provider { db }
        )
    }

    @Test
    fun `a refused member book is a refusal and not the cached rows`() = runTest {
        val cachedRow = contribution(ContributionStatus.PAID)
        every { dao.observeContributions(memberId) } returns flowOf(listOf(cachedRow.toEntity()))
        coEvery { remote.getMemberContributions(memberId, groupId) } returns
            Result.failure(IOException("gateway unreachable"))

        val authoritative = repository.syncMemberContributions(memberId, groupId)

        assertTrue(
            "the behaviour track must not be rewritten from a book the server refused",
            authoritative.isFailure
        )
        assertTrue(
            "the flow that path used before answers success from Room for the same refused refresh",
            repository.getMemberContributions(memberId, groupId).first().isSuccess
        )
        coVerify(exactly = 0) { dao.syncMemberContributions(any(), any()) }
    }

    @Test
    fun `the authoritative read returns the server rows and writes them to the cache`() = runTest {
        val serverRows = listOf(contribution(ContributionStatus.PAID))
        every { dao.observeContributions(memberId) } returns flowOf(emptyList())
        coEvery { remote.getMemberContributions(memberId, groupId) } returns Result.success(serverRows)

        val result = repository.syncMemberContributions(memberId, groupId)

        assertEquals(serverRows, result.getOrNull())
        coVerify(exactly = 1) {
            dao.syncMemberContributions(memberId, serverRows.map { it.toEntity() })
        }
    }

    @Test
    fun `an answered book with no rows is an answer rather than a missing read`() = runTest {
        // This is the case the track may legitimately score as "no history": the server said so.
        every { dao.observeContributions(memberId) } returns flowOf(emptyList())
        coEvery { remote.getMemberContributions(memberId, groupId) } returns Result.success(emptyList())

        val result = repository.syncMemberContributions(memberId, groupId)

        assertTrue(result.isSuccess)
        assertEquals(emptyList<Contribution>(), result.getOrNull())
    }
}
