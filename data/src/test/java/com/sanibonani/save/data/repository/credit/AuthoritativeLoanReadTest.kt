package com.sanibonani.save.data.repository.credit

import com.sanibonani.save.data.datasource.LocalLoanDataSource
import com.sanibonani.save.data.datasource.RemoteLoanDataSource
import com.sanibonani.save.domain.model.Loan
import com.sanibonani.save.domain.model.LoanStatus
import com.sanibonani.save.domain.model.Money
import com.sanibonani.save.domain.service.FileStorageService
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
 * Three money decisions read a member's loan book and none of them may read the cache: the borrowing
 * power left after active loans, the arrears sanction a forced settlement weighs before it puts a
 * member back on probation, and how an incoming payment is split across what the member owes. All
 * three used [LoanRepository.getMemberLoans], whose first emission describes the Room cache - the
 * refresh verdict rides behind it - so `isSuccess` there says only that Room answered.
 * [LoanRepository.getMemberLoansSync] is the read that cannot answer without the server, which is
 * what the group ledger already has in `getGroupLoansSync`.
 */
class AuthoritativeLoanReadTest {

    private val memberId = "m-1"

    private fun loan(id: String, status: LoanStatus) = Loan(
        id = id,
        memberId = memberId,
        groupId = "g-1",
        amount = Money("500.00"),
        status = status
    )

    private class Fixture {
        val remote = mockk<RemoteLoanDataSource>(relaxed = true)
        val local = mockk<LocalLoanDataSource>(relaxed = true)

        fun repository(): LoanRepositoryImpl = LoanRepositoryImpl(
            remoteSource = remote,
            localSource = local,
            fileStorageService = mockk<FileStorageService>(relaxed = true),
            json = Json { ignoreUnknownKeys = true },
            pendingOpDao = mockk(relaxed = true),
            groupDao = mockk(relaxed = true)
        )
    }

    @Test
    fun `a loan refresh the server refused is a refusal and not the cached active loan`() = runTest {
        val fixture = Fixture()
        val cachedLoan = loan("l-1", LoanStatus.ACTIVE)
        every { fixture.local.observeMemberLoans(memberId) } returns flowOf(listOf(cachedLoan))
        coEvery { fixture.remote.getMemberLoans(memberId) } returns
            Result.failure(IOException("gateway unreachable"))
        val repo = fixture.repository()

        val authoritative = repo.getMemberLoansSync(memberId)

        assertTrue(
            "a loan the server was never asked about cannot be counted against borrowing power",
            authoritative.isFailure
        )
        assertTrue(
            "the flow the eligibility check used before answers success from Room for the same refused refresh",
            repo.getMemberLoans(memberId).first().isSuccess
        )
        coVerify(exactly = 0) { fixture.local.saveLoans(any()) }
    }

    @Test
    fun `the authoritative read returns the server loans and writes them to the cache`() = runTest {
        val fixture = Fixture()
        val serverLoans = listOf(loan("l-1", LoanStatus.ACTIVE), loan("l-2", LoanStatus.PARTIALLY_PAID))
        every { fixture.local.observeMemberLoans(memberId) } returns flowOf(emptyList())
        coEvery { fixture.remote.getMemberLoans(memberId) } returns Result.success(serverLoans)

        val result = fixture.repository().getMemberLoansSync(memberId)

        assertEquals(serverLoans, result.getOrNull())
        coVerify(exactly = 1) { fixture.local.saveLoans(serverLoans) }
    }

    @Test
    fun `an answered book with no loan is an answer rather than a missing read`() = runTest {
        // The distinction the allocation and the sanction branch on: empty means the member owes
        // nothing, and only a failure means the app cannot say either way.
        val fixture = Fixture()
        every { fixture.local.observeMemberLoans(memberId) } returns flowOf(emptyList())
        coEvery { fixture.remote.getMemberLoans(memberId) } returns Result.success(emptyList())

        val result = fixture.repository().getMemberLoansSync(memberId)

        assertTrue(result.isSuccess)
        assertEquals(emptyList<Loan>(), result.getOrNull())
    }
}
