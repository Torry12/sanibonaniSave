package com.sanibonani.save.viewmodel

import android.util.Log
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.viewModelScope
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.auth.SupabaseRepository
import com.sanibonani.save.domain.repository.equity.ContributionRepository
import com.sanibonani.save.domain.repository.member.BeneficiaryRepository
import com.sanibonani.save.domain.repository.member.MemberDocumentRepository
import com.sanibonani.save.domain.repository.member.MemberRepository
import com.sanibonani.save.domain.repository.system.ExportRepository
import com.sanibonani.save.domain.usecase.credit.ValidateLoanEligibilityUseCase
import com.sanibonani.save.domain.usecase.member.UpdateMemberStatusUseCase
import com.sanibonani.save.domain.usecase.member.VerifyMemberDocumentUseCase
import com.sanibonani.save.domain.usecase.member.VerifyRelationalDocumentUseCase
import com.sanibonani.save.domain.usecase.system.SendNotificationUseCase
import com.sanibonani.save.domain.utils.PaymentCalculator
import com.sanibonani.save.features.admin.presentation.AdminMemberViewModel
import com.sanibonani.save.features.common.presentation.UIEvent
import com.sanibonani.save.service.AdminGroupContextCacheService
import com.sanibonani.save.service.CachedAdminGroupContext
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Pins the transient-flag behaviour of [AdminMemberViewModel]: every dialog action that
 * raises a "busy" flag must clear it on both the success and failure paths, otherwise the
 * member-detail dialog stays disabled until it is reopened.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AdminMemberViewModelTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val adminContextCacheService = mockk<AdminGroupContextCacheService>(relaxed = true)
    private val memberRepo = mockk<MemberRepository>(relaxed = true)
    private val contributionRepo = mockk<ContributionRepository>(relaxed = true)
    private val beneficiaryRepo = mockk<BeneficiaryRepository>(relaxed = true)
    private val memberDocumentRepo = mockk<MemberDocumentRepository>(relaxed = true)
    private val updateMemberStatusUseCase = mockk<UpdateMemberStatusUseCase>(relaxed = true)
    private val verifyMemberDocumentUseCase = mockk<VerifyMemberDocumentUseCase>(relaxed = true)
    private val verifyRelationalDocumentUseCase = mockk<VerifyRelationalDocumentUseCase>(relaxed = true)
    private val validateLoanEligibilityUseCase = mockk<ValidateLoanEligibilityUseCase>(relaxed = true)
    private val sendNotificationUseCase = mockk<SendNotificationUseCase>(relaxed = true)
    private val exportRepo = mockk<ExportRepository>(relaxed = true)
    private val supabaseRepo = mockk<SupabaseRepository>(relaxed = true)
    private val paymentCalculator = mockk<PaymentCalculator>(relaxed = true)

    private val contextsFlow = MutableStateFlow<Map<String, CachedAdminGroupContext>>(emptyMap())
    private val selectedGroupIdFlow = MutableStateFlow<String?>("g-1")

    private lateinit var viewModel: AdminMemberViewModel
    private val testDispatcher = StandardTestDispatcher()

    private val member = Member(id = "m-1", groupId = "g-1", fullName = "Thabo Mokoena")

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0

        mockkStatic(android.os.Looper::class)
        every { android.os.Looper.getMainLooper() } returns mockk(relaxed = true)

        Dispatchers.setMain(testDispatcher)

        every { adminContextCacheService.contexts } returns contextsFlow
        every { adminContextCacheService.selectedGroupId } returns selectedGroupIdFlow
        every { contributionRepo.observeGroupContributions(any()) } returns flowOf(Result.success(emptyList()))

        viewModel = AdminMemberViewModel(
            adminContextCacheService,
            memberRepo,
            contributionRepo,
            beneficiaryRepo,
            memberDocumentRepo,
            updateMemberStatusUseCase,
            verifyMemberDocumentUseCase,
            verifyRelationalDocumentUseCase,
            validateLoanEligibilityUseCase,
            sendNotificationUseCase,
            exportRepo,
            supabaseRepo,
            paymentCalculator
        )
    }

    @After
    fun tearDown() {
        viewModel.viewModelScope.cancel()
        testDispatcher.scheduler.advanceUntilIdle()
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun hydrateGroup() {
        contextsFlow.value = mapOf(
            "g-1" to CachedAdminGroupContext(
                group = Group(id = "g-1", name = "Test Group"),
                members = listOf(member),
                metrics = ActuarialMetrics(),
                feeStatus = AdminFeeState.DUE,
                isFullyHydrated = true
            )
        )
        testDispatcher.scheduler.advanceUntilIdle()
    }

    @Test
    fun `export failure clears the busy flag instead of leaving the dialog stuck`() = runTest {
        hydrateGroup()
        every { contributionRepo.observeMemberContributions("m-1", "g-1") } returns
            flowOf(Result.failure(IllegalStateException("disk full")))

        viewModel.exportMemberStatement(member, pdf = true)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isExporting)
        assertNull(viewModel.state.value.exportFile)
        assertTrue(viewModel.events.first() is UIEvent.ShowError)
    }

    @Test
    fun `export success publishes the file and clears the busy flag`() = runTest {
        hydrateGroup()
        val statement = File("statement.csv")
        every { contributionRepo.observeMemberContributions("m-1", "g-1") } returns
            flowOf(Result.success(emptyList()))
        coEvery { exportRepo.exportContributionsToCsv(any(), any(), any()) } returns Result.success(statement)

        viewModel.exportMemberStatement(member, pdf = false)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isExporting)
        assertEquals(statement, viewModel.state.value.exportFile)
    }

    @Test
    fun `sendMessage drives the sending flag and clears the draft on success`() = runTest {
        coEvery { sendNotificationUseCase(any(), any(), any(), any(), any()) } returns Result.success(Unit)

        viewModel.sendMessage("Hello", "m-1")
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isSendingMessage)
        assertTrue(viewModel.state.value.messageSentSuccess)
        assertEquals("", viewModel.state.value.messageText)
    }

    @Test
    fun `sendMessage failure clears the sending flag without claiming success`() = runTest {
        coEvery { sendNotificationUseCase(any(), any(), any(), any(), any()) } returns
            Result.failure(IllegalStateException("offline"))

        viewModel.sendMessage("Hello", "m-1")
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isSendingMessage)
        assertFalse(viewModel.state.value.messageSentSuccess)
    }

    @Test
    fun `selectMember resets the previous member's message state`() = runTest {
        coEvery { sendNotificationUseCase(any(), any(), any(), any(), any()) } returns Result.success(Unit)
        viewModel.sendMessage("Hello", "m-1")
        advanceUntilIdle()
        assertTrue(viewModel.state.value.messageSentSuccess)

        viewModel.selectMember(null)
        advanceUntilIdle()

        assertNull(viewModel.state.value.selectedMember)
        assertFalse(viewModel.state.value.messageSentSuccess)
        assertEquals("", viewModel.state.value.messageText)
    }

    @Test
    fun `updateBankingDetails reports failures instead of silently dropping them`() = runTest {
        coEvery { memberRepo.updateMemberBankingDetails(any(), any(), any(), any()) } returns
            Result.failure(IllegalStateException("rejected"))

        viewModel.updateBankingDetails("m-1", "FNB", "123", "250655")
        advanceUntilIdle()

        assertTrue(viewModel.events.first() is UIEvent.ShowError)
    }

    @Test
    fun `updateBeneficiary rejects an invalid SA ID before calling the repository`() = runTest {
        val existing = Beneficiary(id = "b-1", groupId = "g-1", memberId = "m-1", fullName = "Old Name")

        viewModel.updateBeneficiary(existing, "New Name", "123", "Spouse", "", false)
        advanceUntilIdle()

        coVerify(exactly = 0) { beneficiaryRepo.updateBeneficiary(any()) }
        assertTrue(viewModel.events.first() is UIEvent.ShowError)
    }

    @Test
    fun `updateBeneficiary forwards the edited fields and keeps the record identity`() = runTest {
        val existing = Beneficiary(id = "b-1", groupId = "g-1", memberId = "m-1", fullName = "Old Name")
        coEvery { beneficiaryRepo.updateBeneficiary(any()) } returns Result.success(existing)
        val captured = slot<Beneficiary>()

        viewModel.updateBeneficiary(existing, " New Name ", "9001015000085", " Spouse ", "", true)
        advanceUntilIdle()

        coVerify { beneficiaryRepo.updateBeneficiary(capture(captured)) }
        assertEquals("b-1", captured.captured.id)
        assertEquals("g-1", captured.captured.groupId)
        assertEquals("m-1", captured.captured.memberId)
        assertEquals("New Name", captured.captured.fullName)
        assertEquals("9001015000085", captured.captured.idNumber)
        assertEquals("Spouse", captured.captured.relationship)
        assertNull(captured.captured.dateOfBirth)
        assertTrue(captured.captured.isOver65)
        val event = viewModel.events.first()
        assertTrue(event is UIEvent.ShowMessage)
        assertEquals("Beneficiary updated", (event as UIEvent.ShowMessage).message)
    }
}
