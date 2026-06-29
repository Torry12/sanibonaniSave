package com.sanibonani.save.ui.navigation

import com.sanibonani.save.domain.model.UserRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavGraphRoleRoutingTest {

    @Test
    fun destinationForUserRole_platformAdmin_goesToPlatformPanel() {
        assertEquals(Screen.PlatformAdmin.route, NavigationUtils.destinationForUserRole(UserRole.PLATFORM_ADMIN))
    }

    @Test
    fun destinationForUserRole_groupAdmin_goesToAdminPanel() {
        assertEquals(Screen.AdminDashboard.withId(null), NavigationUtils.destinationForUserRole(UserRole.GROUP_ADMIN))
    }

    @Test
    fun destinationForUserRole_member_goesToMemberPanel() {
        assertEquals(Screen.MemberDashboard.withTab(0, null), NavigationUtils.destinationForUserRole(UserRole.MEMBER))
    }

    @Test
    fun destinationForPaymentType_registration_goesToAdminPanelForGroup() {
        assertEquals(
            Screen.AdminDashboard.withId("group_123"),
            NavigationUtils.destinationForPaymentType("registration", "group_123")
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
    fun memberDashboardWithSupportMode_appendsSupportFlag() {
        assertEquals(
            "member_dashboard?targetTab=0&groupId=group_123&memberId=member_123&supportMode=true",
            Screen.MemberDashboard.withTab(0, "group_123", "member_123", supportMode = true)
        )
    }

    @Test
    fun routeBuilders_encodeDynamicArguments() {
        val groupId = "group/a b?x=1&y=2"
        val memberId = "member/with space"

        assertEquals(
            "member_dashboard?targetTab=2&groupId=group%2Fa%20b%3Fx%3D1%26y%3D2&memberId=member%2Fwith%20space&supportMode=true",
            Screen.MemberDashboard.withTab(2, groupId, memberId, supportMode = true)
        )
        assertEquals(
            "admin_dashboard?groupId=group%2Fa%20b%3Fx%3D1%26y%3D2",
            Screen.AdminDashboard.withId(groupId)
        )
        assertEquals(
            "group/group%2Fa%20b%3Fx%3D1%26y%3D2",
            Screen.GroupProfile.withId(groupId)
        )
        assertEquals(
            "payment/joining%2Ffee/10%2F50/group%2Fa%20b%3Fx%3D1%26y%3D2",
            Screen.Payment.build("joining/fee", "10/50", groupId)
        )
    }

    @Test
    fun destinationForPaymentType_joiningFee_goesToMemberPanelForGroup() {
        assertEquals(
            Screen.MemberDashboard.withTab(0, "group_456"),
            NavigationUtils.destinationForPaymentType("joining_fee", "group_456")
        )
    }

    @Test
    fun destinationForPaymentType_unknown_defaultsToMemberPanelForGroup() {
        assertEquals(
            Screen.MemberDashboard.withTab(0, "group_789"),
            NavigationUtils.destinationForPaymentType("late_payment", "group_789")
        )
    }

    @Test
    fun shouldForcePlatformAdminRedirect_whenLoggedInAdminOnMemberRoute_returnsFalse() {
        assertFalse(
            NavigationUtils.shouldForcePlatformAdminRedirect(
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
            NavigationUtils.shouldForcePlatformAdminRedirect(
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
            NavigationUtils.shouldRedirectAuthenticatedFromEntry(
                currentRoute = Screen.Login.route,
                navigateTo = null,
                isNewRegistration = false,
                isResettingPassword = false
            )
        )
    }

    @Test
    fun shouldRedirectAuthenticatedFromEntry_platformAdminOnLanding_returnsTrue() {
        assertTrue(
            NavigationUtils.shouldRedirectAuthenticatedFromEntry(
                currentRoute = Screen.Landing.route,
                navigateTo = null,
                isNewRegistration = false,
                isResettingPassword = false
            )
        )
    }

    @Test
    fun shouldRedirectAuthenticatedFromEntry_whenNavigateToLogin_returnsFalse() {
        assertFalse(
            NavigationUtils.shouldRedirectAuthenticatedFromEntry(
                currentRoute = Screen.UpdatePassword.route,
                navigateTo = "login",
                isNewRegistration = false,
                isResettingPassword = false
            )
        )
    }

    @Test
    fun shouldRedirectUnauthenticatedToLanding_onProtectedRoute_returnsTrue() {
        assertTrue(
            NavigationUtils.shouldRedirectUnauthenticatedToLanding(
                isLoggedIn = false,
                currentRoute = Screen.AdminDashboard.withId("group_1")
            )
        )
    }

    @Test
    fun shouldRedirectUnauthenticatedToLanding_onPublicRoute_returnsFalse() {
        assertFalse(
            NavigationUtils.shouldRedirectUnauthenticatedToLanding(
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
            NavigationUtils.shouldForcePlatformAdminRedirect(
                isLoggedIn = true,
                role = UserRole.MEMBER,
                currentRoute = memberRoute,
                isResettingPassword = false
            )
        )

        // After transition: same non-entry route should not force redirect.
        assertFalse(
            NavigationUtils.shouldForcePlatformAdminRedirect(
                isLoggedIn = true,
                role = UserRole.PLATFORM_ADMIN,
                currentRoute = memberRoute,
                isResettingPassword = false
            )
        )
        // Force redirect still applies for auth entry routes.
        assertTrue(
            NavigationUtils.shouldForcePlatformAdminRedirect(
                isLoggedIn = true,
                role = UserRole.PLATFORM_ADMIN,
                currentRoute = Screen.Login.route,
                isResettingPassword = false
            )
        )
        assertEquals(Screen.PlatformAdmin.route, NavigationUtils.destinationForUserRole(UserRole.PLATFORM_ADMIN))

        // Once redirected to platform admin route: no further force redirect should occur.
        assertFalse(
            NavigationUtils.shouldForcePlatformAdminRedirect(
                isLoggedIn = true,
                role = UserRole.PLATFORM_ADMIN,
                currentRoute = Screen.PlatformAdmin.route,
                isResettingPassword = false
            )
        )
    }

    @Test
    fun isRoleAuthorizedForRoute_platformAdminRoute_allowsOnlyPlatformAdmin() {
        assertTrue(NavigationUtils.isRoleAuthorizedForRoute(UserRole.PLATFORM_ADMIN, Screen.PlatformAdmin.route))
        assertFalse(NavigationUtils.isRoleAuthorizedForRoute(UserRole.GROUP_ADMIN, Screen.PlatformAdmin.route))
        assertFalse(NavigationUtils.isRoleAuthorizedForRoute(UserRole.MEMBER, Screen.PlatformAdmin.route))
    }

    @Test
    fun isRoleAuthorizedForRoute_adminDashboard_requiresSupportModeForPlatformAdmin() {
        val route = Screen.AdminDashboard.withId("group_1")
        assertTrue(NavigationUtils.isRoleAuthorizedForRoute(UserRole.GROUP_ADMIN, route))
        assertFalse(NavigationUtils.isRoleAuthorizedForRoute(UserRole.PLATFORM_ADMIN, route))
        assertTrue(NavigationUtils.isRoleAuthorizedForRoute(UserRole.PLATFORM_ADMIN, route, supportMode = true))
        assertFalse(NavigationUtils.isRoleAuthorizedForRoute(UserRole.MEMBER, route))
    }

    @Test
    fun isRoleAuthorizedForRoute_memberDashboard_requiresSupportModeForPlatformAdmin() {
        val route = Screen.MemberDashboard.withTab(0, "group_1")
        assertTrue(NavigationUtils.isRoleAuthorizedForRoute(UserRole.MEMBER, route))
        assertFalse(NavigationUtils.isRoleAuthorizedForRoute(UserRole.GROUP_ADMIN, route))
        assertFalse(NavigationUtils.isRoleAuthorizedForRoute(UserRole.PLATFORM_ADMIN, route))
        assertTrue(NavigationUtils.isRoleAuthorizedForRoute(UserRole.PLATFORM_ADMIN, route, supportMode = true))
    }

    @Test
    fun shouldRedirectForRoleMismatch_platformAdminOnPlainMemberRoute_returnsTrue() {
        assertTrue(
            NavigationUtils.shouldRedirectForRoleMismatch(
                isLoggedIn = true,
                role = UserRole.PLATFORM_ADMIN,
                currentRoute = Screen.MemberDashboard.withTab(0, "group_1"),
                supportMode = false
            )
        )
    }

    @Test
    fun shouldRedirectForRoleMismatch_platformAdminOnSupportMemberRoute_returnsFalse() {
        assertFalse(
            NavigationUtils.shouldRedirectForRoleMismatch(
                isLoggedIn = true,
                role = UserRole.PLATFORM_ADMIN,
                currentRoute = Screen.MemberDashboard.withTab(0, "group_1", "member_1", supportMode = true),
                supportMode = true
            )
        )
    }

    @Test
    fun shouldRedirectForRoleMismatch_whenRouteNotAllowed_returnsTrue() {
        assertTrue(
            NavigationUtils.shouldRedirectForRoleMismatch(
                isLoggedIn = true,
                role = UserRole.MEMBER,
                currentRoute = Screen.AdminDashboard.withId("group_1")
            )
        )
    }

    @Test
    fun shouldRedirectForRoleMismatch_whenRouteAllowed_returnsFalse() {
        assertFalse(
            NavigationUtils.shouldRedirectForRoleMismatch(
                isLoggedIn = true,
                role = UserRole.GROUP_ADMIN,
                currentRoute = Screen.AdminDashboard.withId("group_1")
            )
        )
    }
}
