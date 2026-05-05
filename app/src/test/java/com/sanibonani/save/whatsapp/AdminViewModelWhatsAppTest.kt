package com.sanibonani.save.whatsapp

import android.util.Log
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.*
import com.sanibonani.save.domain.usecase.*
import com.sanibonani.save.service.AdminGroupContextCacheService
import com.sanibonani.save.viewmodel.AdminViewModel
import io.github.jan.supabase.auth.user.UserSession
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Unit tests for the WhatsApp test-message functionality exposed through [AdminViewModel].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AdminViewModelWhatsAppTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()

    // All mocks use relaxed = true so unstubbed calls don't throw
    private val supabaseRepo           = mockk<SupabaseRepository>(relaxed = true)
    private val groupRepo              = mockk<GroupRepository>(relaxed = true)
    private val memberRepo             = mockk<MemberRepository>(relaxed = true)
    private val beneficiaryRepo        = mockk<BeneficiaryRepository>(relaxed = true)
    private val memberDocumentRepo     = mockk<MemberDocumentRepository>(relaxed = true)
    private val actuarialRepo          = mockk<ActuarialRepository>(relaxed = true)
    private val notifRepo              = mockk<NotificationRepository>(relaxed = true)
    private val paymentRepo            = mockk<PaymentRepository>(relaxed = true)
    private val payoutRepo             = mockk<PayoutRepository>(relaxed = true)
    private val exportRepo             = mockk<ExportRepository>(relaxed = true)
    private val loanRepo               = mockk<LoanRepository>(relaxed = true)
    private val getManagedGroups       = mockk<GetManagedGroupsUseCase>(relaxed = true)
    private val calculateViability     = mockk<CalculateViabilityUseCase>(relaxed = true)
    private val updateMemberStatus     = mockk<UpdateMemberStatusUseCase>(relaxed = true)
    private val sendNotification       = mockk<SendNotificationUseCase>(relaxed = true)
    private val requestPayout          = mockk<RequestPayoutUseCase>(relaxed = true)
    private val validateLoanEligibility = mockk<ValidateLoanEligibilityUseCase>(relaxed = true)

    // Use real cache service backed by relaxed mocks (matches MultiGroupTest pattern)
    private val adminContextCache by lazy {
        AdminGroupContextCacheService(
            groupRepo        = groupRepo,
            memberRepo       = memberRepo,
            notificationRepo = notifRepo,
            payoutRepo       = payoutRepo,
            actuarialRepo    = actuarialRepo
        )
    }

    private lateinit var viewModel: AdminViewModel

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) }                           returns 0
        every { Log.i(any<String>(), any<String>()) }                           returns 0
        every { Log.w(any<String>(), any<String>()) }                           returns 0
        every { Log.w(any<String>(), any<String>(), any<Throwable>()) }         returns 0
        every { Log.e(any<String>(), any<String>()) }                           returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) }         returns 0

        mockkStatic(FirebaseCrashlytics::class)
        val mockCrashlytics = mockk<FirebaseCrashlytics>(relaxed = true)
        every { FirebaseCrashlytics.getInstance() } returns mockCrashlytics

        Dispatchers.setMain(testDispatcher)

        // sessionFlow emits null → observeAdminData() never fires
        every { supabaseRepo.sessionFlow }   returns MutableStateFlow<UserSession?>(null)
        every { supabaseRepo.currentUserId } returns "admin_user"
        coEvery { supabaseRepo.getUserRole() } returns UserRole.GROUP_ADMIN

        // Default relaxed stubs for all observation flows
        every { groupRepo.observeGroupFeeStatus(any()) }  returns flowOf(AdminFeeState.DUE)
        every { groupRepo.observeGroup(any()) }           returns flowOf(Result.success(null))
        every { memberRepo.getGroupMembers(any()) }        returns flowOf(Result.success(emptyList()))
        every { memberRepo.getGroupContributions(any()) }  returns flowOf(Result.success(emptyList()))
        every { notifRepo.observeNotifications(any()) }   returns flowOf(Result.success(emptyList()))
        every { payoutRepo.observePayouts(any()) }         returns flowOf(Result.success(emptyList()))
        every { loanRepo.getGroupLoans(any()) }            returns flowOf(Result.success(emptyList()))
        coEvery { actuarialRepo.computeMetrics(any()) }   returns Result.success(ActuarialMetrics())
        coEvery { sendNotification(any(), any(), anyNullable(), any(), any()) } returns Result.success(Unit)

        viewModel = AdminViewModel(
            supabaseRepo, groupRepo, memberRepo, beneficiaryRepo, memberDocumentRepo,
            actuarialRepo, notifRepo, paymentRepo, payoutRepo, exportRepo, loanRepo,
            adminContextCache, getManagedGroups, calculateViability,
            updateMemberStatus, sendNotification, requestPayout, validateLoanEligibility
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    // ══════════════════════════════════════════════════════════════════════
    // Guard clauses
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun `sendWhatsAppTestToSelectedMember sets error when no member is selected`() = runTest {
        viewModel.sendWhatsAppTestToSelectedMember()
        advanceUntilIdle()

        assertNotNull("Error should be set when no member is selected", viewModel.state.value.error)
        coVerify(exactly = 0) { sendNotification(any(), any(), anyNullable(), any(), any()) }
    }

    @Test
    fun `sendWhatsAppTestToSelectedMember sets error when group not loaded`() = runTest {
        // Set a member but state.group remains null (no group injected)
        viewModel.selectMember(Member(id = "m1", groupId = "", fullName = "Alice"))
        advanceUntilIdle()

        viewModel.sendWhatsAppTestToSelectedMember()
        advanceUntilIdle()

        assertNotNull(viewModel.state.value.error)
        coVerify(exactly = 0) { sendNotification(any(), any(), anyNullable(), any(), any()) }
    }

    // ══════════════════════════════════════════════════════════════════════
    // Happy path
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun `sendWhatsAppTestToSelectedMember success sets whatsAppTestResult`() = runTest {
        injectMemberAndGroup()

        coEvery { sendNotification(any(), any(), anyNullable(), any(), any()) } returns Result.success(Unit)

        viewModel.sendWhatsAppTestToSelectedMember()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse("isSendingWhatsAppTest must be false after completion", state.isSendingWhatsAppTest)
        assertNotNull("whatsAppTestResult must be set on success", state.whatsAppTestResult)
        assertNull("No error on success", state.error)
        assertTrue(
            "Result message must convey success",
            state.whatsAppTestResult?.contains("success", ignoreCase = true) == true ||
            state.whatsAppTestResult?.contains("sent", ignoreCase = true) == true
        )
    }

    @Test
    fun `sendWhatsAppTestToSelectedMember failure sets error and clears result`() = runTest {
        injectMemberAndGroup()

        coEvery {
            sendNotification(any(), any(), anyNullable(), any(), any())
        } returns Result.failure(RuntimeException("WhatsApp API unavailable"))

        viewModel.sendWhatsAppTestToSelectedMember()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isSendingWhatsAppTest)
        assertNotNull("whatsAppTestResult must contain failure feedback", state.whatsAppTestResult)
        assertNotNull("Error must be set on failure", state.error)
        assertEquals(state.error, state.whatsAppTestResult)
    }

    @Test
    fun `sendWhatsAppTestToSelectedMember shows loading state while in flight`() = runTest {
        injectMemberAndGroup()

        coEvery {
            sendNotification(any(), any(), anyNullable(), any(), any())
        } coAnswers {
            kotlinx.coroutines.delay(200)
            Result.success(Unit)
        }

        viewModel.sendWhatsAppTestToSelectedMember()
        testDispatcher.scheduler.advanceTimeBy(50) // inside the delay

        assertTrue(
            "isSendingWhatsAppTest must be true while in flight",
            viewModel.state.value.isSendingWhatsAppTest
        )

        advanceUntilIdle()
        assertFalse(viewModel.state.value.isSendingWhatsAppTest)
    }

    // ══════════════════════════════════════════════════════════════════════
    // Channel / trigger / payload contract
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun `sendWhatsAppTestToSelectedMember uses WHATSAPP channel only`() = runTest {
        val member = Member(id = "m_ch", groupId = "g_ch", fullName = "Channel Test")
        injectMemberAndGroup(member = member, groupId = "g_ch")

        val channelSlot = slot<NotifChannel>()
        coEvery {
            sendNotification(any(), any(), anyNullable(), any(), capture(channelSlot))
        } returns Result.success(Unit)

        viewModel.sendWhatsAppTestToSelectedMember()
        advanceUntilIdle()

        assertEquals(NotifChannel.WHATSAPP, channelSlot.captured)
    }

    @Test
    fun `sendWhatsAppTestToSelectedMember uses CUSTOM trigger event`() = runTest {
        injectMemberAndGroup()

        val triggerSlot = slot<NotifEvent>()
        coEvery {
            sendNotification(any(), any(), anyNullable(), capture(triggerSlot), any())
        } returns Result.success(Unit)

        viewModel.sendWhatsAppTestToSelectedMember()
        advanceUntilIdle()

        assertEquals(NotifEvent.CUSTOM, triggerSlot.captured)
    }

    @Test
    fun `sendWhatsAppTestToSelectedMember targets correct groupId and memberId`() = runTest {
        val member = Member(id = "m_ids", groupId = "g_ids", fullName = "ID Verify Member")
        injectMemberAndGroup(member = member, groupId = "g_ids")

        val groupIdCapture  = slot<String>()
        val memberIdCapture = slot<String?>()
        coEvery {
            sendNotification(capture(groupIdCapture), any(), captureNullable(memberIdCapture), any(), any())
        } returns Result.success(Unit)

        viewModel.sendWhatsAppTestToSelectedMember()
        advanceUntilIdle()

        assertEquals("g_ids",  groupIdCapture.captured)
        assertEquals("m_ids", memberIdCapture.captured)
    }

    @Test
    fun `sendWhatsAppTestToSelectedMember message contains member full name`() = runTest {
        val member = Member(id = "m_name", groupId = "g_name", fullName = "Jane Dlamini")
        injectMemberAndGroup(member = member, groupId = "g_name")

        val messageSlot = slot<String>()
        coEvery {
            sendNotification(any(), capture(messageSlot), anyNullable(), any(), any())
        } returns Result.success(Unit)

        viewModel.sendWhatsAppTestToSelectedMember()
        advanceUntilIdle()

        assertTrue(
            "Message must contain member's full name",
            messageSlot.captured.contains("Jane Dlamini", ignoreCase = true)
        )
    }

    @Test
    fun `sendWhatsAppTestToSelectedMember message contains unique timestamp`() = runTest {
        injectMemberAndGroup()

        val messageSlot = slot<String>()
        coEvery {
            sendNotification(any(), capture(messageSlot), anyNullable(), any(), any())
        } returns Result.success(Unit)

        viewModel.sendWhatsAppTestToSelectedMember()
        advanceUntilIdle()

        assertTrue(
            "Debug message must include digits (Unix timestamp) for uniqueness",
            messageSlot.captured.any { it.isDigit() }
        )
    }

    // ══════════════════════════════════════════════════════════════════════
    // Helper
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Injects a group and member into the ViewModel state via the public API.
     * Stubs all flows that [AdminViewModel.selectGroup] triggers.
     */
    private fun TestScope.injectMemberAndGroup(
        member: Member = Member(id = "test_m1", groupId = "test_group", fullName = "Test Member"),
        groupId: String = "test_group"
    ) {
        val group = Group(id = groupId, name = "Test Group")

        every { groupRepo.observeGroup(groupId) }                       returns flowOf(Result.success(group))
        every { groupRepo.observeGroupFeeStatus(groupId) }              returns flowOf(AdminFeeState.PAID)
        every { memberRepo.getGroupMembers(groupId) }                    returns flowOf(Result.success(emptyList()))
        every { memberRepo.getGroupContributions(groupId) }              returns flowOf(Result.success(emptyList()))
        every { notifRepo.observeNotifications(groupId) }               returns flowOf(Result.success(emptyList()))
        every { payoutRepo.observePayouts(groupId) }                     returns flowOf(Result.success(emptyList()))
        every { loanRepo.getGroupLoans(groupId) }                        returns flowOf(Result.success(emptyList()))
        every { beneficiaryRepo.observeBeneficiaries(any()) }           returns flowOf(Result.success(emptyList()))
        every { memberDocumentRepo.observeMemberDocuments(any()) }      returns flowOf(Result.success(emptyList()))
        every { memberRepo.getMemberContributions(any(), any()) }        returns flowOf(Result.success(emptyList()))
        coEvery { validateLoanEligibility(any(), any()) }               returns
            ValidateLoanEligibilityUseCase.EligibilityResult.Eligible

        viewModel.selectGroup(groupId)
        advanceUntilIdle()
        viewModel.selectMember(member)
        advanceUntilIdle()
    }
}
