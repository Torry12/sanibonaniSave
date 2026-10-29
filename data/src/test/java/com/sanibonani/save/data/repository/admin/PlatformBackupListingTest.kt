package com.sanibonani.save.data.repository.admin

import com.sanibonani.save.data.local.SanibonaniDatabase
import com.sanibonani.save.domain.repository.admin.ActuarialRepository
import com.sanibonani.save.domain.repository.system.DataConnectionProtocol
import com.sanibonani.save.domain.repository.system.NotificationRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.storage.BucketApi
import io.github.jan.supabase.storage.FileObject
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.time.Instant

/**
 * The admin backup screen is the only way an operator checks that a dump exists before a repair, so a
 * listing that silently comes back empty is worse than an error: it reads as "no backups to lose".
 * Backups are storage objects — [PlatformRepositoryImpl.deleteCloudBackup] already deletes them from
 * the `backups` bucket — so the listing has to read the same bucket and no invented table.
 *
 * The storage request builders are inline, so the server is stubbed where the bucket handle is
 * resolved.
 */
class PlatformBackupListingTest {

    private val protocol = mockk<DataConnectionProtocol>()
    private val supabase = mockk<SupabaseClient>()
    private val storage = mockk<Storage>()
    private val bucket = mockk<BucketApi>()

    private lateinit var repository: PlatformRepositoryImpl

    @Before
    fun setUp() {
        mockkStatic("io.github.jan.supabase.storage.StorageKt")
        every { supabase.storage } returns storage
        every { storage["backups"] } returns bucket
        every { protocol.getRemoteClient(SupabaseClient::class.java) } returns supabase

        repository = PlatformRepositoryImpl(
            protocol = protocol,
            db = mockk<SanibonaniDatabase>(relaxed = true),
            actuarialRepo = mockk<ActuarialRepository>(relaxed = true),
            notifRepo = mockk<NotificationRepository>(relaxed = true)
        )
    }

    @After
    fun tearDown() {
        unmockkStatic("io.github.jan.supabase.storage.StorageKt")
    }

    private fun backupObject(name: String, sizeBytes: Long, updatedAtMillis: Long) =
        mockk<FileObject> {
            every { this@mockk.name } returns name
            every { metadata } returns buildJsonObject { put("size", sizeBytes) }
            every { updatedAt } returns Instant.fromEpochMilliseconds(updatedAtMillis)
        }

    @Test
    fun `the newest backup is listed first with the bytes the bucket reports`() = runBlocking {
        coEvery { bucket.list(any(), any()) } returns listOf(
            backupObject("dump-2026-10-27.sql", 1024L, 1_761_600_000_000L),
            backupObject("dump-2026-10-28.sql", 4096L, 1_761_686_400_000L)
        )

        val backups = repository.getCloudBackups().getOrThrow()

        assertEquals(listOf("dump-2026-10-28.sql", "dump-2026-10-27.sql"), backups.map { it.name })
        assertEquals(4096L, backups.first().size)
        verify(exactly = 1) { storage["backups"] }
    }

    @Test
    fun `an empty bucket is reported as no backups, not an error`() = runBlocking {
        coEvery { bucket.list(any(), any()) } returns emptyList()

        val backups = repository.getCloudBackups().getOrThrow()

        assertTrue(backups.isEmpty())
    }

    @Test
    fun `an unreachable bucket is a failure, never a quiet empty list`() = runBlocking {
        coEvery { bucket.list(any(), any()) } throws IllegalStateException("HTTP 404 Bucket Not Found")

        val outcome = repository.getCloudBackups()

        assertTrue(
            "an outage must be distinguishable from a bucket that genuinely holds no dumps",
            outcome.isFailure
        )
        assertNull(outcome.getOrNull())
    }
}
