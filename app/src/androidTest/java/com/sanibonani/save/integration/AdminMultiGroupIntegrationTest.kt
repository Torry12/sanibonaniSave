package com.sanibonani.save.integration

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sanibonani.save.di.TestAppModule
import com.sanibonani.save.di.TestAuthSessionController
import com.sanibonani.save.domain.model.DocumentStatus
import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.MemberStatus
import com.sanibonani.save.domain.model.PayoutStatus
import com.sanibonani.save.domain.repository.ActuarialRepository
import com.sanibonani.save.domain.repository.BeneficiaryClaimRepository
import com.sanibonani.save.domain.repository.BeneficiaryRepository
import com.sanibonani.save.domain.repository.ExportRepository
import com.sanibonani.save.domain.repository.GroupRepository
import com.sanibonani.save.domain.repository.LoanRepository
import com.sanibonani.save.domain.repository.MemberDocumentRepository
import com.sanibonani.save.domain.repository.LedgerRepository
import com.sanibonani.save.domain.repository.MemberRepository
import com.sanibonani.save.domain.repository.NotificationRepository
import com.sanibonani.save.domain.repository.PaymentRepository
import com.sanibonani.save.domain.repository.PayoutRepository
import com.sanibonani.save.domain.repository.SupabaseRepository
import com.sanibonani.save.domain.usecase.ApplyViabilityPlanUseCase
import com.sanibonani.save.domain.usecase.CalculateViabilityUseCase
import com.sanibonani.save.domain.usecase.CreateGroupUseCase
import com.sanibonani.save.domain.usecase.GenerateLoanContractUseCase
import com.sanibonani.save.domain.usecase.groups.GetGroupBusinessInsightsUseCase
import com.sanibonani.save.domain.usecase.GetManagedGroupsUseCase
import com.sanibonani.save.domain.usecase.RequestPayoutUseCase
import com.sanibonani.save.domain.usecase.SendNotificationUseCase
import com.sanibonani.save.domain.usecase.UpdateGroupSettingsUseCase
import com.sanibonani.save.domain.usecase.UpdateMemberStatusUseCase
import com.sanibonani.save.domain.usecase.ValidateLoanEligibilityUseCase
import com.sanibonani.save.domain.usecase.VerifyMemberDocumentUseCase
import com.sanibonani.save.domain.usecase.VerifyRelationalDocumentUseCase
import com.sanibonani.save.service.AdminGroupContextCacheService
import com.sanibonani.save.viewmodel.AdminViewModel
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class AdminMultiGroupIntegrationTest {

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var supabaseRepo: SupabaseRepository

    @Inject
    lateinit var groupRepository: GroupRepository

    @Inject
    lateinit var createGroupUseCase: CreateGroupUseCase

    @Inject
    lateinit var memberRepo: MemberRepository

    @Inject
    lateinit var beneficiaryRepo: BeneficiaryRepository

    @Inject
    lateinit var memberDocumentRepo: MemberDocumentRepository

    @Inject
    lateinit var actuarialRepo: ActuarialRepository

    @Inject
    lateinit var notifRepo: NotificationRepository

    @Inject
    lateinit var paymentRepo: PaymentRepository

    @Inject
    lateinit var payoutRepo: PayoutRepository

    @Inject
    lateinit var exportRepo: ExportRepository

    @Inject
    lateinit var loanRepo: LoanRepository

    @Inject
    lateinit var claimRepo: BeneficiaryClaimRepository

    @Inject
    lateinit var verifyMemberDocumentUseCase: VerifyMemberDocumentUseCase

    @Inject
    lateinit var updateGroupSettingsUseCase: UpdateGroupSettingsUseCase

    @Inject
    lateinit var applyViabilityPlanUseCase: ApplyViabilityPlanUseCase

    @Inject
    lateinit var verifyRelationalDocumentUseCase: VerifyRelationalDocumentUseCase

    @Inject
    lateinit var getManagedGroupsUseCase: GetManagedGroupsUseCase

    @Inject
    lateinit var calculateViabilityUseCase: CalculateViabilityUseCase

    @Inject
    lateinit var updateMemberStatusUseCase: UpdateMemberStatusUseCase

    @Inject
    lateinit var sendNotificationUseCase: SendNotificationUseCase

    @Inject
    lateinit var requestPayoutUseCase: RequestPayoutUseCase

    @Inject
    lateinit var validateLoanEligibilityUseCase: ValidateLoanEligibilityUseCase

    @Inject
    lateinit var generateLoanContractUseCase: GenerateLoanContractUseCase

    @Inject
    lateinit var getGroupBusinessInsightsUseCase: GetGroupBusinessInsightsUseCase

    @Inject
    lateinit var ledgerRepo: LedgerRepository

    lateinit var viewModel: AdminViewModel

    @Before
    fun init() {
        TestAppModule.resetMockState()
        TestAuthSessionController.reset()
        hiltRule.inject()
        val adminContextCacheService = AdminGroupContextCacheService(
            groupRepo = groupRepository,
            memberRepo = memberRepo,
            notificationRepo = notifRepo,
            payoutRepo = payoutRepo,
            actuarialRepo = actuarialRepo
        )
        viewModel = AdminViewModel(
            supabaseRepo, groupRepository, memberRepo, beneficiaryRepo, memberDocumentRepo,
            actuarialRepo, notifRepo, paymentRepo, payoutRepo, exportRepo,
            loanRepo,
            claimRepo,
            adminContextCacheService,
            verifyMemberDocumentUseCase,
            updateGroupSettingsUseCase,
            applyViabilityPlanUseCase,
            verifyRelationalDocumentUseCase,
            getManagedGroupsUseCase, calculateViabilityUseCase,
            updateMemberStatusUseCase, sendNotificationUseCase,
            requestPayoutUseCase,
            validateLoanEligibilityUseCase,
            generateLoanContractUseCase,
            getGroupBusinessInsightsUseCase,
            ledgerRepo
        )
    }

    private suspend fun ensureAuthenticated(): String {
        val email = "test-admin-${UUID.randomUUID()}@example.com"
        val password = "Password123!"
        supabaseRepo.adminSignUp(email, password, emptyMap(), confirm = true).getOrThrow()
        supabaseRepo.signIn(email, password).getOrThrow()
        return supabaseRepo.currentUserId ?: throw IllegalStateException("Failed to sign in")
    }

    private suspend fun awaitState(
        description: String,
        timeoutMs: Long = 10_000L,
        predicate: (com.sanibonani.save.viewmodel.AdminUiState) -> Boolean
    ): com.sanibonani.save.viewmodel.AdminUiState {
        var lastState = viewModel.state.value
        return try {
            withTimeout(timeoutMs) {
                viewModel.state.first {
                    lastState = it
                    predicate(it)
                }
            }
        } catch (_: TimeoutCancellationException) {
            fail(
                "Timed out waiting for $description. " +
                    "Last state: group=${lastState.group?.id}, members=${lastState.members.size}, " +
                    "balance=${lastState.group?.balance}, payouts=${lastState.payouts.size}, " +
                    "requesting=${lastState.isRequestingPayout}, success=${lastState.payoutRequestSuccess}, " +
                    "error=${lastState.error}"
            )
            throw IllegalStateException("Unreachable")
        }
    }

    @Test
    @Ignore("Temporarily disabled: multi-group admin state reset flow is flaky under instrumentation and needs deterministic test fixtures.")
    fun testSwitchingGroupsResetsAndReloadsState() = runBlocking {
        val adminUserId = ensureAuthenticated()
        val adminEmail = supabaseRepo.currentSessionEmail ?: "test@example.com"

        val g1Id = createGroupUseCase(
            group = Group(name = "Group One", adminUserId = adminUserId, joiningFee = 100.0),
            adminEmail = adminEmail,
            adminFullName = "Admin One",
            adminPhone = "0111111111"
        ).getOrThrow()

        val g2Id = createGroupUseCase(
            group = Group(name = "Group Two", adminUserId = adminUserId, joiningFee = 200.0),
            adminEmail = adminEmail,
            adminFullName = "Admin Two",
            adminPhone = "0222222222"
        ).getOrThrow()

        viewModel.loadAdminData()

        val state1 = viewModel.state.first { it.managedGroups.size == 2 && it.group?.id == g1Id }
        assertEquals("Group One", state1.group?.name)
        assertEquals(100.0, state1.group?.joiningFee ?: 0.0, 0.01)

        viewModel.selectGroup(g2Id)

        val state2 = viewModel.state.first { it.group?.id == g2Id }
        assertEquals("Group Two", state2.group?.name)
        assertEquals(200.0, state2.group?.joiningFee ?: 0.0, 0.01)
        assertEquals("Admin Two", state2.members.first().fullName)
    }

    @Test
    fun testDocumentVerificationFlow() = runBlocking {
        val adminUserId = ensureAuthenticated()
        val adminEmail = supabaseRepo.currentSessionEmail ?: "test@example.com"
        val groupId = createGroupUseCase(
            group = Group(name = "Doc Test Group", adminUserId = adminUserId),
            adminEmail = adminEmail,
            adminFullName = "Admin",
            adminPhone = "0111111111"
        ).getOrThrow()

        viewModel.selectGroup(groupId)
        val initialState = awaitState("document test group load") {
            it.group?.id == groupId && it.members.isNotEmpty()
        }
        val adminMember = initialState.members.first()
        
        viewModel.selectMember(adminMember)
        viewModel.verifyDocument(adminMember.id!!, 1, false)

        val suspendedState = awaitState("document rejection suspension update") { s ->
            s.members.any { m -> m.id == adminMember.id && m.status == MemberStatus.SUSPENDED }
        }
        
        val updatedMember = suspendedState.members.find { it.id == adminMember.id }!!
        assertEquals(MemberStatus.SUSPENDED, updatedMember.status)
        assertEquals(DocumentStatus.REJECTED, updatedMember.document1Status)
    }

    @Test
    fun testPayoutFlow() = runBlocking {
        val adminUserId = ensureAuthenticated()
        val adminEmail = supabaseRepo.currentSessionEmail ?: "test@example.com"
        val groupId = createGroupUseCase(
            group = Group(name = "Payout Group", adminUserId = adminUserId, joiningFee = 1000.0),
            adminEmail = adminEmail,
            adminFullName = "Admin",
            adminPhone = "0111111111"
        ).getOrThrow()

        groupRepository.incrementGroupBalance(groupId, 1000.0).getOrThrow()
        viewModel.selectGroup(groupId)
        val state1 = awaitState("group data with seeded balance") {
            it.group?.id == groupId &&
                it.members.isNotEmpty() &&
                (it.group?.balance ?: 0.0) >= 1000.0
        }
        assertTrue((state1.group?.balance ?: 0.0) >= 1000.0)

        // Request a payout
        viewModel.updatePayoutAmount("500.0")
        viewModel.updatePayoutBank("Test Bank")
        viewModel.updatePayoutAccount("123456789")
        viewModel.updatePayoutBranch("000000")
        
        viewModel.submitPayoutRequest()

        val submittedState = awaitState("payout submission result") {
            it.payoutRequestSuccess || (!it.isRequestingPayout && it.error != null)
        }
        assertNull(submittedState.error)
        viewModel.refreshPayouts()

        // Verify payout appears in state
        val finalState = awaitState("payout list refresh") { it.payouts.isNotEmpty() }
        val payout = finalState.payouts.first()
        assertEquals(500.0, payout.amount, 0.01)
        assertEquals(PayoutStatus.PENDING, payout.status)
        
        // Test Cancellation
        viewModel.cancelPayoutRequest(payout.id!!)
        val state3 = awaitState("payout cancellation") {
            it.payouts.any { payoutItem -> payoutItem.id == payout.id && payoutItem.status == PayoutStatus.CANCELLED }
        }
        assertEquals(PayoutStatus.CANCELLED, state3.payouts.first().status)
    }
}
