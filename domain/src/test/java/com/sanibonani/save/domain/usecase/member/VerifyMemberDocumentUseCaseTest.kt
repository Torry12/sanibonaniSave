package com.sanibonani.save.domain.usecase.member

import com.sanibonani.save.domain.model.DocumentStatus
import com.sanibonani.save.domain.model.MemberStatus
import com.sanibonani.save.domain.repository.member.MemberRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Rejecting a required document is supposed to leave the member suspended. The relational-document
 * sibling already reported a refusal to do that; this one returned success regardless, so an admin
 * could watch a rejection go through while the member stayed active with a rejected ID on file.
 */
class VerifyMemberDocumentUseCaseTest {

    private val memberRepo: MemberRepository = mockk()
    private val updateMemberStatusUseCase: UpdateMemberStatusUseCase = mockk()
    private lateinit var useCase: VerifyMemberDocumentUseCase

    @Before
    fun setUp() {
        useCase = VerifyMemberDocumentUseCase(memberRepo, updateMemberStatusUseCase)
    }

    @Test
    fun `a rejection whose suspension was refused reports the refusal`() = runBlocking {
        coEvery { memberRepo.updateMemberDocumentStatus("m1", 0, DocumentStatus.REJECTED) } returns
            Result.success(Unit)
        coEvery { updateMemberStatusUseCase("m1", MemberStatus.SUSPENDED) } returns
            Result.failure(IOException("HTTP 500"))

        val result = useCase("m1", 0, approve = false)

        assertTrue(result.isFailure)
    }

    @Test
    fun `a rejection that suspends the member reports success`() = runBlocking {
        coEvery { memberRepo.updateMemberDocumentStatus("m1", 0, DocumentStatus.REJECTED) } returns
            Result.success(Unit)
        coEvery { updateMemberStatusUseCase("m1", MemberStatus.SUSPENDED) } returns Result.success(Unit)

        assertTrue(useCase("m1", 0, approve = false).isSuccess)
    }

    @Test
    fun `a refused document write does not reach for the member status`() {
        coEvery { memberRepo.updateMemberDocumentStatus("m1", 0, DocumentStatus.REJECTED) } returns
            Result.failure(IOException("HTTP 500"))

        val result = runBlocking { useCase("m1", 0, approve = false) }

        assertFalse("the document write never landed: ${result.exceptionOrNull()?.message}", result.isSuccess)
        coVerify(exactly = 0) { updateMemberStatusUseCase(any(), any()) }
    }

    @Test
    fun `approving a document leaves the member standing alone`() = runBlocking {
        coEvery { memberRepo.updateMemberDocumentStatus("m1", 0, DocumentStatus.VERIFIED) } returns
            Result.success(Unit)

        assertTrue(useCase("m1", 0, approve = true).isSuccess)

        coVerify(exactly = 0) { updateMemberStatusUseCase(any(), any()) }
    }
}
