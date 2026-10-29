package com.sanibonani.save.data.repository.group

import com.sanibonani.save.data.datasource.LocalGroupDataSource
import com.sanibonani.save.data.datasource.RealtimeDataSource
import com.sanibonani.save.data.datasource.RemoteGroupDataSource
import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.service.FileStorageService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class GroupRepositoryImplCacheTest {

    private val remoteGroupSource = mockk<RemoteGroupDataSource>()
    private val localGroupSource = mockk<LocalGroupDataSource>(relaxed = true)
    private val fileStorageService = mockk<FileStorageService>(relaxed = true)
    private val realtimeSource = mockk<RealtimeDataSource>(relaxed = true)

    private lateinit var repository: GroupRepositoryImpl

    @Before
    fun setUp() {
        repository = GroupRepositoryImpl(
            remoteGroupSource = remoteGroupSource,
            localGroupSource = localGroupSource,
            fileStorageService = fileStorageService,
            realtimeSource = realtimeSource,
            json = Json { ignoreUnknownKeys = true }
        )
    }

    @Test
    fun `a fetched group replaces the cached row`() = runBlocking {
        val fresh = Group(id = "g-1", name = "Renamed")
        coEvery { remoteGroupSource.getGroupById("g-1") } returns Result.success(fresh)

        val result = repository.getGroupById("g-1")

        assertEquals(fresh, result.getOrNull())
        coVerify(exactly = 1) { localGroupSource.saveGroup(fresh) }
    }

    @Test
    fun `a failed fetch falls back to the cached row without writing it back`() = runBlocking {
        val cached = Group(id = "g-1", name = "Cached")
        coEvery { remoteGroupSource.getGroupById("g-1") } returns Result.failure(IOException("offline"))
        coEvery { localGroupSource.getGroupById("g-1") } returns cached

        val result = repository.getGroupById("g-1")

        assertEquals(cached, result.getOrNull())
        coVerify(exactly = 0) { localGroupSource.saveGroup(any()) }
    }
}
