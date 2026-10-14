package com.sanibonani.save.data.repository

import com.sanibonani.save.data.repository.system.SyncRepositoryImpl
import com.sanibonani.save.data.local.SanibonaniDatabase
import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.Member
import com.sanibonani.save.domain.model.UserRole
import com.sanibonani.save.domain.repository.group.GroupRepository
import com.sanibonani.save.domain.repository.member.MemberRepository
import com.sanibonani.save.domain.repository.system.NotificationRepository
import com.sanibonani.save.domain.repository.auth.SupabaseRepository
import com.sanibonani.save.domain.repository.system.SyncStatus
import io.github.jan.supabase.SupabaseClient
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SyncRepositoryImplTest {

    private val supabase = mockk<SupabaseClient>(relaxed = true)
    private val supabaseRepo = mockk<SupabaseRepository>()
    private val memberRepo = mockk<MemberRepository>()
    private val groupRepo = mockk<GroupRepository>()
    private val notificationRepo = mockk<NotificationRepository>()
    private val db = mockk<SanibonaniDatabase>(relaxed = true)

    private lateinit var repository: SyncRepositoryImpl

    @Before
    fun setUp() {
        repository = SyncRepositoryImpl(
            supabase = supabase,
            supabaseRepo = supabaseRepo,
            memberRepo = memberRepo,
            groupRepo = groupRepo,
            notificationRepo = notificationRepo,
            db = db,
            contributionHandler = mockk(relaxed = true),
            loanRepaymentHandler = mockk(relaxed = true),
            payoutHandler = mockk(relaxed = true),
            disbursementHandler = mockk(relaxed = true),
            balanceAdjustmentHandler = mockk(relaxed = true),
            beneficiaryClaimHandler = mockk(relaxed = true)
        )
    }

    @Test
    fun `syncAllData fails with user-friendly error when not authenticated`() = runBlocking {
        every { supabaseRepo.currentUserId } returns null

        val result = repository.syncAllData()

        assertTrue(result.isFailure)
        val status = repository.syncStatus.value
        assertTrue(status is SyncStatus.Error)
        assertEquals(
            "Please sign in to sync your data.",
            (status as SyncStatus.Error).message
        )

        coVerify(exactly = 0) { memberRepo.getMemberships(any()) }
    }

    @Test
    fun `syncAllData completes and syncs valid memberships`() = runBlocking {
        every { supabaseRepo.currentUserId } returns "user_1"
        coEvery { supabaseRepo.getUserRole() } returns UserRole.PLATFORM_ADMIN
        coEvery { memberRepo.getMemberships("user_1") } returns Result.success(
            listOf(
                Member(id = "m1", groupId = "group_1"),
                Member(id = "m2", groupId = "")
            )
        )
        coEvery { groupRepo.getGroupsByAdmin("user_1") } returns Result.success(emptyList())
        coEvery { groupRepo.getGroupById("group_1") } returns Result.success(
            Group(id = "group_1", name = "Group One")
        )
        coEvery { memberRepo.syncGroupMembers("group_1") } returns Result.success(emptyList())
        coEvery { notificationRepo.syncNotifications("group_1") } returns Result.success(Unit)

        val result = repository.syncAllData()

        assertTrue(result.isSuccess)
        assertTrue(repository.syncStatus.value is SyncStatus.Completed)
        coVerify(exactly = 1) { memberRepo.getMemberships("user_1") }
        coVerify(exactly = 1) { groupRepo.getGroupById("group_1") }
        coVerify(exactly = 1) { memberRepo.syncGroupMembers("group_1") }
        coVerify(exactly = 1) { notificationRepo.syncNotifications("group_1") }
    }

    @Test
    fun `concurrent syncAllData calls join one in-flight sync`() = runBlocking {
        every { supabaseRepo.currentUserId } returns "user_1"
        coEvery { supabaseRepo.getUserRole() } returns UserRole.PLATFORM_ADMIN
        coEvery { memberRepo.getMemberships("user_1") } coAnswers {
            delay(150)
            Result.success(listOf(Member(id = "m1", groupId = "group_1")))
        }
        coEvery { groupRepo.getGroupsByAdmin("user_1") } returns Result.success(emptyList())
        coEvery { groupRepo.getGroupById("group_1") } returns Result.success(
            Group(id = "group_1", name = "Group One")
        )
        coEvery { memberRepo.syncGroupMembers("group_1") } returns Result.success(emptyList())
        coEvery { notificationRepo.syncNotifications("group_1") } returns Result.success(Unit)

        val results = listOf(
            async { repository.syncAllData() },
            async { repository.syncAllData() },
            async { repository.syncAllData() }
        ).awaitAll()

        assertTrue(results.all { it.isSuccess })
        coVerify(exactly = 1) { memberRepo.getMemberships("user_1") }
    }
}

