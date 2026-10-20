package com.sanibonani.save

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.group.GroupRepository
import com.sanibonani.save.domain.repository.member.MemberRepository
import com.sanibonani.save.data.repository.member.MemberRepositoryImpl
import com.sanibonani.save.data.datasource.*
import com.sanibonani.save.data.local.PendingOperationDao
import com.sanibonani.save.domain.service.FileStorageService
import com.sanibonani.save.data.repository.mappers.DataPayloadFactory
import com.sanibonani.save.domain.repository.admin.ActuarialRepository
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import javax.inject.Provider
import kotlinx.serialization.json.Json
import com.google.firebase.crashlytics.FirebaseCrashlytics

@OptIn(ExperimentalCoroutinesApi::class)
class MemberRepositoryTest {

    private lateinit var repository: MemberRepository

    private val remoteMemberSource = mockk<RemoteMemberDataSource>(relaxed = true)
    private val localMemberSource = mockk<LocalMemberDataSource>(relaxed = true)
    private val fileStorageService = mockk<FileStorageService>(relaxed = true)
    private val pendingOpDao = mockk<PendingOperationDao>(relaxed = true)
    private val dbProvider = mockk<Provider<com.sanibonani.save.data.local.SanibonaniDatabase>>(relaxed = true)
    private val remoteContributionSource = mockk<RemoteContributionDataSource>(relaxed = true)
    private val json = Json { ignoreUnknownKeys = true }

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0

        mockkStatic(FirebaseCrashlytics::class)
        val mockCrashlytics = mockk<FirebaseCrashlytics>(relaxed = true)
        every { FirebaseCrashlytics.getInstance() } returns mockCrashlytics

        // Mock Looper and Handler
        mockkStatic(Looper::class)
        every { Looper.getMainLooper() } returns mockk(relaxed = true)
        mockkConstructor(Handler::class)
        every { anyConstructed<Handler>().post(any()) } answers {
            val runnable = firstArg<Runnable>()
            runnable.run()
            true
        }

        Dispatchers.setMain(testDispatcher)

        repository = MemberRepositoryImpl(
            remoteMemberSource,
            localMemberSource,
            fileStorageService,
            json,
            pendingOpDao,
            dbProvider,
            remoteContributionSource
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkConstructor(Handler::class)
        unmockkAll()
    }

    @Test
    fun `getGroupMembers emits cached data immediately`() = runTest {
        val groupId = "test_group"
        val cachedMembers = listOf(
            Member(
                id = "1", groupId = groupId, userId = "u1",
                fullName = "John Doe", idNumber = "123", phone = "012", email = "a@b.com",
                status = MemberStatus.ACTIVE
            )
        )

        every { localMemberSource.observeActiveMembers(groupId) } returns flowOf(cachedMembers)
        coEvery { remoteMemberSource.getMembers(groupId) } returns Result.success(cachedMembers)

        val result = repository.getGroupMembers(groupId).first()

        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull()?.size)
        assertEquals("John Doe", result.getOrNull()?.get(0)?.fullName)
    }
}
