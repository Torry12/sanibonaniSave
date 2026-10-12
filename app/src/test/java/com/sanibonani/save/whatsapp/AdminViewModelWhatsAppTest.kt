package com.sanibonani.save.whatsapp

import android.util.Log
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.admin.*
import com.sanibonani.save.domain.repository.auth.*
import com.sanibonani.save.domain.repository.equity.ContributionRepository
import com.sanibonani.save.domain.repository.disbursement.PayoutRepository
import com.sanibonani.save.domain.repository.group.*
import com.sanibonani.save.domain.repository.member.*
import com.sanibonani.save.domain.repository.system.*

import com.sanibonani.save.domain.usecase.member.*
import com.sanibonani.save.domain.usecase.credit.ValidateLoanEligibilityUseCase
import com.sanibonani.save.domain.usecase.system.*
import com.sanibonani.save.domain.utils.PaymentCalculator
import com.sanibonani.save.service.AdminGroupContextCacheService
import com.sanibonani.save.features.admin.presentation.AdminMemberViewModel
import com.sanibonani.save.domain.model.AuthSession
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

@OptIn(ExperimentalCoroutinesApi::class)
class AdminViewModelWhatsAppTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()

    private val groupRepo              = mockk<GroupRepository>(relaxed = true)
    private val memberRepo             = mockk<MemberRepository>(relaxed = true)
    private val contributionRepo       = mockk<ContributionRepository>(relaxed = true)
    private val beneficiaryRepo        = mockk<BeneficiaryRepository>(relaxed = true)
    private val memberDocumentRepo     = mockk<MemberDocumentRepository>(relaxed = true)
    private val actuarialRepo          = mockk<ActuarialRepository>(relaxed = true)
    private val payoutRepo             = mockk<PayoutRepository>(relaxed = true)
    private val notificationRepo       = mockk<NotificationRepository>(relaxed = true)
    private val exportRepo             = mockk<ExportRepository>(relaxed = true)
    private val supabaseRepo           = mockk<SupabaseRepository>(relaxed = true)
    private val paymentCalculator      = mockk<PaymentCalculator>(relaxed = true)

    private val adminContextCache      = AdminGroupContextCacheService(groupRepo, memberRepo, notificationRepo, payoutRepo, actuarialRepo)

    private val updateMemberStatus     = mockk<UpdateMemberStatusUseCase>(relaxed = true)
    private val verifyMemberDocument   = mockk<VerifyMemberDocumentUseCase>(relaxed = true)
    private val verifyRelationalDoc    = mockk<VerifyRelationalDocumentUseCase>(relaxed = true)
    private val validateLoanEligibility = mockk<ValidateLoanEligibilityUseCase>(relaxed = true)
    private val sendNotification       = mockk<SendNotificationUseCase>(relaxed = true)

    private lateinit var viewModel: AdminMemberViewModel

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.e(any(), any()) } returns 0

        mockkStatic(FirebaseCrashlytics::class)
        every { FirebaseCrashlytics.getInstance() } returns mockk(relaxed = true)

        Dispatchers.setMain(testDispatcher)

        viewModel = AdminMemberViewModel(
            adminContextCache,
            memberRepo,
            contributionRepo,
            beneficiaryRepo,
            memberDocumentRepo,
            updateMemberStatus,
            verifyMemberDocument,
            verifyRelationalDoc,
            validateLoanEligibility,
            sendNotification,
            exportRepo,
            supabaseRepo,
            paymentCalculator
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `sendMessage success sets messageSentSuccess`() = runTest {
        val groupId = "g1"
        val memberId = "m1"
        adminContextCache.selectGroup(groupId)

        coEvery { sendNotification(groupId, any(), memberId, any(), any()) } returns Result.success(Unit)

        viewModel.updateNotifChannel(NotifChannel.WHATSAPP)
        viewModel.sendMessage("Test message", memberId)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.messageSentSuccess)
        assertNull(viewModel.state.value.error)
    }
}
