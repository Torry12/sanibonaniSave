package com.sanibonani.save.data.remote.supabase

internal const val LEDGER_PAGE_SIZE = 500

internal suspend fun <T> fetchAllLedgerPages(
    fetchPage: suspend (from: Long, to: Long) -> List<T>
): List<T> {
    val entries = mutableListOf<T>()
    var from = 0L

    while (true) {
        val page = fetchPage(from, from + LEDGER_PAGE_SIZE - 1)
        entries.addAll(page)
        if (page.size < LEDGER_PAGE_SIZE) return entries
        from += LEDGER_PAGE_SIZE
    }
}
