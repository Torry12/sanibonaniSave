package com.sanibonani.save.viewmodel

import android.util.Log
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.admin.*
import com.sanibonani.save.domain.repository.auth.*
import com.sanibonani.save.domain.repository.payment.BankRepository
import com.sanibonani.save.domain.repository.payment.FinancialProviderRepository
import com.sanibonani.save.domain.repository.treasury.LedgerRepository
import com.sanibonani.save.domain.repository.disbursement.PayoutRepository
import com.sanibonani.save.domain.repository.treasury.ReconciliationRepository
import com.sanibonani.save.domain.repository.group.*
import com.sanibonani.save.domain.repository.member.*
import com.sanibonani.save.domain.repository.system.*
import com.sanibonani.save.domain.usecase.payment.SyncBankTransactionsUseCase
import com.sanibonani.save.domain.usecase.disbursement.RequestPayoutUseCase
import com.sanibonani.save.domain.usecase.credit.ProcessLoanDisbursementUseCase
import com.sanibonani.save.domain.usecase.member.ProcessBurialClaimUseCase
import com.sanibonani.save.service.AdminGroupContextCacheService
import com.sanibonani.save.service.CachedAdminGroupContext
import com.sanibonani.save.features.admin.presentation.AdminFinancialViewModel
import com.sanibonani.save.features.admin.presentation.actions.AdminBankActions
import com.sanibonani.save.features.admin.presentation.actions.AdminClaimActions
import com.sanibonani.save.features.admin.presentation.actions.AdminLedgerActions
import com.sanibonani.save.features.admin.presentation.actions.AdminLoanActions
import com.sanibonani.save.features.admin.presentation.actions.AdminPayoutActions
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.sanibonani.save.domain.repository.credit.LoanRepository

@OptIn(ExperimentalCoroutinesApi::class)
class AdminFinancialViewModelTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val groupRepo = mockk<GroupRepository>(relaxed = true)
    private val memberRepo = mockk<MemberRepository>(relaxed = true)
    private val notifRepo = mockk<NotificationRepository>(relaxed = true)
    private val payoutRepo = mockk<PayoutRepository>(relaxed = true)
    private val actuarialRepo = mockk<ActuarialRepository>(relaxed = true)
    private val ledgerRepo = mockk<LedgerRepository>(relaxed = true)
    private val loanRepo = mockk<LoanRepository>(relaxed = true)
    private val claimRepo = mockk<BeneficiaryClaimRepository>(relaxed = true)
    private val reconRepo = mockk<ReconciliationRepository>(relaxed = true)
    private val bankRepo = mockk<BankRepository>(relaxed = true)
    private val exportRepo = mockk<ExportRepository>(relaxed = true)
    private val syncBankTransactionsUseCase = mockk<SyncBankTransactionsUseCase>(relaxed = true)
    private val linkBankAccountUseCase = mockk<com.sanibonani.save.domain.usecase.payment.LinkBankAccountUseCase>(relaxed = true)
    private val finalizeBankConnectionUseCase = mockk<com.sanibonani.save.domain.usecase.payment.FinalizeBankConnectionUseCase>(relaxed = true)
    private val financialProviderRepo = mockk<FinancialProviderRepository>(relaxed = true)
    private val supabaseRepo = mockk<SupabaseRepository>(relaxed = true)

    private val requestPayoutUseCase = mockk<RequestPayoutUseCase>(relaxed = true)
    private val processLoanDecisionUseCase = mockk<com.sanibonani.save.domain.usecase.credit.ProcessLoanDecisionUseCase>(relaxed = true)
    private val processLoanDisbursementUseCase = mockk<ProcessLoanDisbursementUseCase>(relaxed = true)
    private val processBurialClaimUseCase = mockk<ProcessBurialClaimUseCase>(relaxed = true)

    private val adminContextCacheService = mockk<AdminGroupContextCacheService>(relaxed = true)
    private val selectedGroupIdFlow = MutableStateFlow<String?>(null)
    private val contextsFlow = MutableStateFlow<Map<String, CachedAdminGroupContext>>(emptyMap())
    private val testDispatcher = StandardTestDispatcher()

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
        val mockCrashlytics = mockk<FirebaseCrashlytics>(relaxed = true)
        every { FirebaseCrashlytics.getInstance() } returns mockCrashlytics

        Dispatchers.setMain(testDispatcher)
        every { supabaseRepo.sessionFlow } returns MutableStateFlow(null)

        every { adminContextCacheService.selectedGroupId } returns selectedGroupIdFlow
        every { adminContextCacheService.contexts } returns contextsFlow
        every { adminContextCacheService.selectGroup(any()) } answers { selectedGroupIdFlow.value = it.invocation.args[0] as String }

        // Avoid NoSuchElementException (no longer needed if cache service is mocked,
        // but still good practice for other repo calls if they are used)
        coEvery { memberRepo.getGroupMembers(any()) } returns flowOf(Result.success(emptyList()))
        coEvery { notifRepo.observeNotifications(any()) } returns flowOf(Result.success(emptyList()))
        coEvery { payoutRepo.observePayouts(any()) } returns flowOf(Result.success(emptyList()))
        coEvery { actuarialRepo.computeMetrics(any()) } returns Result.success(mockk(relaxed = true))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun createViewModel() = AdminFinancialViewModel(
        adminContextCacheService,
        ledgerRepo,
        reconRepo,
        bankRepo,
        payoutRepo,
        loanRepo,
        claimRepo,
        financialProviderRepo,
        supabaseRepo,
        AdminLoanActions(
            processLoanDecisionUseCase,
            processLoanDisbursementUseCase,
            supabaseRepo,
            adminContextCacheService
        ),
        AdminClaimActions(
            processBurialClaimUseCase,
            claimRepo,
            supabaseRepo,
            adminContextCacheService
        ),
        AdminPayoutActions(
            requestPayoutUseCase,
            payoutRepo,
            adminContextCacheService
        ),
        AdminBankActions(
            syncBankTransactionsUseCase,
            linkBankAccountUseCase,
            finalizeBankConnectionUseCase,
            bankRepo,
            adminContextCacheService
        ),
        AdminLedgerActions(
            reconRepo,
            bankRepo,
            ledgerRepo,
            exportRepo,
            adminContextCacheService
        )
    )

    @Test
    fun `submitPayoutRequest calls use case and updates state`() = runTest {
        val groupId = "group-1"
        adminContextCacheService.selectGroup(groupId)

        coEvery { requestPayoutUseCase(groupId = groupId, memberId = null, requestedAmount = Money(1500.0)) } returns Result.success("payout-1")

        val vm = createViewModel()

        vm.updatePayoutAmount("1500")
        vm.submitPayoutRequest()

        advanceUntilIdle()

        coVerify(exactly = 1) { requestPayoutUseCase(groupId = groupId, memberId = null, requestedAmount = Money(1500.0)) }
        assertEquals(true, vm.state.value.payoutRequestSuccess)
    }

    @Test
    fun `approveAndEscalatePayoutRequest updates status via repository`() = runTest {
        val payoutId = "payout-1"
        coEvery { payoutRepo.updatePayoutStatus(payoutId, PayoutStatus.GROUP_APPROVED) } returns Result.success(Unit)

        val vm = createViewModel()

        vm.approveAndEscalatePayoutRequest(payoutId)
        advanceUntilIdle()

        coVerify(exactly = 1) { payoutRepo.updatePayoutStatus(payoutId, PayoutStatus.GROUP_APPROVED) }
        assertEquals("Request escalated for platform approval", vm.state.value.successMessage)
    }

    @Test
    fun `refreshBankData calls sync use case`() = runTest {
        val groupId = "group-1"
        val providerId = "bank-1"
        val mockGroup = Group(id = groupId, name = "Test Group", financialProviderId = providerId)

        // Populate mocked cache service flows
        val mockContext = CachedAdminGroupContext(group = mockGroup)
        contextsFlow.value = mapOf(groupId to mockContext)
        selectedGroupIdFlow.value = groupId

        coEvery { syncBankTransactionsUseCase(groupId) } returns Result.success(Unit)

        val vm = createViewModel()
        advanceUntilIdle() // Propagate context to UI state

        vm.refreshBankData()
        advanceUntilIdle()

        coVerify(exactly = 1) { syncBankTransactionsUseCase(groupId) }
        assertEquals(true, vm.state.value.bankRefreshSuccess)
    }
}
