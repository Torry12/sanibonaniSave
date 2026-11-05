package com.sanibonani.save.data.repository

import com.sanibonani.save.data.repository.system.SyncRepositoryImpl
import com.sanibonani.save.data.local.PendingOperationDao
import com.sanibonani.save.data.local.PendingOperationEntity
import com.sanibonani.save.data.local.SanibonaniDatabase
import com.sanibonani.save.data.repository.system.BeneficiaryClaimOutboxHandler
import com.sanibonani.save.data.repository.system.ContributionOutboxHandler
import com.sanibonani.save.data.repository.system.DisbursementOutboxHandler
import com.sanibonani.save.data.repository.system.LoanRepaymentOutboxHandler
import com.sanibonani.save.data.repository.system.OutboxPolicy
import com.sanibonani.save.data.repository.system.PayoutRequestOutboxHandler
import com.sanibonani.save.data.repository.system.PayoutStatusUpdateOutboxHandler
import com.sanibonani.save.domain.model.DomainPillar
import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.Member
import com.sanibonani.save.domain.model.UserRole
import com.sanibonani.save.domain.repository.group.GroupRepository
import com.sanibonani.save.domain.repository.member.MemberRepository
import com.sanibonani.save.domain.repository.system.NotificationRepository
import com.sanibonani.save.domain.repository.auth.SupabaseRepository
import com.sanibonani.save.domain.model.SyncStatus
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.spyk
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.net.UnknownHostException

class SyncRepositoryImplTest {

    private val supabase = mockk<SupabaseClient>(relaxed = true)
    private val postgrest = mockk<Postgrest>(relaxed = true)
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
    private val beneficiaryClaimHandler = mockk<BeneficiaryClaimOutboxHandler>(relaxed = true)
    private val payoutStatusUpdateHandler = mockk<PayoutStatusUpdateOutboxHandler>(relaxed = true)

    private lateinit var repository: SyncRepositoryImpl

    private val requestedTables = mutableListOf<String>()

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
            beneficiaryClaimHandler = beneficiaryClaimHandler,
            payoutStatusUpdateHandler = payoutStatusUpdateHandler
        )
    }

    @After
    fun tearDownRemoteTables() {
        unmockkStatic("io.github.jan.supabase.postgrest.PostgrestKt")
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

    @Test
    fun `concurrent group refreshes join one in-flight hydration`() = runBlocking {
        // The admin and member caches each keep their own warm-up coordinator, so Support Mode can
        // ask for the same group twice at once. Only the platform-wide sync joined its in-flight
        // pass: two overlapping hydrations doubled every pull and then raced each other writing the
        // group's stale-sections verdict.
        every { supabaseRepo.currentUserId } returns "user_1"
        coEvery { groupRepo.getGroupById("group_1") } returns Result.success(
            Group(id = "group_1", name = "Group One")
        )
        coEvery { memberRepo.syncGroupMembers("group_1") } coAnswers {
            delay(150)
            Result.success(emptyList())
        }
        coEvery { notificationRepo.syncNotifications("group_1") } returns Result.success(Unit)
        val spy = spyk(repository)
        coEvery { spy.syncRefresh(any(), any()) } coAnswers {
            delay(50)
            Result.success(Unit)
        }

        val results = listOf(
            async { spy.syncGroupData("group_1") },
            async { spy.syncGroupData("group_1") },
            async { spy.syncGroupData("group_1") }
        ).awaitAll()

        assertTrue(results.all { it.isSuccess })
        coVerify(exactly = 1) { memberRepo.syncGroupMembers("group_1") }
        coVerify(exactly = 1) { groupRepo.getGroupById("group_1") }
        coVerify(exactly = 1) { spy.syncRefresh(DomainPillar.TREASURY, "group_1") }
    }

    @Test
    fun `a manual group refresh pulls every pillar regardless of role`() = runBlocking {
        every { supabaseRepo.currentUserId } returns "user_1"
        coEvery { supabaseRepo.getUserRole() } returns UserRole.MEMBER
        coEvery { groupRepo.getGroupById("group_1") } returns Result.success(
            Group(id = "group_1", name = "Group One")
        )
        coEvery { memberRepo.syncGroupMembers("group_1") } returns Result.success(emptyList())
        coEvery { notificationRepo.syncNotifications("group_1") } returns Result.success(Unit)
        val spy = spyk(repository)
        coEvery { spy.syncRefresh(any(), any()) } returns Result.success(Unit)

        assertTrue(spy.syncGroupData("group_1").isSuccess)

        DomainPillar.entries
            .filter { it != DomainPillar.GLOBAL }
            .forEach { pillar ->
                coVerify(exactly = 1) { spy.syncRefresh(pillar, "group_1") }
            }
        coVerify(exactly = 0) { spy.syncRefresh(DomainPillar.GLOBAL, any()) }
        // A user-initiated refresh is not role-tailored, so it must not branch on the role.
        coVerify(exactly = 0) { supabaseRepo.getUserRole() }
    }

    @Test
    fun `startup sync keeps the role-tailored pillar set`() = runBlocking {
        every { supabaseRepo.currentUserId } returns "user_1"
        coEvery { supabaseRepo.getUserRole() } returns UserRole.MEMBER
        coEvery { memberRepo.getMemberships("user_1") } returns Result.success(
            listOf(Member(id = "m1", groupId = "group_1"))
        )
        coEvery { groupRepo.getGroupsByAdmin("user_1") } returns Result.success(emptyList())
        coEvery { memberRepo.syncGroupMembers("group_1") } returns Result.success(emptyList())
        coEvery { notificationRepo.syncNotifications("group_1") } returns Result.success(Unit)
        val spy = spyk(repository)
        coEvery { spy.syncRefresh(any(), any()) } returns Result.success(Unit)

        assertTrue(spy.syncAllData().isSuccess)

        coVerify(exactly = 1) { spy.syncRefresh(DomainPillar.CREDIT, "group_1") }
        coVerify(exactly = 0) { spy.syncRefresh(DomainPillar.EQUITY, any()) }
        coVerify(exactly = 0) { spy.syncRefresh(DomainPillar.TREASURY, any()) }
        coVerify(exactly = 0) { groupRepo.getGroupById(any()) }
    }

    @Test
    fun `a pillar refresh reports the relation it could not pull`() = runBlocking {
        // The inner reads used to wrap themselves in results nobody looked at, so a pillar whose
        // ledger never arrived reported a clean refresh and the caller stamped the cache as fresh.
        stubRemoteTables()

        val outcome = repository.syncRefresh(DomainPillar.TREASURY, "group_1")

        assertTrue("an unreachable ledger is not a refreshed treasury", outcome.isFailure)
    }

    @Test
    fun `an unreachable bank connection does not suppress the transaction pull`() = runBlocking {
        val tables = stubRemoteTables()

        val outcome = repository.syncRefresh(DomainPillar.BANK, "group_1")

        assertEquals(
            "both relations of the pillar have to be attempted, not only the first",
            listOf("bank_connections", "bank_transactions"),
            tables
        )
        assertTrue(outcome.isFailure)
    }

    @Test
    fun `a manual group refresh fails when one pillar did not land`() = runBlocking {
        every { supabaseRepo.currentUserId } returns "user_1"
        coEvery { groupRepo.getGroupById("group_1") } returns Result.success(
            Group(id = "group_1", name = "Group One")
        )
        coEvery { memberRepo.syncGroupMembers("group_1") } returns Result.success(emptyList())
        coEvery { notificationRepo.syncNotifications("group_1") } returns Result.success(Unit)
        val spy = spyk(repository)
        coEvery { spy.syncRefresh(any(), any()) } returns Result.success(Unit)
        coEvery { spy.syncRefresh(DomainPillar.TREASURY, "group_1") } returns
            Result.failure(IOException("ledger unreachable"))

        val outcome = spy.syncGroupData("group_1")

        assertTrue("a refresh that never pulled the ledger cannot claim a fresh book", outcome.isFailure)
    }

    @Test
    fun `the boot sweep keeps hydrating the other groups when one pillar is missing`() = runBlocking {
        // Startup is best-effort: one group that will not answer must not strand the user on an
        // error, because the local cache is what an offline app is meant to open on.
        every { supabaseRepo.currentUserId } returns "user_1"
        coEvery { supabaseRepo.getUserRole() } returns UserRole.MEMBER
        coEvery { memberRepo.getMemberships("user_1") } returns Result.success(
            listOf(Member(id = "m1", groupId = "group_1"), Member(id = "m2", groupId = "group_2"))
        )
        coEvery { groupRepo.getGroupsByAdmin("user_1") } returns Result.success(emptyList())
        coEvery { memberRepo.syncGroupMembers(any()) } returns Result.success(emptyList())
        coEvery { notificationRepo.syncNotifications(any()) } returns Result.success(Unit)
        val spy = spyk(repository)
        coEvery { spy.syncRefresh(any(), any()) } returns Result.success(Unit)
        coEvery { spy.syncRefresh(any(), "group_1") } returns
            Result.failure(IOException("group_1 unreachable"))

        assertTrue("one group must not break the whole boot sync", spy.syncAllData().isSuccess)
        coVerify(exactly = 1) { spy.syncRefresh(DomainPillar.CREDIT, "group_2") }
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
    fun `a pull that lands clears only the relations it covered`() = runBlocking {
        // Boot is best-effort and its misses used to be logged and dropped. The app needs the same
        // misses after the pass, so a screen can tell a verified book from the cache it opened on.
        stubRemoteTables()
        coEvery { memberRepo.syncGroupMembers("group_1") } returns
            Result.failure(IOException("members offline"))

        assertTrue(repository.syncGroupData("group_1").isFailure)
        val outstanding = repository.staleGroups.value["group_1"]
        assertNotNull(outstanding)
        assertTrue(outstanding!!.containsAll(setOf("members", "the ledger")))

        // The member list answers on the next pass; the ledger is still unreachable, so the group
        // stops being outstanding for the members and stays outstanding for the ledger.
        coEvery { memberRepo.syncGroupMembers("group_1") } returns Result.success(emptyList())
        repository.syncGroupData("group_1")

        val afterSecondPass = repository.staleGroups.value["group_1"]
        assertNotNull(afterSecondPass)
        assertFalse("a relation that landed must stop being reported", afterSecondPass!!.contains("members"))
        assertTrue("a pillar this pass did not read cannot clear another pillar's miss",
            afterSecondPass.contains("the ledger"))
    }

    @Test
    fun `a sync that cannot start leaves no previous session's misses behind`() = runBlocking {
        every { supabaseRepo.currentUserId } returns "user_1"
        coEvery { groupRepo.getGroupById("group_1") } returns
            Result.failure(IOException("group offline"))
        repository.syncGroupData("group_1")
        assertTrue(repository.staleGroups.value.isNotEmpty())

        every { supabaseRepo.currentUserId } returns null

        assertTrue(repository.syncAllData().isFailure)
        assertTrue("a new session must not inherit what the last one could not verify",
            repository.staleGroups.value.isEmpty())
    }

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

    @Test
    fun `a bracketed refusal on replay consumes an attempt instead of replaying forever`() = runBlocking {
        // The RPC raises its code before its first write, and the HTTP layer reports the resulting 400
        // as an IOException. Classified as transport, the row never advanced toward the ceiling, so the
        // group's lane stayed halted behind money the server had already refused.
        val refused = queued("refused", "group_a", 1, retryCount = 2)
        coEvery { outboxDao.getAll() } returns listOf(refused)
        coEvery { contributionHandler.handle(any()) } returns
            Result.failure(IOException("[FIN_002] This contribution is already booked"))

        repository.processOutbox()

        coVerify(exactly = 1) { outboxDao.upsert(match { it.retryCount == 3 }) }
    }

    @Test
    fun `an operation type this build cannot handle parks on first sight`() = runBlocking {
        val retired = PendingOperationEntity(
            id = "adjust-1",
            type = "balance_adjustment",
            payload = "{}",
            groupId = "group_a",
            createdAt = 1
        )
        val behind = queued("behind", "group_a", 2)
        coEvery { outboxDao.getAll() } returns listOf(retired, behind)
        coEvery { contributionHandler.handle(any()) } returns Result.success(Unit)

        repository.processOutbox()

        val stored = slot<PendingOperationEntity>()
        coVerify(exactly = 1) { outboxDao.upsert(capture(stored)) }
        assertEquals(OutboxPolicy.MAX_PERMANENT_ATTEMPTS, stored.captured.retryCount)
        coVerify(exactly = 0) { contributionHandler.handle(any()) }

        // The parked row is skipped on the next pass, so the money queued behind it drains.
        coEvery { outboxDao.getAll() } returns listOf(stored.captured, behind)
        repository.processOutbox()
        coVerify(exactly = 1) { outboxDao.delete(behind) }
    }

    /**
     * The PostgREST builders are inline reified functions, so the server is stubbed where the table
     * handle is resolved. That is the only place the requested relation name is observable, and a
     * name the schema does not define fails with a 404 that [safeRunCatching] hides from the caller.
     */
    private fun stubRemoteTables(): List<String> {
        mockkStatic("io.github.jan.supabase.postgrest.PostgrestKt")
        every { supabase.postgrest } returns postgrest
        every { postgrest[any()] } answers {
            requestedTables += firstArg<String>()
            throw IOException("stubbed remote relation")
        }
        return requestedTables
    }

    @Test
    fun `the dynamics pillar reads the singular member behavior track table`() = runBlocking {
        val tables = stubRemoteTables()

        repository.syncRefresh(DomainPillar.DYNAMICS, "group_1")

        assertEquals(listOf("member_behavior_track"), tables)
        assertFalse(tables.contains("member_behavior_tracks"))
    }

    @Test
    fun `the disbursement pillar reads beneficiary claims once with no second guess`() = runBlocking {
        val tables = stubRemoteTables()

        repository.syncRefresh(DomainPillar.DISBURSEMENT, "group_1")

        assertEquals(listOf("payouts", "beneficiary_payout_claims"), tables)
    }

    @Test
    fun `every pillar reads tables the schema defines`() = runBlocking {
        val expected = mapOf(
            DomainPillar.EQUITY to listOf("contributions", "payments"),
            DomainPillar.CREDIT to listOf("loans"),
            DomainPillar.TREASURY to listOf("group_ledger"),
            // Each relation is read on its own outcome, so both are requested even though the
            // stubbed transport fails on the first.
            DomainPillar.BANK to listOf("bank_connections", "bank_transactions")
        )

        expected.forEach { (pillar, tables) ->
            requestedTables.clear()
            stubRemoteTables()

            repository.syncRefresh(pillar, "group_1")

            assertEquals(tables, requestedTables)
        }
    }

    @Test
    fun `the platform sweep never reads more than its budget at once`() = runBlocking {
        // The pass fired a lane per group and an async per pillar inside every lane, so a platform
        // admin's whole membership left for the server at once. Everything past the transport's
        // per-host dispatch cap waits in a queue no timeout in the app can see — and a screen's own
        // read waits in that same queue behind a sweep nobody asked for.
        every { supabaseRepo.currentUserId } returns "user_1"
        coEvery { supabaseRepo.getUserRole() } returns UserRole.PLATFORM_ADMIN
        coEvery { memberRepo.getMemberships("user_1") } returns Result.success(
            (1..4).map { Member(id = "m$it", groupId = "group_$it") }
        )
        coEvery { groupRepo.getGroupsByAdmin("user_1") } returns Result.success(emptyList())
        coEvery { groupRepo.getGroupById(any()) } coAnswers {
            counted { Result.success(Group(id = firstArg(), name = "Group")) }
        }
        coEvery { memberRepo.syncGroupMembers(any()) } coAnswers {
            counted { Result.success(emptyList()) }
        }
        coEvery { notificationRepo.syncNotifications(any()) } coAnswers {
            counted { Result.success(Unit) }
        }
        val spy = spyk(repository)
        coEvery { spy.syncRefresh(any(), any()) } coAnswers { counted { Result.success(Unit) } }

        assertTrue(spy.syncAllData().isSuccess)

        val peak = peakReadsInFlight.get()
        assertTrue("the sweep held $peak reads at once; it may not outstrip the transport", peak <= 3)
        assertTrue("a sweep that reads one relation at a time is not a sweep", peak > 1)
    }

    @Test
    fun `a group a screen opened is not queued behind the platform sweep`() = runBlocking {
        // Both passes used the same lanes, so a refresh that fills a screen waited on whatever the
        // background sweep happened to be holding, and a sweep stuck in one slow read stuck the
        // screen with it.
        every { supabaseRepo.currentUserId } returns "user_1"
        coEvery { supabaseRepo.getUserRole() } returns UserRole.PLATFORM_ADMIN
        coEvery { memberRepo.getMemberships("user_1") } returns Result.success(
            listOf(
                Member(id = "m1", groupId = "sweep_1"),
                Member(id = "m2", groupId = "sweep_2"),
                Member(id = "m3", groupId = "sweep_3")
            )
        )
        coEvery { groupRepo.getGroupsByAdmin("user_1") } returns Result.success(emptyList())
        coEvery { groupRepo.getGroupById(any()) } returns Result.success(Group(id = "g", name = "Group"))
        coEvery { notificationRepo.syncNotifications(any()) } returns Result.success(Unit)
        coEvery { memberRepo.syncGroupMembers("open_group") } returns Result.success(emptyList())
        val sweepIsBusy = CompletableDeferred<Unit>()
        coEvery { memberRepo.syncGroupMembers(match { it.startsWith("sweep_") }) } coAnswers {
            sweepHeld.incrementAndGet()
            sweepIsBusy.await()
            Result.success(emptyList())
        }
        val spy = spyk(repository)
        coEvery { spy.syncRefresh(any(), any()) } returns Result.success(Unit)

        val sweep = async { spy.syncAllData() }
        withTimeout(5_000) { while (sweepHeld.get() < 3) delay(10) }

        val attempted = runCatching { withTimeout(2_000) { spy.syncGroupData("open_group") } }

        assertTrue(
            "opening a group waited on the background sweep's budget: ${attempted.exceptionOrNull()}",
            attempted.isSuccess
        )
        assertTrue(attempted.getOrNull()!!.isSuccess)

        // Let the sweep's own lanes finish unwinding rather than leaving them parked on the gate.
        sweepIsBusy.complete(Unit)
        sweep.cancel()
    }

    @Test
    fun `a sweep that runs out of time answers instead of cancelling the screen that asked`() = runBlocking<Unit> {
        // The deadline was released through safeRunCatching, which rethrows cancellation, so the
        // caller's await() took the sweep's timeout for its own: no terminal status was ever
        // written and the banner stayed on the last progress the sweep had stamped.
        every { supabaseRepo.currentUserId } returns "user_1"
        coEvery { supabaseRepo.getUserRole() } returns UserRole.MEMBER
        coEvery { memberRepo.getMemberships("user_1") } returns Result.success(
            listOf(Member(id = "m1", groupId = "group_1"))
        )
        coEvery { groupRepo.getGroupsByAdmin("user_1") } returns Result.success(emptyList())
        coEvery { notificationRepo.syncNotifications(any()) } returns Result.success(Unit)
        val neverAnswers = CompletableDeferred<Unit>()
        coEvery { memberRepo.syncGroupMembers("group_1") } coAnswers {
            neverAnswers.await()
            Result.success(emptyList())
        }
        repository.sweepTimeoutMillis = 100

        val caller = async { runCatching { repository.syncAllData() } }
        val wrapped = withTimeout(5_000) { caller.await() }

        val outcome = wrapped.getOrElse { failure ->
            error("the sweep reported nothing to the coroutine that asked for it: $failure")
        }
        assertTrue("an expired sweep is a verdict, not a success", outcome.isFailure)
        assertTrue(
            "the sweep's deadline must not reach the caller as its own cancellation: ${outcome.exceptionOrNull()}",
            outcome.exceptionOrNull() !is CancellationException
        )
        assertTrue(
            "the sync banner needs a terminal status, not the last progress it stamped: ${repository.syncStatus.value}",
            repository.syncStatus.value is SyncStatus.Error
        )

        neverAnswers.complete(Unit)
    }

    private val readsInFlight = AtomicInteger()
    private val peakReadsInFlight = AtomicInteger()

    /** Counts a relation read so a test can prove the pass overlaps reads without flooding them. */
    private suspend fun <T> counted(read: suspend () -> T): T {
        val now = readsInFlight.incrementAndGet()
        peakReadsInFlight.updateAndGet { maxOf(it, now) }
        try {
            delay(20)
            return read()
        } finally {
            readsInFlight.decrementAndGet()
        }
    }

    private val sweepHeld = AtomicInteger()
}

