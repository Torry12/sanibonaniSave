package com.sanibonani.save.data.repository

import com.sanibonani.save.data.local.PendingOperationDao
import io.ktor.client.plugins.ResponseException
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `safeApiCall` is the only guard standing between a failed money write and it being lost,
 * so the transient/permanent split has to match [com.sanibonani.save.data.repository.system.OutboxPolicy].
 */
class BaseRepositoryOutboxQueueTest {

    private class Probe : BaseRepository("OutboxQueueProbe") {
        suspend fun write(dao: PendingOperationDao, block: suspend () -> Unit): Result<Unit> =
            safeApiCall("contribution", "g1", "{}", dao) { block() }
    }

    private fun rejected(status: HttpStatusCode): ResponseException {
        val response = mockk<HttpResponse>(relaxed = true)
        every { response.status } returns status
        val rejection = mockk<ResponseException>(relaxed = true)
        every { rejection.response } returns response
        return rejection
    }

    @Test
    fun `a throttled contribution is queued for replay instead of dropped`() = runBlocking {
        val dao = mockk<PendingOperationDao>(relaxed = true)

        val result = Probe().write(dao) { throw rejected(HttpStatusCode.TooManyRequests) }

        assertTrue(result.isFailure)
        coVerify(exactly = 1) { dao.upsert(any()) }
    }

    @Test
    fun `a timed out contribution is queued for replay instead of dropped`() = runBlocking {
        val dao = mockk<PendingOperationDao>(relaxed = true)

        Probe().write(dao) { throw rejected(HttpStatusCode.RequestTimeout) }

        coVerify(exactly = 1) { dao.upsert(any()) }
    }

    @Test
    fun `a server side failure is queued for replay`() = runBlocking {
        val dao = mockk<PendingOperationDao>(relaxed = true)

        Probe().write(dao) { throw rejected(HttpStatusCode.InternalServerError) }

        coVerify(exactly = 1) { dao.upsert(any()) }
    }

    @Test
    fun `a rejected contribution is not queued because replay would fail the same way`() = runBlocking {
        val dao = mockk<PendingOperationDao>(relaxed = true)

        Probe().write(dao) { throw rejected(HttpStatusCode.BadRequest) }

        coVerify(exactly = 0) { dao.upsert(any()) }
    }
}
