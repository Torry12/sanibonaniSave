package com.sanibonani.save.ui.navigation

import com.sanibonani.save.domain.model.UserRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavGraphRoleRoutingTest {

    @Test
    fun destinationForUserRole_platformAdmin_goesToPlatformPanel() {
        assertEquals(Screen.PlatformAdmin.route, destinationForUserRole(UserRole.PLATFORM_ADMIN))
    }

    @Test
    fun destinationForUserRole_groupAdmin_goesToAdminPanel() {
        assertEquals(Screen.AdminDashboard.withId(null), destinationForUserRole(UserRole.GROUP_ADMIN))
    }

    @Test
    fun destinationForUserRole_member_goesToMemberPanel() {
        assertEquals(Screen.MemberDashboard.withTab(0, null), destinationForUserRole(UserRole.MEMBER))
    }

    @Test
    fun destinationForRoleString_platformAdmin_goesToPlatformPanel() {
        assertEquals(Screen.PlatformAdmin.route, destinationForRoleString("platform_admin"))
    }

    @Test
    fun destinationForRoleString_groupAdmin_goesToAdminPanel() {
        assertEquals(Screen.AdminDashboard.withId(null), destinationForRoleString("group_admin"))
    }

    @Test
    fun destinationForRoleString_aliasesAreNormalized() {
        assertEquals(Screen.PlatformAdmin.route, destinationForRoleString(" platform-admin "))
        assertEquals(Screen.AdminDashboard.withId(null), destinationForRoleString("groupadmin"))
    }

    @Test
    fun destinationForRoleString_unknown_defaultsToMemberPanel() {
        assertEquals(Screen.MemberDashboard.withTab(0, null), destinationForRoleString("something_else"))
    }

    @Test
    fun destinationForPaymentType_registration_goesToAdminPanelForGroup() {
        assertEquals(
            Screen.AdminDashboard.withId("group_123"),
            destinationForPaymentType("registration", "group_123")
        )
    }

    @Test
    fun adminDashboardWithSupportMode_appendsSupportFlag() {
        assertEquals(
            "admin_dashboard?groupId=group_123&supportMode=true",
            Screen.AdminDashboard.withId("group_123", supportMode = true)
        )
    }

    @Test
    fun destinationForPaymentType_joiningFee_goesToMemberPanelForGroup() {
        assertEquals(
            Screen.MemberDashboard.withTab(0, "group_456"),
            destinationForPaymentType("joining_fee", "group_456")
        )
    }

    @Test
    fun destinationForPaymentType_unknown_defaultsToMemberPanelForGroup() {
        assertEquals(
            Screen.MemberDashboard.withTab(0, "group_789"),
            destinationForPaymentType("late_payment", "group_789")
        )
    }

    @Test
    fun shouldForcePlatformAdminRedirect_whenLoggedInAdminOnMemberRoute_returnsFalse() {
        assertFalse(
            shouldForcePlatformAdminRedirect(
                isLoggedIn = true,
                role = UserRole.PLATFORM_ADMIN,
                currentRoute = Screen.MemberDashboard.withTab(0),
                isResettingPassword = false
            )
        )
    }

    @Test
    fun shouldForcePlatformAdminRedirect_whenAlreadyOnPlatformRoute_returnsFalse() {
        assertFalse(
            shouldForcePlatformAdminRedirect(
                isLoggedIn = true,
                role = UserRole.PLATFORM_ADMIN,
                currentRoute = Screen.PlatformAdmin.route,
                isResettingPassword = false
            )
        )
    }

    @Test
    fun shouldRedirectAuthenticatedFromEntry_forAuthenticatedUserOnLogin_returnsTrue() {
        assertTrue(
            shouldRedirectAuthenticatedFromEntry(
                currentRoute = Screen.Login.route,
                navigateTo = null,
                isNewRegistration = false,
                isResettingPassword = false,
                role = UserRole.MEMBER
            )
        )
    }

    @Test
    fun shouldRedirectAuthenticatedFromEntry_platformAdminOnLanding_returnsTrue() {
        assertTrue(
            shouldRedirectAuthenticatedFromEntry(
                currentRoute = Screen.Landing.route,
                navigateTo = null,
                isNewRegistration = false,
                isResettingPassword = false,
                role = UserRole.PLATFORM_ADMIN
            )
        )
    }

    @Test
    fun shouldRedirectAuthenticatedFromEntry_whenNavigateToLogin_returnsFalse() {
        assertFalse(
            shouldRedirectAuthenticatedFromEntry(
                currentRoute = Screen.UpdatePassword.route,
                navigateTo = "login",
                isNewRegistration = false,
                isResettingPassword = false,
                role = UserRole.MEMBER
            )
        )
    }

    @Test
    fun shouldRedirectUnauthenticatedToLanding_onProtectedRoute_returnsTrue() {
        assertTrue(
            shouldRedirectUnauthenticatedToLanding(
                isLoggedIn = false,
                currentRoute = Screen.AdminDashboard.withId("group_1")
            )
        )
    }

    @Test
    fun shouldRedirectUnauthenticatedToLanding_onPublicRoute_returnsFalse() {
        assertFalse(
            shouldRedirectUnauthenticatedToLanding(
                isLoggedIn = false,
                currentRoute = Screen.BrowseGroups.route
            )
        )
    }

    @Test
    fun roleTransition_memberToPlatformAdmin_forcesRedirect_thenStabilizesOnPlatformRoute() {
        val memberRoute = Screen.MemberDashboard.withTab(0, null)

        // Before transition: member on member route should not be force-redirected.
        assertFalse(
            shouldForcePlatformAdminRedirect(
                isLoggedIn = true,
                role = UserRole.MEMBER,
                currentRoute = memberRoute,
                isResettingPassword = false
            )
        )

        // After transition: same non-entry route should not force redirect.
        assertFalse(
            shouldForcePlatformAdminRedirect(
                isLoggedIn = true,
                role = UserRole.PLATFORM_ADMIN,
                currentRoute = memberRoute,
                isResettingPassword = false
            )
        )
        // Force redirect still applies for auth entry routes.
        assertTrue(
            shouldForcePlatformAdminRedirect(
                isLoggedIn = true,
                role = UserRole.PLATFORM_ADMIN,
                currentRoute = Screen.Login.route,
                isResettingPassword = false
            )
        )
        assertEquals(Screen.PlatformAdmin.route, destinationForUserRole(UserRole.PLATFORM_ADMIN))

        // Once redirected to platform admin route: no further force redirect should occur.
        assertFalse(
            shouldForcePlatformAdminRedirect(
                isLoggedIn = true,
                role = UserRole.PLATFORM_ADMIN,
                currentRoute = Screen.PlatformAdmin.route,
                isResettingPassword = false
            )
        )
    }

    @Test
    fun isRoleAuthorizedForRoute_platformAdminRoute_allowsOnlyPlatformAdmin() {
        assertTrue(isRoleAuthorizedForRoute(UserRole.PLATFORM_ADMIN, Screen.PlatformAdmin.route))
        assertFalse(isRoleAuthorizedForRoute(UserRole.GROUP_ADMIN, Screen.PlatformAdmin.route))
        assertFalse(isRoleAuthorizedForRoute(UserRole.MEMBER, Screen.PlatformAdmin.route))
    }

    @Test
    fun isRoleAuthorizedForRoute_adminDashboard_allowsGroupAndPlatformAdmin() {
        val route = Screen.AdminDashboard.withId("group_1")
        assertTrue(isRoleAuthorizedForRoute(UserRole.GROUP_ADMIN, route))
        assertTrue(isRoleAuthorizedForRoute(UserRole.PLATFORM_ADMIN, route))
        assertFalse(isRoleAuthorizedForRoute(UserRole.MEMBER, route))
    }

    @Test
    fun isRoleAuthorizedForRoute_memberDashboard_allowsMemberAndPlatformAdmin() {
        val route = Screen.MemberDashboard.withTab(0, "group_1")
        assertTrue(isRoleAuthorizedForRoute(UserRole.MEMBER, route))
        assertFalse(isRoleAuthorizedForRoute(UserRole.GROUP_ADMIN, route))
        assertTrue(isRoleAuthorizedForRoute(UserRole.PLATFORM_ADMIN, route))
    }

    @Test
    fun shouldRedirectForRoleMismatch_whenRouteNotAllowed_returnsTrue() {
        assertTrue(
            shouldRedirectForRoleMismatch(
                isLoggedIn = true,
                role = UserRole.MEMBER,
                currentRoute = Screen.AdminDashboard.withId("group_1")
            )
        )
    }

    @Test
    fun shouldRedirectForRoleMismatch_whenRouteAllowed_returnsFalse() {
        assertFalse(
            shouldRedirectForRoleMismatch(
                isLoggedIn = true,
                role = UserRole.GROUP_ADMIN,
                currentRoute = Screen.AdminDashboard.withId("group_1")
            )
        )
    }
}
