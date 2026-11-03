package com.sanibonani.save.viewmodel

import android.util.Log
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.Member
import com.sanibonani.save.domain.repository.auth.SupabaseRepository
import com.sanibonani.save.domain.repository.group.GroupRepository
import com.sanibonani.save.domain.repository.member.MemberRepository
import com.sanibonani.save.domain.service.LocationService
import com.sanibonani.save.domain.usecase.auth.GetAttributableActorIdUseCase
import com.sanibonani.save.domain.usecase.group.GetGroupByIdUseCase
import com.sanibonani.save.domain.usecase.member.RegisterMemberUseCase
import com.sanibonani.save.features.member.presentation.MemberRegistrationViewModel
import com.sanibonani.save.service.UserProfileCacheService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.IOException

/**
 * The join form renders a message under each field it does not accept, and the ViewModel had never
 * written one: every rejection went into the banner at the bottom of the screen, so the field the
 * member has to fix stayed visually clean.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MemberRegistrationFieldErrorTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val memberRepo = mockk<MemberRepository>(relaxed = true)
    private val groupRepo = mockk<GroupRepository>(relaxed = true)
    private val supabaseRepo = mockk<SupabaseRepository>(relaxed = true)
    private val locationService = mockk<LocationService>(relaxed = true)
    private val profileCache = mockk<UserProfileCacheService>(relaxed = true)

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: MemberRegistrationViewModel

    // Luhn-valid per InputValidator.verifySAIdChecksum, DOB segment 800101.
    private val validId = "8001015009087"

    @Before
    fun setup() {
        mockkStatic(android.os.Looper::class)
        every { android.os.Looper.getMainLooper() } returns mockk(relaxed = true)

        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.e(any(), any()) } returns 0

        mockkStatic(FirebaseCrashlytics::class)
        every { FirebaseCrashlytics.getInstance() } returns mockk(relaxed = true)

        Dispatchers.setMain(testDispatcher)

        every { supabaseRepo.currentUserId } returns "user-1"

        viewModel = MemberRegistrationViewModel(
            registerMember = RegisterMemberUseCase(memberRepo, groupRepo, supabaseRepo),
            getGroupById = GetGroupByIdUseCase(groupRepo),
            getAttributableActorId = GetAttributableActorIdUseCase(supabaseRepo),
            locationService = locationService,
            userProfileCache = profileCache,
            memberContextCacheService = null
        )

        viewModel.onFieldChange("idNumber", validId)
        viewModel.onFieldChange("phone", "0821234567")
        viewModel.onFieldChange("email", "sipho@example.com")
        viewModel.onFieldChange("street", "12 Vilakazi Street")
        viewModel.onFieldChange("suburb", "Meadowlands")
        viewModel.onFieldChange("city", "Soweto")
        viewModel.onFieldChange("province", "Gauteng")
    }

    @After
    fun tearDown() {
        testDispatcher.scheduler.advanceUntilIdle()
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `a name the form rejects is blamed on the name field`() = runTest {
        viewModel.onFieldChange("fullName", "Bo")

        viewModel.submit("g1", null)
        advanceUntilIdle()

        assertEquals("Full Name must be at least 3 characters.", viewModel.registerState.value.fieldErrors["fullName"])
        assertNull("and not doubled up in the banner", viewModel.registerState.value.error)
        coVerify(exactly = 0) { memberRepo.registerMember(any(), any()) }
    }

    @Test
    fun `an id number that fails the checksum is blamed on the id field`() = runTest {
        viewModel.onFieldChange("fullName", "Sipho Dlamini")
        viewModel.onFieldChange("idNumber", "8001015009086")

        viewModel.submit("g1", null)
        advanceUntilIdle()

        assertTrue(
            "the field has to carry the rule: ${viewModel.registerState.value.fieldErrors}",
            viewModel.registerState.value.fieldErrors["idNumber"]?.contains("13 digits") == true
        )
        coVerify(exactly = 0) { memberRepo.registerMember(any(), any()) }
    }

    @Test
    fun `a malformed email is blamed on the email field`() = runTest {
        viewModel.onFieldChange("fullName", "Sipho Dlamini")
        viewModel.onFieldChange("email", "not-an-email")

        viewModel.submit("g1", null)
        advanceUntilIdle()

        assertEquals("Please enter a valid email address.", viewModel.registerState.value.fieldErrors["email"])
        coVerify(exactly = 0) { memberRepo.registerMember(any(), any()) }
    }

    @Test
    fun `correcting the blamed field re-opens the join button`() = runTest {
        viewModel.onFieldChange("fullName", "Bo")

        viewModel.submit("g1", null)
        advanceUntilIdle()
        assertTrue(viewModel.registerState.value.fieldErrors.containsKey("fullName"))
        assertTrue("a blamed field blocks the submit", !viewModel.registerState.value.canSubmit)

        viewModel.onFieldChange("fullName", "Sipho Dlamini")

        assertTrue(viewModel.registerState.value.fieldErrors.isEmpty())
        assertTrue(viewModel.registerState.value.canSubmit)
    }

    @Test
    fun `a registration the server refused stays in the banner`() = runTest {
        viewModel.onFieldChange("fullName", "Sipho Dlamini")
        coEvery { groupRepo.getGroupById(any()) } returns Result.failure(IOException("This group is full."))

        viewModel.submit("g1", null)
        advanceUntilIdle()

        assertTrue(
            "the refusal has to be reported: ${viewModel.registerState.value.error}",
            viewModel.registerState.value.error != null
        )
        assertTrue(
            "and nothing is blamed on a field the member typed correctly",
            viewModel.registerState.value.fieldErrors.isEmpty()
        )
    }

    @Test
    fun `a accepted registration clears the form into success`() = runTest {
        viewModel.onFieldChange("fullName", "Sipho Dlamini")
        val group = Group(
            id = "g1",
            name = "Alpha stokvel",
            registrationPaid = true,
            maxMembers = 0
        )
        coEvery { groupRepo.getGroupById(any()) } returns Result.success(group)
        coEvery { memberRepo.getMemberByUserId(any(), any()) } returns
            Result.failure(NoSuchElementException("no membership for this user in this group yet"))
        coEvery { memberRepo.registerMember(any(), any()) } answers {
            Result.success(firstArg<Member>())
        }

        viewModel.submit("g1", null)
        advanceUntilIdle()

        assertTrue(viewModel.registerState.value.success)
        assertTrue(viewModel.registerState.value.fieldErrors.isEmpty())
    }
}
