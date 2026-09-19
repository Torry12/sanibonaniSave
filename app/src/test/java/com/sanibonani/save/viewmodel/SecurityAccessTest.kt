package com.sanibonani.save.viewmodel

import com.sanibonani.save.domain.model.UserRole
import com.sanibonani.save.domain.repository.*
import com.sanibonani.save.domain.usecase.CalculateGroupHealthScoreUseCase
import com.sanibonani.save.domain.usecase.ProcessBurialClaimUseCase
import com.sanibonani.save.domain.usecase.ProcessPayoutUseCase
import com.sanibonani.save.data.sync.RealtimeSyncManager
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SecurityAccessTest {

    private val platformRepo = mockk<PlatformRepository>(relaxed = true)
    private val payoutRepo = mockk<PayoutRepository>(relaxed = true)
    private val loanRepo = mockk<LoanRepository>(relaxed = true)
    private val processPayoutUseCase = mockk<ProcessPayoutUseCase>(relaxed = true)
    private val processBurialClaimUseCase = mockk<ProcessBurialClaimUseCase>(relaxed = true)
    private val claimRepo = mockk<BeneficiaryClaimRepository>(relaxed = true)
    private val memberRepo = mockk<MemberRepository>(relaxed = true)
    private val notifRepo = mockk<NotificationRepository>(relaxed = true)
    private val supabaseRepo = mockk<SupabaseRepository>(relaxed = true)
    private val platformConfigRepo = mockk<PlatformConfigRepository>(relaxed = true)
    private val ledgerRepo = mockk<LedgerRepository>(relaxed = true)
    private val reconRepo = mockk<ReconciliationRepository>(relaxed = true)
    private val auditRepo = mockk<AuditRepository>(relaxed = true)
    private val auditLogRepo = mockk<AuditLogRepository>(relaxed = true)
    private val calculateGroupHealthScoreUseCase = mockk<CalculateGroupHealthScoreUseCase>(relaxed = true)
    private val exportRepo = mockk<ExportRepository>(relaxed = true)
    private val dataVerificationService = mockk<com.sanibonani.save.domain.service.DataVerificationService>(relaxed = true)
    private val syncManager = mockk<RealtimeSyncManager>(relaxed = true)

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { supabaseRepo.sessionFlow } returns MutableStateFlow(null)
        every { syncManager.syncMetrics } returns MutableStateFlow(emptyMap())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `PlatformAdminViewModel blocks access for non-admin user`() = runTest {
        // Given: User is a MEMBER, not a PLATFORM_ADMIN
        coEvery { supabaseRepo.getUserRole() } returns UserRole.MEMBER

        val viewModel = PlatformAdminViewModel(
            platformRepo, payoutRepo, loanRepo, processPayoutUseCase, claimRepo,
            processBurialClaimUseCase, memberRepo, notifRepo, supabaseRepo,
            platformConfigRepo, ledgerRepo, reconRepo, auditRepo, auditLogRepo,
            calculateGroupHealthScoreUseCase, exportRepo, dataVerificationService, syncManager
        )

        advanceUntilIdle()

        // Then: isAuthorized should be false
        assertFalse("ViewModel should block access for non-admin roles", viewModel.state.value.isAuthorized)
    }

    @Test
    fun `PlatformAdminViewModel allows access for admin user`() = runTest {
        // Given: User is a PLATFORM_ADMIN
        coEvery { supabaseRepo.getUserRole() } returns UserRole.PLATFORM_ADMIN

        val viewModel = PlatformAdminViewModel(
            platformRepo, payoutRepo, loanRepo, processPayoutUseCase, claimRepo,
            processBurialClaimUseCase, memberRepo, notifRepo, supabaseRepo,
            platformConfigRepo, ledgerRepo, reconRepo, auditRepo, auditLogRepo,
            calculateGroupHealthScoreUseCase, exportRepo, dataVerificationService, syncManager
        )

        advanceUntilIdle()

        // Then: isAuthorized should be true
        assertTrue("ViewModel should allow access for Platform Admin role", viewModel.state.value.isAuthorized)
    }
}
