package com.sanibonani.save.data.remote.supabase

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class LedgerPagingTest {

    @Test
    fun `fetches every full page and stops on the final partial page`() = runTest {
        val requestedRanges = mutableListOf<Pair<Long, Long>>()

        val entries = fetchAllLedgerPages { from, to ->
            requestedRanges += from to to
            when (from) {
                0L -> List(LEDGER_PAGE_SIZE) { it }
                500L -> List(LEDGER_PAGE_SIZE) { LEDGER_PAGE_SIZE + it }
                1000L -> listOf(1000, 1001)
                else -> error("Unexpected page offset: $from")
            }
        }

        assertEquals(
            listOf(0L to 499L, 500L to 999L, 1000L to 1499L),
            requestedRanges
        )
        assertEquals(1002, entries.size)
        assertEquals(listOf(999, 1000, 1001), entries.takeLast(3))
    }

    @Test
    fun `empty first page returns no entries`() = runTest {
        var calls = 0

        val entries = fetchAllLedgerPages<Int> { _, _ ->
            calls += 1
            emptyList()
        }

        assertEquals(emptyList<Int>(), entries)
        assertEquals(1, calls)
    }
}
