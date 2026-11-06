package com.sanibonani.save.data.remote

import com.sanibonani.save.data.local.LocalDatabaseProvider
import com.sanibonani.save.data.local.SanibonaniDatabase
import com.sanibonani.save.data.utils.DevSettings
import io.github.jan.supabase.SupabaseClient
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

/**
 * A rebuild is a reconnect, and a reconnect is not a tier switch.
 *
 * The Room cache does not have a connection to lose: `sanibonani_local.db` and
 * `sanibonani_online.db` are two files, and which one the manager opens is decided entirely by
 * `devSettings.useOnlineDatabase`. Rebuilding it while that flag has not moved closes the open
 * instance and builds a second Room instance over the same file. That was the `#80` closed-database
 * failure: a reconnect, or any settings write, took the cache out from under a graph that was not
 * watching - the DAOs were Hilt singletons and every repository that injected one kept reading the
 * instance that had just been closed.
 *
 * `#89` stopped an unchanged rebuild from replacing the cache at all, and `#90` removed the frozen
 * half: no DAO is provided by the graph now, and every reader resolves it from this manager per
 * access, so the flip that does happen is followed. LiveRoomCacheAccessTest keeps it that way.
 *
 * The network client is the opposite case - that is what a reconnect is actually for, so it is
 * rebuilt on every call and these tests assert it stays that way.
 */
class SupabaseConnectionManagerRebuildTest {

    private val clientProvider = mockk<RemoteClientProvider>(relaxed = true)
    private val dbProvider = mockk<LocalDatabaseProvider>(relaxed = true)
    private val devSettings = mockk<DevSettings>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { devSettings.useOnlineDatabase } returns false
        // The manager collects this on construction; a relaxed mock of the property hands back a
        // chained stub instead of a flow, and the collect throws before the test body can.
        every { devSettings.settingsChanged } returns MutableSharedFlow(extraBufferCapacity = 1)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `a rebuild that does not change the tier keeps the live Room instance open`() = runTest {
        val firstCache = mockk<SanibonaniDatabase>(relaxed = true)
        val rebuiltCache = mockk<SanibonaniDatabase>(relaxed = true)
        val client = mockk<SupabaseClient>(relaxed = true)
        every { dbProvider.createDatabase(any()) } returns firstCache andThen rebuiltCache
        every { clientProvider.createClient(any()) } returns client

        val manager = SupabaseConnectionManager(clientProvider, dbProvider, devSettings)
        manager.rebuild()

        verify(exactly = 1) { dbProvider.createDatabase(any()) }
        assertSame(firstCache, manager.getLocalDatabase(SanibonaniDatabase::class.java))
        // timeout, not a plain exactly = 0: the close the defect performs is launched on IO, so the
        // assertion has to wait long enough to see it before calling its absence proof.
        verify(exactly = 0, timeout = 300) { firstCache.close() }
        // The client does reconnect - it is the Room half that must not be rebuilt.
        verify(exactly = 2) { clientProvider.createClient(any()) }
    }

    @Test
    fun `a rebuild after the tier flips installs the other cache and closes the one it replaces`() = runTest {
        val localCache = mockk<SanibonaniDatabase>(relaxed = true)
        val onlineCache = mockk<SanibonaniDatabase>(relaxed = true)
        every { dbProvider.createDatabase(any()) } returns localCache andThen onlineCache
        every { clientProvider.createClient(any()) } returns mockk(relaxed = true)
        every { devSettings.useOnlineDatabase } returnsMany listOf(false, true)

        val manager = SupabaseConnectionManager(clientProvider, dbProvider, devSettings)
        manager.rebuild()

        verify(exactly = 2) { dbProvider.createDatabase(any()) }
        assertSame(onlineCache, manager.getLocalDatabase(SanibonaniDatabase::class.java))
        verify(exactly = 1, timeout = 1_000) { localCache.close() }
    }

    @Test
    fun `the cache is opened for the tier the setting names`() = runTest {
        val onlineCache = mockk<SanibonaniDatabase>(relaxed = true)
        every { devSettings.useOnlineDatabase } returns true
        every { dbProvider.createDatabase(true) } returns onlineCache
        every { clientProvider.createClient(any()) } returns mockk(relaxed = true)

        val manager = SupabaseConnectionManager(clientProvider, dbProvider, devSettings)

        assertSame(onlineCache, manager.getLocalDatabase(SanibonaniDatabase::class.java))
        verify(exactly = 1) { dbProvider.createDatabase(true) }
    }
}
