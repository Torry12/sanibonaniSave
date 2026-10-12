package com.sanibonani.save

import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.GroupType
import com.sanibonani.save.domain.model.Member
import com.sanibonani.save.domain.model.MemberStatus
import com.sanibonani.save.domain.model.Money
import com.sanibonani.save.domain.model.NotificationPref
import com.sanibonani.save.domain.repository.group.GroupRepository
import com.sanibonani.save.domain.repository.member.MemberRepository
import com.sanibonani.save.domain.repository.auth.SupabaseRepository
import com.sanibonani.save.domain.usecase.group.CreateGroupUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import com.sanibonani.save.domain.model.AdminFeeState

/**
 * Regression tests for the group-admin onboarding bug where
 * `CreateGroupUseCase` used a plain INSERT for the admin member row, but the
 * database trigger `tr_ensure_admin_is_member` had *already* inserted that row
 * when the group was created. The duplicate hit `UNIQUE(group_id, user_id)`
 * (PostgreSQL 23505), which `toUserMessage()` rendered as
 * "This record already exists. Please check your data and try again." That
 * made a successful creation look like a failure, and each retry minted a new
 * orphaned group.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CreateGroupAdminOnboardingTest {

    private val groupRepository = mockk<GroupRepository>(relaxed = true)
    private val memberRepository = mockk<MemberRepository>(relaxed = true)
    private val supabaseRepository = mockk<SupabaseRepository>(relaxed = true)

    private lateinit var useCase: CreateGroupUseCase

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        useCase = CreateGroupUseCase(groupRepository, memberRepository, supabaseRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun group() = Group(
        id = null,
        name = "Test Stokvel",
        type = GroupType.STOKVEL,
        province = "Gauteng",
        city = "Soweto",
        joiningFee = Money(200.0),
        monthlyContribution = Money(500.0),
        probationMonths = 3
    )

    @Test
    fun `admin onboarding uses upsert so the trigger-created row does not collide`() = runTest {
        coEvery { supabaseRepository.signUp(any(), any(), any()) } returns Result.success("user-1")
        coEvery { groupRepository.createGroup(any()) } returns Result.success("group-1")
        coEvery { memberRepository.upsertMember(any()) } returns Result.success(mockk(relaxed = true))

        val result = useCase(group = group(), adminEmail = "admin@test.com", adminPassword = "password123")

        assertTrue("Group creation should succeed", result.isSuccess)
        // The critical assertion: the admin member must be written with an UPSERT.
        coVerify(exactly = 1) { memberRepository.upsertMember(any()) }
        // ...and never with the plain insert that collided with the trigger.
        coVerify(exactly = 0) { memberRepository.registerMember(any(), any()) }
    }

    @Test
    fun `pre-existing trigger row is enriched rather than treated as an error`() = runTest {
        val groupId = "group-1"
        coEvery { groupRepository.createGroup(any()) } returns Result.success(groupId)

        // Simulate the row the trigger already wrote: placeholder name, no phone/email.
        val triggerRow = Member(
            id = "member-1",
            groupId = groupId,
            userId = "user-1",
            fullName = "Group Admin",
            phone = "",
            email = ""
        )
        coEvery { memberRepository.getMemberships(any()) } returns Result.success(listOf(triggerRow))
        coEvery { memberRepository.upsertMember(any()) } returns Result.success(mockk(relaxed = true))

        val result = useCase(
            group = group(),
            adminFullName = "Real Admin",
            adminPhone = "0712345678",
            adminEmail = null
        )

        assertTrue("An existing trigger row must not block group creation", result.isSuccess)
        coVerify(exactly = 1) { memberRepository.upsertMember(any()) }
    }

    @Test
    fun `member onboarding failure rolls back the group instead of orphaning it`() = runTest {
        val groupId = "group-1"
        coEvery { groupRepository.createGroup(any()) } returns Result.success(groupId)
        coEvery { memberRepository.upsertMember(any()) } returns
            Result.failure(Exception("network down"))
        coEvery { groupRepository.deleteGroup(groupId) } returns Result.success(Unit)

        val result = useCase(group = group(), adminFullName = "Admin", adminPhone = "0712345678")

        assertTrue("Creation should fail when onboarding fails", result.isFailure)
        coVerify(exactly = 1) { groupRepository.deleteGroup(groupId) }
    }

    @Test
    fun `group is left in place when the rollback itself fails`() = runTest {
        val groupId = "group-1"
        coEvery { groupRepository.createGroup(any()) } returns Result.success(groupId)
        coEvery { memberRepository.upsertMember(any()) } returns
            Result.failure(Exception("network down"))
        coEvery { groupRepository.deleteGroup(groupId) } returns
            Result.failure(Exception("delete forbidden"))

        // Must still surface the original failure, not the rollback failure.
        val result = useCase(group = group(), adminFullName = "Admin", adminPhone = "0712345678")

        assertTrue(result.isFailure)
        assertEquals("network down", result.exceptionOrNull()?.message)
    }

    @Test
    fun `role elevation failure is logged but does not abort group creation`() = runTest {
        coEvery { groupRepository.createGroup(any()) } returns Result.success("group-1")
        coEvery { memberRepository.upsertMember(any()) } returns Result.success(mockk(relaxed = true))
        coEvery { supabaseRepository.updateUserRole(any(), any(), any()) } returns
            Result.failure(Exception("claims update failed"))

        val result = useCase(group = group(), adminFullName = "Admin", adminPhone = "0712345678")

        // The group and membership are valid; losing the role claim is recoverable.
        assertTrue("A role-claim failure must not discard the created group", result.isSuccess)
    }
}
