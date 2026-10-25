package com.sanibonani.save.ui.navigation

import com.sanibonani.save.domain.model.UserRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun routeGuard_unauthenticatedOnProtectedRoute_redirectsToLanding() {
        assertEquals(
            Screen.Landing.route,
            RouteGuard.getRedirect(
                isLoggedIn = false,
                role = UserRole.MEMBER,
                currentRoute = Screen.AdminDashboard.withId("g1"),
                isNewRegistration = false,
                isResettingPassword = false,
                supportMode = false
            )
        )
    }

    @Test
    fun routeGuard_platformAdminOnEntryRoute_redirectsToPlatformAdmin() {
        assertEquals(
            Screen.PlatformAdmin.route,
            RouteGuard.getRedirect(
                isLoggedIn = true,
                role = UserRole.PLATFORM_ADMIN,
                currentRoute = Screen.Login.route,
                isNewRegistration = false,
                isResettingPassword = false,
                supportMode = false
            )
        )
    }

    @Test
    fun routeGuard_memberOnAdminRoute_redirectsToMemberDashboard() {
        assertEquals(
            Screen.MemberDashboard.withTab(0, null),
            RouteGuard.getRedirect(
                isLoggedIn = true,
                role = UserRole.MEMBER,
                currentRoute = Screen.AdminDashboard.withId("g1"),
                isNewRegistration = false,
                isResettingPassword = false,
                supportMode = false
            )
        )
    }

    @Test
    fun routeGuard_platformAdminOnAdminRouteInSupportMode_noRedirect() {
        assertNull(
            RouteGuard.getRedirect(
                isLoggedIn = true,
                role = UserRole.PLATFORM_ADMIN,
                currentRoute = Screen.AdminDashboard.withId("g1"),
                isNewRegistration = false,
                isResettingPassword = false,
                supportMode = true
            )
        )
    }

    @Test
    fun routeGuard_newRegistration_redirectsToOnboarding() {
        assertEquals(
            Screen.NewUserOnboarding.build(),
            RouteGuard.getRedirect(
                isLoggedIn = true,
                role = UserRole.MEMBER,
                currentRoute = Screen.Landing.route,
                isNewRegistration = true,
                isResettingPassword = false,
                supportMode = false
            )
        )
    }

    @Test
    fun routeGuard_unauthenticatedOnRegister_noRedirect() {
        assertNull(
            RouteGuard.getRedirect(
                isLoggedIn = false,
                role = UserRole.MEMBER,
                currentRoute = Screen.Register.route,
                isNewRegistration = false,
                isResettingPassword = false,
                supportMode = false
            )
        )
    }

    @Test
    fun routeGuard_unauthenticatedOnGroupWizard_redirectsToLanding() {
        assertEquals(
            Screen.Landing.route,
            RouteGuard.getRedirect(
                isLoggedIn = false,
                role = UserRole.MEMBER,
                currentRoute = Screen.RegisterGroup.build("STOKVEL"),
                isNewRegistration = false,
                isResettingPassword = false,
                supportMode = false
            )
        )
    }

    @Test
    fun routeGuard_platformAdminOnGroupWizard_noRedirect() {
        assertNull(
            RouteGuard.getRedirect(
                isLoggedIn = true,
                role = UserRole.PLATFORM_ADMIN,
                currentRoute = Screen.RegisterGroup.build("STOKVEL"),
                isNewRegistration = false,
                isResettingPassword = false,
                supportMode = false
            )
        )
    }

    @Test
    fun routeGuard_groupAdminOnGroupWizard_noRedirect() {
        assertNull(
            RouteGuard.getRedirect(
                isLoggedIn = true,
                role = UserRole.GROUP_ADMIN,
                currentRoute = Screen.RegisterGroup.route,
                isNewRegistration = false,
                isResettingPassword = false,
                supportMode = false
            )
        )
    }

    // ── Group creation finish line ──────────────────────────────────────────
    // Routing on the pre-creation role sent a brand-new creator (still MEMBER in state)
    // to the member dashboard with no group context, and the role guard then bounced
    // every attempt to open the group they had just created.

    @Test
    fun groupCreationDestination_groupAdmin_opensTheCreatedGroup() {
        assertEquals(
            Screen.AdminDashboard.withId("group_123"),
            NavigationUtils.destinationAfterGroupCreation(UserRole.GROUP_ADMIN, "group_123")
        )
    }

    @Test
    fun groupCreationDestination_platformAdmin_opensTheCreatedGroupInSupportMode() {
        assertEquals(
            Screen.AdminDashboard.withId("group_123", supportMode = true),
            NavigationUtils.destinationAfterGroupCreation(UserRole.PLATFORM_ADMIN, "group_123")
        )
    }

    @Test
    fun groupCreationDestination_staleMemberRole_staysInTheCreatedGroupContext() {
        assertEquals(
            Screen.MemberDashboard.withTab(0, "group_123"),
            NavigationUtils.destinationAfterGroupCreation(UserRole.MEMBER, "group_123")
        )
    }
}
