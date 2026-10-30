package com.sanibonani.save.domain.usecase.admin

import com.sanibonani.save.domain.model.AuditLog
import com.sanibonani.save.domain.repository.admin.AuditLogRepository
import com.sanibonani.save.domain.repository.auth.SupabaseRepository
import com.sanibonani.save.domain.usecase.auth.GetAttributableActorIdUseCase
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `audit_logs.actor_id` is the answer to "who opened this member's screen". A support session that
 * cannot name the administrator must not write a row against a placeholder: the row would read as a
 * decision by an account that never made it.
 */
class RecordImpersonationAuditTest {

    private val auditLogRepository: AuditLogRepository = mockk(relaxed = true)
    private val supabaseRepository: SupabaseRepository = mockk()

    private fun useCase() = RecordImpersonationAuditUseCase(
        auditLogRepository = auditLogRepository,
        getAttributableActorId = GetAttributableActorIdUseCase(supabaseRepository)
    )

    @Test
    fun `the signed-in administrator is named as the actor`() = runBlocking {
        every { supabaseRepository.currentUserId } returns "support-1"
        val captured = slot<AuditLog>()

        val outcome = useCase()("m1", "g1")

        assertTrue(outcome.isSuccess)
        coVerify { auditLogRepository.logEvent(capture(captured)) }
        assertEquals("support-1", captured.captured.actorId)
        assertEquals("IMPERSONATION_REQUESTED", captured.captured.action)
        assertEquals("m1", captured.captured.targetMemberId)
        assertEquals("g1", captured.captured.targetGroupId)
    }

    @Test
    fun `a session with no signed-in administrator writes no audit row`() = runBlocking {
        every { supabaseRepository.currentUserId } returns null

        val outcome = useCase()("m1", "g1")

        assertTrue("the caller must be told the session was not recorded", outcome.isFailure)
        assertTrue(
            capturedMessage(outcome),
            capturedMessage(outcome).contains("signed-in administrator is unknown")
        )
        coVerify(exactly = 0) { auditLogRepository.logEvent(any()) }
    }

    @Test
    fun `a blank session id is not an actor either`() = runBlocking {
        every { supabaseRepository.currentUserId } returns "   "

        assertTrue(useCase()("m1", "g1").isFailure)
        coVerify(exactly = 0) { auditLogRepository.logEvent(any()) }
    }

    private fun capturedMessage(outcome: Result<Unit>): String =
        outcome.exceptionOrNull()?.message ?: ""
}
