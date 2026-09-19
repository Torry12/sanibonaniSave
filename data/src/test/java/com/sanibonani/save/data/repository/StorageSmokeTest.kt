package com.sanibonani.save.data.repository

import com.sanibonani.save.domain.repository.StorageRepository
import io.github.jan.supabase.SupabaseClient
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import javax.inject.Provider
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.BucketApi
import io.github.jan.supabase.storage.UploadConfig

class StorageSmokeTest {
    private val supabase = mockk<SupabaseClient>()
    private val storage = mockk<Storage>()
    private val bucket = mockk<BucketApi>()
    private lateinit var repo: StorageRepositoryImpl

    @Before
    fun setup() {
        every { supabase.plugin(Storage) } returns storage
        every { storage["documents"] } returns bucket
        repo = StorageRepositoryImpl(Provider { supabase })
    }

    @Test
    fun `uploadFile calls bucket upload with correct params`() = runBlocking {
        val testBytes = "test content".toByteArray()
        val testPath = "test/path.pdf"

        coEvery { bucket.upload(testPath, testBytes, any()) } returns ""

        val result = repo.uploadFile("documents", testPath, testBytes)

        assertTrue(result.isSuccess)
        assertEquals(testPath, result.getOrNull())

        coVerify { bucket.upload(testPath, testBytes, any()) }
    }
}
