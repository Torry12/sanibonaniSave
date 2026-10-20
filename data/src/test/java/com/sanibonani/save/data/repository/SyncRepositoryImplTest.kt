package com.sanibonani.save.data.repository

import com.sanibonani.save.data.repository.system.SyncRepositoryImpl
import com.sanibonani.save.data.local.PendingOperationDao
import com.sanibonani.save.data.local.PendingOperationEntity
import com.sanibonani.save.data.local.SanibonaniDatabase
import com.sanibonani.save.data.repository.system.BalanceAdjustmentOutboxHandler
import com.sanibonani.save.data.repository.system.BeneficiaryClaimOutboxHandler
import com.sanibonani.save.data.repository.system.ContributionOutboxHandler
import com.sanibonani.save.data.repository.system.DisbursementOutboxHandler
import com.sanibonani.save.data.repository.system.LoanRepaymentOutboxHandler
import com.sanibonani.save.data.repository.system.OutboxPolicy
import com.sanibonani.save.data.repository.system.PayoutRequestOutboxHandler
import com.sanibonani.save.data.repository.system.PayoutStatusUpdateOutboxHandler
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
import io.mockk.slot
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.UnknownHostException

class SyncRepositoryImplTest {

    private val supabase = mockk<SupabaseClient>(relaxed = true)
    private val supabaseRepo = mockk<SupabaseRepository>()
    private val memberRepo = mockk<MemberRepository>()
    private val groupRepo = mockk<GroupRepository>()
    private val notificationRepo = mockk<NotificationRepository>()
    private val db = mockk<SanibonaniDatabase>(relaxed = true)
    private val outboxDao = mockk<PendingOperationDao>(relaxed = true)

    private val contributionHandler = mockk<ContributionOutboxHandler>(relaxed = true)
    private val loanRepaymentHandler = mockk<LoanRepaymentOutboxHandler>(relaxed = true)
    private val payoutHandler = mockk<PayoutRequestOutboxHandler>(relaxed = true)
    private val disbursementHandler = mockk<DisbursementOutboxHandler>(relaxed = true)
    private val balanceAdjustmentHandler = mockk<BalanceAdjustmentOutboxHandler>(relaxed = true)
    private val beneficiaryClaimHandler = mockk<BeneficiaryClaimOutboxHandler>(relaxed = true)
    private val payoutStatusUpdateHandler = mockk<PayoutStatusUpdateOutboxHandler>(relaxed = true)

    private lateinit var repository: SyncRepositoryImpl

    @Before
    fun setUp() {
        every { db.pendingOperationDao() } returns outboxDao
        repository = SyncRepositoryImpl(
            supabase = supabase,
            supabaseRepo = supabaseRepo,
            memberRepo = memberRepo,
            groupRepo = groupRepo,
            notificationRepo = notificationRepo,
            db = db,
            contributionHandler = contributionHandler,
            loanRepaymentHandler = loanRepaymentHandler,
            payoutHandler = payoutHandler,
            disbursementHandler = disbursementHandler,
            balanceAdjustmentHandler = balanceAdjustmentHandler,
            beneficiaryClaimHandler = beneficiaryClaimHandler,
            payoutStatusUpdateHandler = payoutStatusUpdateHandler
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

    private fun queued(
        id: String,
        groupId: String,
        createdAt: Long,
        retryCount: Int = 0
    ) = PendingOperationEntity(
        id = id,
        type = "contribution",
        payload = "{}",
        groupId = groupId,
        createdAt = createdAt,
        retryCount = retryCount
    )

    @Test
    fun `a rejected operation only halts its own group lane`() = runBlocking {
        val poison = queued("poison", "group_a", 1)
        val healthy = queued("healthy", "group_b", 2)
        coEvery { outboxDao.getAll() } returns listOf(poison, healthy)
        coEvery { contributionHandler.handle(any()) } answers {
            val operation = firstArg<com.sanibonani.save.domain.model.PendingOperation>()
            if (operation.groupId == "group_a") {
                Result.failure(IllegalArgumentException("Outbox payload is missing 'amount'"))
            } else {
                Result.success(Unit)
            }
        }

        assertTrue(repository.processOutbox().isSuccess)

        coVerify(exactly = 1) { outboxDao.delete(healthy) }
        coVerify(exactly = 1) { outboxDao.upsert(match { it.retryCount == 1 }) }
    }

    @Test
    fun `a halted lane keeps its sequence`() = runBlocking {
        coEvery { outboxDao.getAll() } returns listOf(
            queued("first", "group_a", 1),
            queued("second", "group_a", 2)
        )
        coEvery { contributionHandler.handle(any()) } returns
            Result.failure(IllegalArgumentException("rejected by policy"))

        repository.processOutbox()

        coVerify(exactly = 1) { contributionHandler.handle(match { it.id == "first" }) }
        coVerify(exactly = 0) { contributionHandler.handle(match { it.id == "second" }) }
    }

    @Test
    fun `a parked operation no longer blocks the operations behind it`() = runBlocking {
        val parked = queued("parked", "group_a", 1, retryCount = OutboxPolicy.MAX_PERMANENT_ATTEMPTS)
        val next = queued("next", "group_a", 2)
        coEvery { outboxDao.getAll() } returns listOf(parked, next)
        coEvery { contributionHandler.handle(any()) } returns Result.success(Unit)

        repository.processOutbox()

        coVerify(exactly = 0) { contributionHandler.handle(match { it.id == "parked" }) }
        coVerify(exactly = 1) { outboxDao.delete(next) }
    }

    @Test
    fun `the final rejected attempt parks the operation`() = runBlocking {
        val dying = queued(
            "dying",
            "group_a",
            1,
            retryCount = OutboxPolicy.MAX_PERMANENT_ATTEMPTS - 1
        )
        coEvery { outboxDao.getAll() } returns listOf(dying)
        coEvery { contributionHandler.handle(any()) } returns
            Result.failure(IllegalArgumentException("rejected by policy"))

        repository.processOutbox()

        val stored = slot<PendingOperationEntity>()
        coVerify(exactly = 1) { outboxDao.upsert(capture(stored)) }
        assertEquals(OutboxPolicy.MAX_PERMANENT_ATTEMPTS, stored.captured.retryCount)
        assertNotNull(stored.captured.lastError)
    }

    @Test
    fun `a queued payout status update is replayed instead of dead-lettered`() = runBlocking {
        val statusUpdate = PendingOperationEntity(
            id = "status-1",
            type = "payout_status_update",
            payload = """{"payout_id":"p-1","status":"GROUP_APPROVED"}""",
            groupId = null,
            createdAt = 1
        )
        coEvery { outboxDao.getAll() } returns listOf(statusUpdate)
        coEvery { payoutStatusUpdateHandler.handle(any()) } returns Result.success(Unit)

        repository.processOutbox()

        coVerify(exactly = 1) { payoutStatusUpdateHandler.handle(match { it.id == "status-1" }) }
        coVerify(exactly = 1) { outboxDao.delete(match { it.id == "status-1" }) }
        coVerify(exactly = 0) { outboxDao.upsert(match { it.retryCount > 0 }) }
    }

    @Test
    fun `a transient failure does not consume a permanent attempt`() = runBlocking {
        val pending = queued("pending", "group_a", 1)
        coEvery { outboxDao.getAll() } returns listOf(pending)
        coEvery { contributionHandler.handle(any()) } returns
            Result.failure(UnknownHostException("api.example.org"))

        repository.processOutbox()

        coVerify(exactly = 1) { outboxDao.upsert(match { it.retryCount == 0 }) }
    }
}

