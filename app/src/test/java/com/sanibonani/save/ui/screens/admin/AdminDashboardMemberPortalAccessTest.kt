package com.sanibonani.save.ui.screens.admin

import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.Member
import com.sanibonani.save.viewmodel.AdminUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdminDashboardMemberPortalAccessTest {

    @Test
    fun `support mode requires selected member before opening member portal`() {
        val state = AdminUiState(
            group = Group(id = "group_1", name = "Test Group"),
            currentGroupId = "group_1"
        )

        val access = resolveAdminMemberPortalAccess(state, supportActive = true)

        assertFalse(access.enabled)
        assertEquals("group_1", access.groupId)
        assertNull(access.memberId)
        assertTrue(access.description.contains("select a member", ignoreCase = true))
    }

    @Test
    fun `support mode opens selected member portal when member is selected`() {
        val state = AdminUiState(
            group = Group(id = "group_1", name = "Test Group"),
            currentGroupId = "group_1",
            selectedMember = Member(id = "member_1", fullName = "Alice Member", groupId = "group_1")
        )

        val access = resolveAdminMemberPortalAccess(state, supportActive = true)

        assertTrue(access.enabled)
        assertEquals("group_1", access.groupId)
        assertEquals("member_1", access.memberId)
        assertTrue(access.title.contains("Alice Member"))
    }

    @Test
    fun `normal admin mode opens current user member portal without member selection`() {
        val state = AdminUiState(
            group = Group(id = "group_1", name = "Test Group"),
            currentGroupId = "group_1"
        )

        val access = resolveAdminMemberPortalAccess(state, supportActive = false)

        assertTrue(access.enabled)
        assertEquals("group_1", access.groupId)
        assertNull(access.memberId)
        assertEquals("Switch to Member View", access.title)
    }
}

