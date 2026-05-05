package com.sanibonani.save.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sanibonani.save.data.local.SanibonaniDatabase
import com.sanibonani.save.domain.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration tests for GroupRepository.
 * Tests group creation, activation, member registration, and multi-group queries.
 */
@RunWith(AndroidJUnit4::class)
class GroupRepositoryIntegrationTest {

    private lateinit var db: SanibonaniDatabase
    private lateinit var groupRepository: GroupRepository
    private lateinit var memberRepository: MemberRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, SanibonaniDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        // Initialize repositories with in-memory DB
        groupRepository = GroupRepositoryImpl(db = db)
        memberRepository = MemberRepositoryImpl(db = db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ══════════════════════════════════════════════════════════════════════════
    // GROUP CREATION TESTS
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `createGroup - burial society with settings`() = runBlocking {
        val group = Group(
            name = "Test Burial Society",
            type = GroupType.BURIAL_SOCIETY,
            monthlyContribution = 150.0,
            maxBeneficiaries = 5,
            beneficiaryIncreasePct = 10.0,
            joiningFee = 50.0
        )

        val created = groupRepository.createGroup(group)

        assertTrue("Group should be created successfully", created.isSuccess)
        assertNotNull("Group should have an ID", created.getOrNull()?.id)
        assertEquals("Name should match", "Test Burial Society", created.getOrNull()?.name)
    }

    @Test
    fun `createGroup - stokvel with specific settings`() = runBlocking {
        val group = Group(
            name = "Monthly Stokvel",
            type = GroupType.STOKVEL,
            monthlyContribution = 500.0,
            joiningFee = 100.0
        )

        val created = groupRepository.createGroup(group)

        assertTrue("Stokvel should be created", created.isSuccess)
        val createdGroup = created.getOrNull()!!
        assertEquals("Type should be STOKVEL", GroupType.STOKVEL, createdGroup.type)
    }

    @Test
    fun `createGroup - duplicate name allowed (different admin)`() = runBlocking {
        val group1 = Group(
            name = "Same Name Group",
            type = GroupType.BURIAL_SOCIETY,
            monthlyContribution = 100.0
        )

        val group2 = Group(
            name = "Same Name Group",
            type = GroupType.STOKVEL,
            monthlyContribution = 200.0
        )

        val created1 = groupRepository.createGroup(group1)
        val created2 = groupRepository.createGroup(group2)

        assertTrue("Both should be created", created1.isSuccess && created2.isSuccess)
        assertNotEquals("Should have different IDs", created1.getOrNull()?.id, created2.getOrNull()?.id)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // GROUP ACTIVATION & MEMBER AUTO-REGISTRATION TESTS
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `activateGroup - admin auto-registers as ACTIVE member`() = runBlocking {
        val adminId = "admin-123"
        val group = Group(
            id = "group-1",
            name = "Test Group",
            type = GroupType.BURIAL_SOCIETY,
            monthlyContribution = 150.0,
            createdBy = adminId
        )

        // Create group
        groupRepository.createGroup(group)

        // Activate group (mimics successful payment flow)
        groupRepository.activateGroup("group-1", adminId)

        // Verify admin is registered as member
        val members = memberRepository.getGroupMembers("group-1").first()
        val adminMember = members.firstOrNull { it.userId == adminId }

        assertNotNull("Admin should be registered as member", adminMember)
        assertEquals("Admin should be ACTIVE", MemberStatus.ACTIVE, adminMember?.status)
    }

    @Test
    fun `activateGroup - current_members count incremented`() = runBlocking {
        val group = Group(
            id = "group-1",
            name = "Test Group",
            type = GroupType.BURIAL_SOCIETY,
            monthlyContribution = 150.0,
            currentMembers = 0
        )

        groupRepository.createGroup(group)
        groupRepository.activateGroup("group-1", "admin-123")

        val updatedGroup = groupRepository.getGroup("group-1").first()
        assertEquals("Current members should be 1", 1, updatedGroup?.currentMembers)
    }

    @Test
    fun `activateGroup - joining fee credited to admin`() = runBlocking {
        val adminId = "admin-123"
        val group = Group(
            id = "group-1",
            name = "Test Group",
            type = GroupType.BURIAL_SOCIETY,
            monthlyContribution = 150.0,
            joiningFee = 100.0,
            createdBy = adminId
        )

        groupRepository.createGroup(group)
        groupRepository.activateGroup("group-1", adminId)

        // Verify joining fee contribution recorded
        val contributions = memberRepository.getMemberContributions(adminId, "group-1").first()
        val joiningFeeContrib = contributions.firstOrNull { it.type == "joining_fee" }

        assertNotNull("Joining fee should be recorded", joiningFeeContrib)
        assertEquals("Amount should equal joining fee", 100.0, joiningFeeContrib?.amount)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // GROUP UPDATE TESTS
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `updateGroup - settings updated successfully`() = runBlocking {
        val group = Group(
            id = "group-1",
            name = "Test Group",
            type = GroupType.BURIAL_SOCIETY,
            monthlyContribution = 150.0,
            maxBeneficiaries = 5
        )

        groupRepository.createGroup(group)

        // Update settings
        val updated = group.copy(
            monthlyContribution = 200.0,
            maxBeneficiaries = 7
        )
        groupRepository.updateGroup(updated)

        val fetched = groupRepository.getGroup("group-1").first()
        assertEquals("Contribution should be updated", 200.0, fetched?.monthlyContribution)
        assertEquals("Max beneficiaries should be updated", 7, fetched?.maxBeneficiaries)
    }

    @Test
    fun `updateGroup - status transition PENDING to ACTIVE`() = runBlocking {
        val group = Group(
            id = "group-1",
            name = "Test Group",
            type = GroupType.BURIAL_SOCIETY,
            monthlyContribution = 150.0,
            status = GroupStatus.PENDING_PAYMENT
        )

        groupRepository.createGroup(group)

        val updated = group.copy(status = GroupStatus.ACTIVE)
        groupRepository.updateGroup(updated)

        val fetched = groupRepository.getGroup("group-1").first()
        assertEquals("Status should be ACTIVE", GroupStatus.ACTIVE, fetched?.status)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // MULTI-GROUP MEMBER TESTS
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `getMemberGroups - returns all groups for member`() = runBlocking {
        val memberId = "member-123"

        // Create 3 groups
        val group1 = Group(id = "g1", name = "Group 1", type = GroupType.BURIAL_SOCIETY, monthlyContribution = 100.0)
        val group2 = Group(id = "g2", name = "Group 2", type = GroupType.STOKVEL, monthlyContribution = 200.0)
        val group3 = Group(id = "g3", name = "Group 3", type = GroupType.ROSCA, monthlyContribution = 300.0)

        groupRepository.createGroup(group1)
        groupRepository.createGroup(group2)
        groupRepository.createGroup(group3)

        // Register member in all groups
        memberRepository.registerMember(
            Member(id = "m1", userId = memberId, groupId = "g1", status = MemberStatus.ACTIVE)
        )
        memberRepository.registerMember(
            Member(id = "m2", userId = memberId, groupId = "g2", status = MemberStatus.ACTIVE)
        )
        memberRepository.registerMember(
            Member(id = "m3", userId = memberId, groupId = "g3", status = MemberStatus.ACTIVE)
        )

        // Fetch member groups
        val memberGroups = groupRepository.getMemberGroups(memberId).first()

        assertEquals("Member should be in 3 groups", 3, memberGroups.size)
        assertTrue("Should contain Group 1", memberGroups.any { it.id == "g1" })
        assertTrue("Should contain Group 2", memberGroups.any { it.id == "g2" })
        assertTrue("Should contain Group 3", memberGroups.any { it.id == "g3" })
    }

    @Test
    fun `getMemberGroups - excludes groups where member not active`() = runBlocking {
        val memberId = "member-123"

        val group1 = Group(id = "g1", name = "Group 1", type = GroupType.BURIAL_SOCIETY, monthlyContribution = 100.0)
        val group2 = Group(id = "g2", name = "Group 2", type = GroupType.STOKVEL, monthlyContribution = 200.0)

        groupRepository.createGroup(group1)
        groupRepository.createGroup(group2)

        // Register in g1 as ACTIVE, g2 as PENDING_PAYMENT
        memberRepository.registerMember(
            Member(id = "m1", userId = memberId, groupId = "g1", status = MemberStatus.ACTIVE)
        )
        memberRepository.registerMember(
            Member(id = "m2", userId = memberId, groupId = "g2", status = MemberStatus.PENDING_PAYMENT)
        )

        val memberGroups = groupRepository.getMemberGroups(memberId).first()

        assertEquals("Member should be in 1 ACTIVE group", 1, memberGroups.size)
        assertEquals("Should be Group 1", "g1", memberGroups.first().id)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // GROUP DISCOVERY TESTS
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `discoverGroups - returns all active groups`() = runBlocking {
        val group1 = Group(
            id = "g1",
            name = "Active Group 1",
            type = GroupType.BURIAL_SOCIETY,
            monthlyContribution = 100.0,
            status = GroupStatus.ACTIVE,
            latitude = -26.2023,
            longitude = 28.0436 // Johannesburg
        )
        val group2 = Group(
            id = "g2",
            name = "Active Group 2",
            type = GroupType.STOKVEL,
            monthlyContribution = 200.0,
            status = GroupStatus.ACTIVE,
            latitude = -33.9249,
            longitude = 18.4241 // Cape Town
        )
        val group3 = Group(
            id = "g3",
            name = "Inactive Group",
            type = GroupType.ROSCA,
            monthlyContribution = 300.0,
            status = GroupStatus.INACTIVE
        )

        groupRepository.createGroup(group1)
        groupRepository.createGroup(group2)
        groupRepository.createGroup(group3)

        val activeGroups = groupRepository.discoverGroups(GroupStatus.ACTIVE).first()

        assertEquals("Should return 2 active groups", 2, activeGroups.size)
        assertTrue("Should not include inactive group", activeGroups.none { it.id == "g3" })
    }

    @Test
    fun `discoverGroupsByType - filters by group type`() = runBlocking {
        groupRepository.createGroup(Group(id = "g1", name = "Burial 1", type = GroupType.BURIAL_SOCIETY, monthlyContribution = 100.0))
        groupRepository.createGroup(Group(id = "g2", name = "Burial 2", type = GroupType.BURIAL_SOCIETY, monthlyContribution = 150.0))
        groupRepository.createGroup(Group(id = "g3", name = "Stokvel 1", type = GroupType.STOKVEL, monthlyContribution = 200.0))

        val burialGroups = groupRepository.discoverGroupsByType(GroupType.BURIAL_SOCIETY).first()

        assertEquals("Should return 2 burial societies", 2, burialGroups.size)
        assertTrue("All should be burial societies", burialGroups.all { it.type == GroupType.BURIAL_SOCIETY })
    }

    @Test
    fun `discoverGroupsByProximity - returns groups near coordinates`() = runBlocking {
        groupRepository.createGroup(Group(
            id = "g1",
            name = "Johannesburg Group",
            type = GroupType.BURIAL_SOCIETY,
            monthlyContribution = 100.0,
            latitude = -26.2023,
            longitude = 28.0436
        ))
        groupRepository.createGroup(Group(
            id = "g2",
            name = "Far Group",
            type = GroupType.STOKVEL,
            monthlyContribution = 200.0,
            latitude = -45.0000,
            longitude = 170.0000 // Southern Ocean, far away
        ))

        // Query near Johannesburg
        val nearbyGroups = groupRepository.discoverGroupsByProximity(
            latitude = -26.2023,
            longitude = 28.0436,
            radiusKm = 50.0
        ).first()

        assertEquals("Should return nearby group", 1, nearbyGroups.size)
        assertEquals("Should be Johannesburg Group", "g1", nearbyGroups.first().id)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // GROUP STATISTICS TESTS
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `getGroupStats - returns correct member count`() = runBlocking {
        val group = Group(id = "g1", name = "Test", type = GroupType.BURIAL_SOCIETY, monthlyContribution = 100.0)
        groupRepository.createGroup(group)

        // Add members
        repeat(5) { i ->
            memberRepository.registerMember(
                Member(id = "m$i", userId = "user-$i", groupId = "g1", status = MemberStatus.ACTIVE)
            )
        }

        val stats = groupRepository.getGroupStats("g1")
        assertEquals("Member count should be 5", 5, stats?.memberCount)
    }

    @Test
    fun `getGroupStats - calculates total balance correctly`() = runBlocking {
        val group = Group(id = "g1", name = "Test", type = GroupType.BURIAL_SOCIETY, monthlyContribution = 100.0)
        groupRepository.createGroup(group)

        val updated = group.copy(currentBalance = 5000.0)
        groupRepository.updateGroup(updated)

        val stats = groupRepository.getGroupStats("g1")
        assertEquals("Balance should be 5000", 5000.0, stats?.totalBalance)
    }
}

