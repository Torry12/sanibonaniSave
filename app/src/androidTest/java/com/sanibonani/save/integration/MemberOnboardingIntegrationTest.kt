package com.sanibonani.save.integration

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.GroupRepository
import com.sanibonani.save.domain.repository.MemberRepository
import com.sanibonani.save.domain.repository.SupabaseRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject
import java.util.UUID

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class MemberOnboardingIntegrationTest {

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var memberRepository: MemberRepository

    @Inject
    lateinit var groupRepository: GroupRepository

    @Inject
    lateinit var supabaseRepo: SupabaseRepository

    @Before
    fun init() {
        hiltRule.inject()
    }

    private suspend fun ensureAuthenticated(): String {
        val email = "test-admin-${UUID.randomUUID()}@example.com"
        val password = "Password123!"
        // Use adminSignUp to bypass email confirmation
        supabaseRepo.adminSignUp(email, password, emptyMap(), confirm = true).getOrThrow()
        supabaseRepo.signIn(email, password).getOrThrow()
        return supabaseRepo.currentUserId ?: throw IllegalStateException("Failed to sign in")
    }

    @Test
    fun testMemberRegistrationFlowWithPayment() = runBlocking {
        // 0. Authenticate
        val adminUserId = ensureAuthenticated()
        
        // 1. Create a test group with a joining fee
        val groupName = "Integration Test Group ${UUID.randomUUID().toString().take(8)}"
        val group = Group(
            name = groupName,
            type = GroupType.STOKVEL,
            province = "Gauteng",
            city = "Johannesburg",
            township = "Soweto",
            description = "Test Description",
            joiningFee = 150.0,
            probationMonths = 3,
            adminUserId = adminUserId,
            registrationPaid = true // Group must be active to accept members
        )
        
        val groupId = groupRepository.createGroup(group).getOrThrow()
        
        // 2. Register a member without payment initially
        val userId = UUID.randomUUID().toString()
        val member = Member(
            groupId = groupId,
            userId = userId,
            fullName = "Test Member",
            idNumber = "9001015000081",
            phone = "0123456789",
            email = "test@example.com",
            notificationPref = NotificationPref.BOTH,
            status = MemberStatus.PENDING_PAYMENT
        )
        
        val registeredMember = memberRepository.registerMember(member, null).getOrThrow()
        assertEquals(MemberStatus.PENDING_PAYMENT, registeredMember.status)
        
        // 3. Complete registration with a transaction ID
        val transactionId = "yoco_tx_${UUID.randomUUID()}"
        val activatedMember = memberRepository.registerMember(registeredMember, transactionId).getOrThrow()
        
        // 4. Verify member is now in PROBATION (since group has 3 months probation)
        assertEquals(MemberStatus.PROBATION, activatedMember.status)
        assertNotNull(activatedMember.joinedAt)
        assertNotNull(activatedMember.probationEndAt)
        
        // 5. Verify group member count incremented
        val updatedGroup = groupRepository.getGroupById(groupId).getOrThrow()
        assertTrue(updatedGroup.currentMembers >= 1)
        
        // 6. Verify contribution was recorded
        val result = memberRepository.getMemberContributions(activatedMember.id!!, groupId).first()
        val contributions = result.getOrThrow()
        assertTrue(contributions.any { it.yocoTransactionId == transactionId && it.amount == 150.0 })
    }

    @Test
    fun testIdempotentRegistration() = runBlocking {
        val adminUserId = ensureAuthenticated()
        val groupName = "Idempotent Test Group ${UUID.randomUUID().toString().take(8)}"
        val group = Group(
            name = groupName,
            type = GroupType.STOKVEL,
            province = "Gauteng",
            city = "Johannesburg",
            township = "Soweto",
            description = "Test Description",
            joiningFee = 100.0,
            probationMonths = 0,
            adminUserId = adminUserId,
            registrationPaid = true
        )
        val realGroupId = groupRepository.createGroup(group).getOrThrow()

        val userId = UUID.randomUUID().toString()
        val member = Member(
            groupId = realGroupId,
            userId = userId,
            fullName = "Idempotent User",
            idNumber = "8001015000081",
            phone = "0123456789",
            email = "idempotent@example.com",
            notificationPref = NotificationPref.EMAIL,
            status = MemberStatus.PENDING_PAYMENT
        )

        // First attempt
        memberRepository.registerMember(member, null).getOrThrow()
        
        // Second attempt - should throw "pending registration" error as per logic
        val result = memberRepository.registerMember(member, null)
        assertTrue(result.isFailure)
        assertEquals("You have a pending registration. Please complete payment.", result.exceptionOrNull()?.message)
        
        // Third attempt with payment - should succeed and activate
        val finalResult = memberRepository.registerMember(member, "tx_123").getOrThrow()
        assertEquals(MemberStatus.ACTIVE, finalResult.status)
    }
}
