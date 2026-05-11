package com.sanibonani.save.integration

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sanibonani.save.domain.model.AdminFeeState
import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.GroupType
import com.sanibonani.save.domain.model.MemberStatus
import com.sanibonani.save.domain.model.NotificationPref
import com.sanibonani.save.domain.repository.GroupRepository
import com.sanibonani.save.domain.repository.MemberRepository
import com.sanibonani.save.domain.repository.SupabaseRepository
import com.sanibonani.save.domain.usecase.CreateGroupUseCase
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import javax.inject.Inject

/**
 * Integration tests for the complete group onboarding flow.
 * Tests that when a group is registered:
 * 1. Group is created with all details
 * 2. Admin is automatically added as first member with full info captured
 * 3. Group activation after payment works correctly
 * 4. Admin member status transitions properly
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class GroupOnboardingIntegrationTest {

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var groupRepository: GroupRepository

    @Inject
    lateinit var memberRepository: MemberRepository

    @Inject
    lateinit var supabaseRepo: SupabaseRepository

    @Inject
    lateinit var createGroupUseCase: CreateGroupUseCase

    @Before
    fun init() {
        hiltRule.inject()
    }

    private suspend fun ensureAuthenticated(): String {
        val email = "test-admin-${UUID.randomUUID()}@example.com"
        val password = "Password123!"
        supabaseRepo.adminSignUp(email, password, emptyMap(), confirm = true).getOrThrow()
        supabaseRepo.signIn(email, password).getOrThrow()
        return supabaseRepo.currentUserId ?: throw IllegalStateException("Failed to sign in")
    }

    @Test
    fun testRegisterGroupFlow() = runBlocking {
        // 1. Authenticate or create a new user
        val adminUserId = ensureAuthenticated()
        val adminEmail = supabaseRepo.currentSessionEmail ?: "test@example.com"

        // 2. Define a new group
        val groupName = "Onboarding Test Group ${UUID.randomUUID().toString().take(8)}"
        val group = Group(
            name = groupName,
            type = GroupType.STOKVEL,
            province = "Gauteng",
            city = "Johannesburg",
            township = "Soweto",
            description = "Test Description",
            joiningFee = 200.0,
            monthlyContribution = 500.0,
            probationMonths = 3,
            adminUserId = adminUserId
        )

        // 3. Create group (this should also create the admin as a member)
        val groupId = createGroupUseCase(
            group = group,
            adminEmail = adminEmail,
            adminFullName = "Integration Admin",
            adminPhone = "0123456789"
        ).getOrThrow()

        // 4. Verify group is created but not yet active (registrationPaid = false)
        val createdGroup = groupRepository.getGroupById(groupId).getOrThrow()
        assertFalse(createdGroup.registrationPaid)
        assertEquals(AdminFeeState.PENDING_ACTIVATION, createdGroup.feeStatus)

        // 5. Verify admin member exists with PENDING_PAYMENT status
        val membersResult = memberRepository.getGroupMembers(groupId).first()
        val members = membersResult.getOrThrow()
        val adminMember = members.find { it.userId == adminUserId }
        assertNotNull("Admin member should be created", adminMember)
        assertEquals("Admin should have PENDING_PAYMENT status before registration payment", MemberStatus.PENDING_PAYMENT, adminMember?.status)

        // 6. Finalize registration (simulate payment)
        val txId = "yoco_reg_${UUID.randomUUID()}"
        groupRepository.activateGroup(groupId, txId).getOrThrow()

        // 7. Verify group is now active
        val activatedGroup = groupRepository.getGroupById(groupId).getOrThrow()
        assertTrue(activatedGroup.registrationPaid)
        assertEquals(AdminFeeState.PAID, activatedGroup.feeStatus)

        // 8. Verify admin member is now PROBATION (since probationMonths > 0)
        // Note: activateGroup handles auto-crediting and status update for admin
        val updatedMembers = withTimeout(10_000) {
            memberRepository.getGroupMembers(groupId).first { result ->
                result.getOrNull()?.any { member ->
                    member.userId == adminUserId && member.status == MemberStatus.PROBATION
                } == true
            }
        }.getOrThrow()
        val updatedAdmin = updatedMembers.find { it.userId == adminUserId }!!
        assertEquals(MemberStatus.PROBATION, updatedAdmin.status)
        
        // 9. Verify admin joining fee was auto-credited
        val contributions = withTimeout(10_000) {
            memberRepository.getMemberContributions(updatedAdmin.id!!, groupId).first { result ->
                result.getOrNull()?.any { it.type == "joining_fee" && it.amount == 200.0 } == true
            }
        }.getOrThrow()
        assertTrue("Admin joining fee should be auto-credited", contributions.any { it.amount == 200.0 })
        
        // 10. Verify group balance reflects the credited contribution (joiningFee + monthlyContribution)
        // Group creation R700 fee credits the admin's first monthly contribution.
        // Joining fee is also auto-credited if > 0.
        val expectedBalance = (group.joiningFee ?: 0.0) + (group.monthlyContribution ?: 0.0)
        assertEquals(expectedBalance, activatedGroup.balance, 0.01)

        // 11. Verify admin has both joining fee and first contribution credited
        val finalContributions = withTimeout(10_000) {
            memberRepository.getMemberContributions(updatedAdmin.id!!, groupId).first { result ->
                val list = result.getOrNull() ?: return@first false
                list.any { it.type == "joining_fee" && it.amount == group.joiningFee } &&
                    list.any { it.type == "contribution" && it.amount == group.monthlyContribution }
            }
        }.getOrThrow()
        assertTrue("Admin joining fee should be auto-credited", finalContributions.any { it.type == "joining_fee" && it.amount == group.joiningFee })
        assertTrue("Admin first monthly contribution should be auto-credited", finalContributions.any { it.type == "contribution" && it.amount == group.monthlyContribution })
    }

    @Test
    fun testAdminOnboardingWithFullInfo() = runBlocking {
        // 1. Setup test data
        val adminEmail = "fulladmin-${UUID.randomUUID().toString().take(8)}@test.com"
        val adminPassword = "SecurePassword123!"
        val adminFullName = "Sipho Nkosi"
        val adminPhone = "0821234567"
        val adminIdNumber = "9001015800085"
        val adminStreet = "123 Main Street"
        val adminSuburb = "Sandton"

        // 2. Define group with all details
        val group = Group(
            name = "Full Onboarding Test ${UUID.randomUUID().toString().take(6)}",
            type = GroupType.BURIAL_SOCIETY,
            province = "Gauteng",
            city = "Johannesburg",
            township = "Sandton",
            description = "Complete onboarding test with all admin details",
            joiningFee = 300.0,
            monthlyContribution = 200.0,
            probationMonths = 3,
            maxBeneficiaries = 5,
            beneficiaryIncreasePct = 10.0,
            latitude = -26.1076,
            longitude = 28.0567,
            geohash = "ke7g0d6n2"
        )

        // 3. Create group with full admin info
        val groupId = createGroupUseCase(
            group = group,
            adminEmail = adminEmail,
            adminPassword = adminPassword,
            adminFullName = adminFullName,
            adminPhone = adminPhone,
            adminIdNumber = adminIdNumber,
            adminStreet = adminStreet,
            adminSuburb = adminSuburb,
            adminNotificationPref = NotificationPref.WHATSAPP
        ).getOrThrow()

        // 4. Verify group was created with geolocation
        val createdGroup = groupRepository.getGroupById(groupId).getOrThrow()
        assertNotNull("Group should have latitude", createdGroup.latitude)
        assertNotNull("Group should have longitude", createdGroup.longitude)
        assertEquals("Group type should be burial_society", GroupType.BURIAL_SOCIETY, createdGroup.type)
        assertEquals(5, createdGroup.maxBeneficiaries)
        assertEquals(10.0, createdGroup.beneficiaryIncreasePct ?: 0.0, 0.01)

        // 5. Verify admin member has all info captured
        val members = memberRepository.getGroupMembers(groupId).first().getOrThrow()
        val adminMember = members.firstOrNull()

        assertNotNull("Admin member should exist", adminMember)
        assertEquals(adminFullName, adminMember?.fullName)
        assertEquals(adminEmail, adminMember?.email)
        assertEquals(adminPhone, adminMember?.phone)
        assertEquals(adminIdNumber, adminMember?.idNumber)
        assertEquals(adminStreet, adminMember?.street)
        assertEquals(adminSuburb, adminMember?.suburb)
        assertEquals("Johannesburg", adminMember?.city)
        assertEquals("Gauteng", adminMember?.province)
        assertEquals(NotificationPref.WHATSAPP, adminMember?.notificationPref)
        assertEquals(MemberStatus.PENDING_PAYMENT, adminMember?.status)
        assertNotNull("Probation end date should be set", adminMember?.probationEndAt)
    }

    @Test
    fun testGroupGeolocationCapture() = runBlocking {
        // 1. Authenticate
        ensureAuthenticated()
        val adminEmail = supabaseRepo.currentSessionEmail ?: "test@example.com"

        // 2. Create group with geolocation
        val group = Group(
            name = "Geolocation Test ${UUID.randomUUID().toString().take(6)}",
            type = GroupType.STOKVEL,
            province = "Western Cape",
            city = "Cape Town",
            township = "Khayelitsha",
            latitude = -34.0043,
            longitude = 18.6509,
            geohash = "k3vz9q8m5"
        )

        val groupId = createGroupUseCase(
            group = group,
            adminEmail = adminEmail,
            adminFullName = "Geo Admin"
        ).getOrThrow()

        // 3. Verify geolocation is stored
        val createdGroup = groupRepository.getGroupById(groupId).getOrThrow()
        assertEquals(-34.0043, createdGroup.latitude ?: 0.0, 0.001)
        assertEquals(18.6509, createdGroup.longitude ?: 0.0, 0.001)
        assertEquals("k3vz9q8m5", createdGroup.geohash)
    }
}
