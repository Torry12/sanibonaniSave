package com.sanibonani.save.integration

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sanibonani.save.data.local.*
import com.sanibonani.save.domain.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GroupRelationshipTest {

    private lateinit var db: SanibonaniDatabase
    private lateinit var groupDao: GroupDao
    private lateinit var memberDao: MemberDao
    private lateinit var userProfileDao: UserProfileDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, SanibonaniDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        groupDao = db.groupDao()
        memberDao = db.memberDao()
        userProfileDao = db.userProfileDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testGroupWithMembersRelation() = runBlocking {
        val groupId = "group-1"
        val group = Group(id = groupId, name = "Test Group", type = GroupType.STOKVEL)
        groupDao.upsertGroup(group.toEntity())

        val member1 = Member(id = "m1", groupId = groupId, fullName = "Member One", status = MemberStatus.ACTIVE)
        val member2 = Member(id = "m2", groupId = groupId, fullName = "Member Two", status = MemberStatus.ACTIVE)
        memberDao.upsertMembers(listOf(member1.toEntity(), member2.toEntity()))

        val groupWithMembers = groupDao.observeGroupWithMembers(groupId).first()

        assertNotNull("GroupWithMembers should not be null", groupWithMembers)
        assertEquals("Group name should match", "Test Group", groupWithMembers?.group?.name)
        assertEquals("Should have 2 members", 2, groupWithMembers?.members?.size)
        assertTrue("Should contain Member One", groupWithMembers!!.members.any { it.fullName == "Member One" })
        assertTrue("Should contain Member Two", groupWithMembers.members.any { it.fullName == "Member Two" })
    }

    @Test
    fun testMemberWithGroupRelation() = runBlocking {
        val groupId = "group-1"
        val group = Group(id = groupId, name = "Test Group", type = GroupType.STOKVEL)
        groupDao.upsertGroup(group.toEntity())

        val memberId = "m1"
        val member = Member(id = memberId, groupId = groupId, fullName = "Member One", status = MemberStatus.ACTIVE)
        memberDao.upsertMember(member.toEntity())

        val memberWithGroup = memberDao.observeMemberWithGroup(memberId).first()

        assertNotNull("MemberWithGroup should not be null", memberWithGroup)
        assertEquals("Member name should match", "Member One", memberWithGroup?.member?.fullName)
        assertEquals("Group name should match", "Test Group", memberWithGroup?.group?.name)
    }

    @Test
    fun testUserWithMembershipsRelation() = runBlocking {
        val userId = "user-123"
        val profile = UserProfile(id = userId, fullName = "John Doe", email = "john@example.com", role = "member")
        userProfileDao.upsertProfile(profile.toEntity())

        val groupId1 = "g1"
        val groupId2 = "g2"
        groupDao.upsertGroups(listOf(
            Group(id = groupId1, name = "Group 1").toEntity(),
            Group(id = groupId2, name = "Group 2").toEntity()
        ))

        memberDao.upsertMembers(listOf(
            Member(id = "m1", userId = userId, groupId = groupId1, fullName = "John in G1").toEntity(),
            Member(id = "m2", userId = userId, groupId = groupId2, fullName = "John in G2").toEntity()
        ))

        val userWithMemberships = userProfileDao.observeUserWithMemberships(userId).first()

        assertNotNull("UserWithMemberships should not be null", userWithMemberships)
        assertEquals("User name should match", "John Doe", userWithMemberships?.user?.fullName)
        assertEquals("Should have 2 memberships", 2, userWithMemberships?.memberships?.size)
    }
}
