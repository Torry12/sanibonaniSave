package com.sanibonani.save.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.BucketApi
import io.github.jan.supabase.storage.FileUploadResponse
import io.github.jan.supabase.storage.storage
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for StorageRepositoryImpl
 * Tests upload, download, delete, and signed URL functionality.
 */
class StorageRepositoryImplTest {

    private lateinit var storageRepository: StorageRepositoryImpl
    private val supabaseClient: SupabaseClient = mockk()
    private val storage: Storage = mockk()
    private val bucketApi: BucketApi = mockk()

    @Before
    fun setUp() {
        // `SupabaseClient.storage` is provided via a Kotlin extension property.
        // When `SupabaseClient` is a mock, the real extension would try to resolve the Storage
        // plugin and crash. Mock the extension's generated Kotlin class so we can stub it.
        mockkStatic("io.github.jan.supabase.storage.StorageKt")
        every { supabaseClient.storage } returns storage
        every { storage.get(any()) } returns bucketApi
        storageRepository = StorageRepositoryImpl(supabaseClient)
    }

    @After
    fun tearDown() {
        unmockkStatic("io.github.jan.supabase.storage.StorageKt")
    }

    @Test
    fun `uploadFile should return path on success`() = runBlocking {
        // Given
        val bucketName = "documents"
        val path = "members/test-member-id/doc_1.pdf"
        val byteArray = "test content".toByteArray()

        val uploadResponse: FileUploadResponse = mockk(relaxed = true)
        coEvery { bucketApi.upload(path, byteArray, any()) } returns uploadResponse

        // When
        val result = storageRepository.uploadFile(bucketName, path, byteArray)

        // If the test fails, surface the underlying exception in the test report.
        result.exceptionOrNull()?.let { throw it }

        // Then
        assertTrue(result.isSuccess)
        assertEquals(path, result.getOrNull())
        coVerify { bucketApi.upload(path, byteArray, any()) }
    }

    @Test
    fun `getPublicUrl should return public URL for avatars bucket`() = runBlocking {
        // Given
        val bucketName = "avatars"
        val path = "members/test-member-id/profile.jpg"
        val expectedUrl = "https://project.supabase.co/storage/v1/object/public/avatars/$path"
        
        every { bucketApi.publicUrl(path) } returns expectedUrl

        // When
        val result = storageRepository.getPublicUrl(bucketName, path)

        // Then
        assertEquals(expectedUrl, result)
        verify { bucketApi.publicUrl(path) }
    }

    @Test
    fun `getPublicUrl should return authenticated URL for private documents bucket`() = runBlocking {
        val path = "members/test-member-id/doc_1.pdf"
        every { supabaseClient.supabaseUrl } returns "https://project.supabase.co"

        val result = storageRepository.getPublicUrl("documents", path)

        assertEquals(
            "https://project.supabase.co/storage/v1/object/authenticated/documents/$path",
            result
        )
        verify(exactly = 0) { bucketApi.publicUrl(any()) }
    }

    @Test
    fun `downloadFile should return bytes on success`() = runBlocking {
        // Given
        val bucketName = "documents"
        val path = "members/test-member-id/doc_1.pdf"
        val expectedBytes = "file content".toByteArray()

        coEvery { bucketApi.downloadAuthenticated(path) } returns expectedBytes

        // When
        val result = storageRepository.downloadFile(bucketName, path)

        // If the test fails, surface the underlying exception in the test report.
        result.exceptionOrNull()?.let { throw it }

        // Then
        assertTrue(result.isSuccess)
        assertArrayEquals(expectedBytes, result.getOrNull())
    }

    @Test
    fun `deleteFile should succeed when file exists`() = runBlocking {
        // Given
        val bucketName = "documents"
        val path = "members/test-member-id/doc_1.pdf"

        coEvery { bucketApi.delete(path) } returns Unit

        // When
        val result = storageRepository.deleteFile(bucketName, path)

        // If the test fails, surface the underlying exception in the test report.
        result.exceptionOrNull()?.let { throw it }

        // Then
        assertTrue(result.isSuccess)
        coVerify { bucketApi.delete(path) }
    }

    @Test
    fun `uploadFile should handle errors gracefully`() = runBlocking {
        // Given
        val bucketName = "documents"
        val path = "invalid/path"
        val byteArray = "test".toByteArray()

        coEvery { bucketApi.upload(path, byteArray, any()) } throws Exception("Upload failed")

        // When
        val result = storageRepository.uploadFile(bucketName, path, byteArray)

        // Then
        assertTrue(result.isFailure)
    }
}

