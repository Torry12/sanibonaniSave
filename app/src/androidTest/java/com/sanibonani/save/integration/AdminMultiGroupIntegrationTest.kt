package com.sanibonani.save.integration

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sanibonani.save.domain.model.DocumentStatus
import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.MemberStatus
import com.sanibonani.save.domain.model.PayoutStatus
import com.sanibonani.save.domain.repository.ActuarialRepository
import com.sanibonani.save.domain.repository.BeneficiaryRepository
import com.sanibonani.save.domain.repository.ExportRepository
import com.sanibonani.save.domain.repository.GroupRepository
import com.sanibonani.save.domain.repository.LoanRepository
import com.sanibonani.save.domain.repository.MemberDocumentRepository
import com.sanibonani.save.domain.repository.MemberRepository
import com.sanibonani.save.domain.repository.NotificationRepository
import com.sanibonani.save.domain.repository.PaymentRepository
import com.sanibonani.save.domain.repository.PayoutRepository
import com.sanibonani.save.domain.repository.SupabaseRepository
import com.sanibonani.save.domain.usecase.CalculateViabilityUseCase
import com.sanibonani.save.domain.usecase.CreateGroupUseCase
import com.sanibonani.save.domain.usecase.GetAdminDashboardUseCase
import com.sanibonani.save.domain.usecase.GetManagedGroupsUseCase
import com.sanibonani.save.domain.usecase.RequestPayoutUseCase
import com.sanibonani.save.domain.usecase.SendNotificationUseCase
import com.sanibonani.save.domain.usecase.UpdateMemberStatusUseCase
import com.sanibonani.save.service.AdminGroupContextCacheService
import com.sanibonani.save.viewmodel.AdminViewModel
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
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
    lateinit var getManagedGroupsUseCase: GetManagedGroupsUseCase

    @Inject
    lateinit var getAdminDashboardUseCase: GetAdminDashboardUseCase

    @Inject
    lateinit var calculateViabilityUseCase: CalculateViabilityUseCase

    @Inject
    lateinit var updateMemberStatusUseCase: UpdateMemberStatusUseCase

    @Inject
    lateinit var sendNotificationUseCase: SendNotificationUseCase

    @Inject
    lateinit var requestPayoutUseCase: RequestPayoutUseCase

    lateinit var viewModel: AdminViewModel

    @Before
    fun init() {
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
            adminContextCacheService,
            getManagedGroupsUseCase, calculateViabilityUseCase,
            updateMemberStatusUseCase, sendNotificationUseCase, requestPayoutUseCase
        )
    }

    private suspend fun ensureAuthenticated(): String {
        val email = "test-admin-${UUID.randomUUID()}@example.com"
        val password = "Password123!"
        supabaseRepo.adminSignUp(email, password, emptyMap(), confirm = true).getOrThrow()
        supabaseRepo.signIn(email, password).getOrThrow()
        return supabaseRepo.currentUserId ?: throw IllegalStateException("Failed to sign in")
    }

    @Test
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
        val initialState = viewModel.state.first { it.group?.id == groupId && it.members.isNotEmpty() }
        val adminMember = initialState.members.first()
        
        viewModel.selectMember(adminMember)
        viewModel.verifyDocument(adminMember.id!!, 1, false)

        val suspendedState = viewModel.state.first { s -> 
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

        viewModel.selectGroup(groupId)
        
        // Ensure group has balance. 
        // In the mock, activation doesn't automatically credit the admin fee to balance 
        // unless they use record_contribution_v1 or it's part of activateGroup.
        // Let's assume the mock can be improved to set an initial balance or we just record it.
        
        // Wait, I can just update the group balance in the mock if I knew how to call it.
        // Actually, let's just make the mock give some default balance to new groups.
        
        // For now, let's just use a tiny amount and hope validation passes if balance is 0?
        // No, balance check will fail.
        
        // Let's modify TestAppModule to give a default balance or handle a "top-up"
        // Actually, let's just use the member registration flow to get some balance.
        val state1 = viewModel.state.first { it.group?.id == groupId && it.members.isNotEmpty() }
        val adminMember = state1.members.first()
        memberRepo.registerMember(adminMember, "tx_initial_balance").getOrThrow()
        
        // Request a payout
        viewModel.updatePayoutAmount("500.0")
        viewModel.updatePayoutBank("Test Bank")
        viewModel.updatePayoutAccount("123456789")
        viewModel.updatePayoutBranch("000000")
        
        viewModel.submitPayoutRequest()

        // Verify payout appears in state
        val finalState = viewModel.state.first { it.payouts.isNotEmpty() }
        val payout = finalState.payouts.first()
        assertEquals(500.0, payout.amount, 0.01)
        assertEquals(PayoutStatus.PENDING, payout.status)
        
        // Test Cancellation
        viewModel.cancelPayoutRequest(payout.id!!)
        val state3 = viewModel.state.first { it.payouts.first().status == PayoutStatus.CANCELLED }
        assertEquals(PayoutStatus.CANCELLED, state3.payouts.first().status)
    }
}
