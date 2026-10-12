package com.sanibonani.save.data.repository

import com.sanibonani.save.data.repository.system.StorageRepositoryImpl
import com.sanibonani.save.domain.repository.system.StorageRepository
import io.github.jan.supabase.SupabaseClient
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.BucketApi
import io.github.jan.supabase.storage.storage
import io.github.jan.supabase.storage.FileUploadResponse

class StorageSmokeTest {
    private val supabase = mockk<SupabaseClient>()
    private val storage = mockk<Storage>()
    private val bucket = mockk<BucketApi>()
    private lateinit var repo: StorageRepositoryImpl

    @Before
    fun setup() {
        mockkStatic("io.github.jan.supabase.storage.StorageKt")
        every { supabase.storage } returns storage
        every { storage["documents"] } returns bucket
        repo = StorageRepositoryImpl(supabase)
    }

    @After
    fun tearDown() {
        unmockkStatic("io.github.jan.supabase.storage.StorageKt")
    }

    @Test
    fun `uploadFile calls bucket upload with correct params`() = runBlocking {
        val testBytes = "test content".toByteArray()
        val testPath = "test/path.pdf"

        coEvery { bucket.upload(testPath, testBytes, any()) } returns mockk<FileUploadResponse>(relaxed = true)

        val result = repo.uploadFile("documents", testPath, testBytes)

        assertTrue(result.isSuccess)
        assertEquals(testPath, result.getOrNull())

        coVerify { bucket.upload(testPath, testBytes, any()) }
    }
}
