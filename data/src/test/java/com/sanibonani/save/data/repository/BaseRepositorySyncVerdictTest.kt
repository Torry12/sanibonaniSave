package com.sanibonani.save.data.repository

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `observeAndSync` answers from the Room cache and refreshes it in the background, so the rows on screen
 * are never proof the server took part. Before this the refusal was logged here and dropped, which left
 * every `onFailure` branch the screens had been given - the discovery banner, the member ledger's
 * "Transactions unavailable" - unreachable for the one failure that actually happens in the field.
 * The verdict now travels in the flow, but only behind the cached rows, because a caller that takes a
 * single emission has to be handed the cache it was promised, not this refusal.
 */
class BaseRepositorySyncVerdictTest {

    private class Probe : BaseRepository("SyncVerdictProbe") {
        fun observeCacheThenSync(
            dbFlow: Flow<List<String>>,
            networkFetch: suspend () -> List<String>,
            cacheSync: suspend (List<String>) -> Unit
        ): Flow<Result<List<String>>> = observeAndSync(
            dbFlow = dbFlow,
            mapper = { it },
            toEntity = { it },
            networkFetch = networkFetch,
            cacheSync = cacheSync
        )

        fun observeItemThenSync(
            dbFlow: Flow<String?>,
            networkFetch: suspend () -> String,
            cacheSync: suspend (String) -> Unit
        ): Flow<Result<String?>> = observeAndSyncItem(
            dbFlow = dbFlow,
            mapper = { it },
            toEntity = { it },
            networkFetch = networkFetch,
            cacheSync = cacheSync
        )
    }

    private val probe = Probe()

    @Test
    fun `a refresh the server refuses still hands over the cached rows and then says so`() = runTest {
        val emissions = probe.observeCacheThenSync(
            dbFlow = flowOf(listOf("cached-row")),
            networkFetch = { throw IOException("gateway unreachable") },
            cacheSync = { }
        ).take(2).toList()

        assertEquals(2, emissions.size)
        assertTrue("the rows the screen is already showing come first", emissions[0].isSuccess)
        assertEquals(listOf("cached-row"), emissions[0].getOrNull())
        assertTrue("the refusal is a verdict the collector can act on", emissions[1].isFailure)
    }

    @Test
    fun `a caller that takes one emission is handed the cache and not the refusal`() = runTest {
        val first = probe.observeCacheThenSync(
            dbFlow = flow {
                delay(200L)
                emit(listOf("cached-row"))
            },
            networkFetch = { throw IOException("gateway unreachable") },
            cacheSync = { }
        ).first()

        assertTrue(first.isSuccess)
        assertEquals(listOf("cached-row"), first.getOrNull())
    }

    @Test
    fun `a refresh that answers adds no verdict emission of its own`() = runTest {
        val emissions = mutableListOf<Result<List<String>>>()
        val cached = mutableListOf<List<String>>()
        val collector = launch {
            probe.observeCacheThenSync(
                dbFlow = flowOf(listOf("cached-row")),
                networkFetch = { listOf("server-row") },
                cacheSync = { rows -> cached.add(rows) }
            ).collect { emissions.add(it) }
        }
        advanceUntilIdle()
        collector.cancel()

        assertEquals(listOf("server-row"), cached.single())
        assertEquals(1, emissions.size)
        assertTrue(emissions.single().isSuccess)
    }

    @Test
    fun `a refresh whose cache write was refused is reported instead of only logged`() = runTest {
        val emissions = probe.observeCacheThenSync(
            dbFlow = flowOf(listOf("cached-row")),
            networkFetch = { listOf("server-row") },
            cacheSync = { throw IOException("room write failed") }
        ).take(2).toList()

        assertTrue(emissions[0].isSuccess)
        assertTrue("a sync that did not land cannot read as a clean refresh", emissions[1].isFailure)
    }

    @Test
    fun `the single item variant reports its refusal behind the cached row`() = runTest {
        val emissions = probe.observeItemThenSync(
            dbFlow = flowOf("cached-item"),
            networkFetch = { throw IOException("gateway unreachable") },
            cacheSync = { }
        ).take(2).toList()

        assertEquals(2, emissions.size)
        assertEquals("cached-item", emissions[0].getOrNull())
        assertTrue(emissions[1].isFailure)
    }

    @Test
    fun `a cancelled read is not reported to the collector as a refusal`() = runTest {
        val emissions = mutableListOf<Result<List<String>>>()
        val collector = launch {
            runCatching {
                probe.observeCacheThenSync(
                    dbFlow = flowOf(listOf("cached-row")),
                    networkFetch = { throw CancellationException("the collector left") },
                    cacheSync = { }
                ).collect { emissions.add(it) }
            }
        }
        advanceUntilIdle()
        collector.join()

        assertNull(emissions.firstOrNull { it.isFailure })
    }
}
