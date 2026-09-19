package com.sanibonani.save.ui.navigation

import com.sanibonani.save.domain.model.UserRole
import com.sanibonani.save.domain.utils.UserRoleMapper
import java.net.URLEncoder

internal fun encodeRouteValue(value: String): String =
    URLEncoder.encode(value, "UTF-8").replace("+", "%20")

sealed class Screen(val route: String) {
    data object Landing             : Screen("landing")
    data object Login               : Screen("login")
    data object Register            : Screen("register")
    data object NewUserOnboarding   : Screen("new_user_onboarding")
    data object PasswordRecovery    : Screen("password_recovery")
    data object UpdatePassword      : Screen("update_password")
    data object BrowseGroups        : Screen("browse")
    data object RegisterGroup       : Screen("register_group")

    data object MemberDashboard : Screen("member_dashboard?targetTab={targetTab}&groupId={groupId}&memberId={memberId}&supportMode={supportMode}") {
        fun withTab(
            tab: Int,
            groupId: String? = null,
            memberId: String? = null,
            supportMode: Boolean = false
        ) =
            "member_dashboard?targetTab=$tab" +
                (if (groupId != null) "&groupId=${encodeRouteValue(groupId)}" else "") +
                (if (memberId != null) "&memberId=${encodeRouteValue(memberId)}" else "") +
                (if (supportMode) "&supportMode=true" else "")
    }

    data object AdminDashboard  : Screen("admin_dashboard?groupId={groupId}&supportMode={supportMode}") {
        fun withId(groupId: String? = null, supportMode: Boolean = false): String {
            val params = buildList {
                if (groupId != null) add("groupId=${encodeRouteValue(groupId)}")
                if (supportMode) add("supportMode=true")
            }
            return "admin_dashboard" + if (params.isEmpty()) "" else "?${params.joinToString("&")}"
        }
    }

    data object PlatformAdmin       : Screen("platform_admin")
    data object CreatePlatformAdmin : Screen("create_platform_admin")
    data object PaymentSandbox      : Screen("payment_sandbox")

    data object HealthScoreDetail : Screen("health_score/{groupId}") {
        fun withId(id: String) = "health_score/${encodeRouteValue(id)}"
    }

    data object GroupProfile : Screen("group/{groupId}") {
        fun withId(id: String) = "group/${encodeRouteValue(id)}"
    }

    data object RegisterMember : Screen("join/{groupId}") {
        fun withId(id: String) = "join/${encodeRouteValue(id)}"
    }

    data object GroupVoting : Screen("group_voting?groupId={groupId}&memberId={memberId}") {
        fun withParams(groupId: String, memberId: String? = null): String {
            return "group_voting?groupId=${encodeRouteValue(groupId)}" +
                (if (memberId != null) "&memberId=${encodeRouteValue(memberId)}" else "")
        }
    }

    data object Payment : Screen("payment/{type}/{amount}/{groupId}") {
        fun build(type: String, amount: String, groupId: String) =
            "payment/${encodeRouteValue(type)}/${encodeRouteValue(amount)}/${encodeRouteValue(groupId)}"
    }
}

object NavigationUtils {
    fun String?.isAuthOrPublicRoute(): Boolean {
        val route = this ?: return false
        return route.startsWith(Screen.Landing.route) ||
                route.startsWith(Screen.Login.route) ||
                route.startsWith(Screen.Register.route) ||
                route.startsWith(Screen.NewUserOnboarding.route) ||
                route.startsWith(Screen.PasswordRecovery.route) ||
                route.startsWith(Screen.UpdatePassword.route) ||
                route.startsWith(Screen.BrowseGroups.route) ||
                route.startsWith("group/")
    }

    fun String?.isAuthEntryRoute(): Boolean {
        val route = this ?: return false
        return route.startsWith(Screen.Login.route) ||
                route.startsWith(Screen.Register.route)
    }

    fun destinationForUserRole(role: UserRole, groupId: String? = null): String {
        return when (role) {
            UserRole.PLATFORM_ADMIN -> Screen.PlatformAdmin.route
            UserRole.GROUP_ADMIN    -> Screen.AdminDashboard.withId(groupId)
            UserRole.MEMBER         -> Screen.MemberDashboard.withTab(0, groupId)
        }
    }

    fun destinationForRoleString(role: String, groupId: String? = null): String {
        val resolvedRole = UserRoleMapper.fromRaw(role) ?: UserRole.MEMBER
        return destinationForUserRole(resolvedRole, groupId)
    }

    fun destinationForPaymentType(type: String, groupId: String): String {
        return when (type) {
            "registration" -> Screen.AdminDashboard.withId(groupId)
            "joining_fee"  -> Screen.MemberDashboard.withTab(0, groupId)
            else           -> Screen.MemberDashboard.withTab(0, groupId)
        }
    }

    fun shouldForcePlatformAdminRedirect(
        isLoggedIn: Boolean,
        role: UserRole,
        currentRoute: String?,
        isResettingPassword: Boolean
    ): Boolean {
        return isLoggedIn && role == UserRole.PLATFORM_ADMIN && currentRoute.isAuthEntryRoute() && !isResettingPassword
    }

    fun shouldRedirectAuthenticatedFromEntry(
        currentRoute: String?,
        navigateTo: String?,
        isNewRegistration: Boolean,
        isResettingPassword: Boolean
    ): Boolean {
        val isEntry = currentRoute.isAuthEntryRoute() || currentRoute == Screen.Landing.route
        return isEntry && navigateTo != "login" && !isNewRegistration && !isResettingPassword
    }

    fun shouldRedirectUnauthenticatedToLanding(
        isLoggedIn: Boolean,
        currentRoute: String?
    ): Boolean {
        return !isLoggedIn && !currentRoute.isAuthOrPublicRoute()
    }

    fun isRoleAuthorizedForRoute(
        role: UserRole,
        currentRoute: String?,
        supportMode: Boolean = false
    ): Boolean {
        val route = currentRoute ?: return true
        if (route.isAuthOrPublicRoute()) return true

        if (route == Screen.PlatformAdmin.route ||
            route == Screen.CreatePlatformAdmin.route ||
            route == Screen.PaymentSandbox.route
        ) return role == UserRole.PLATFORM_ADMIN

        if (route == Screen.NewUserOnboarding.route) return role == UserRole.MEMBER || role == UserRole.GROUP_ADMIN

        if (route.startsWith("member_dashboard")) {
            return role == UserRole.MEMBER || (role == UserRole.PLATFORM_ADMIN && supportMode)
        }

        if (route.startsWith("admin_dashboard")) {
            return role == UserRole.GROUP_ADMIN || (role == UserRole.PLATFORM_ADMIN && supportMode)
        }

        if (route.startsWith("health_score/")) return role == UserRole.GROUP_ADMIN || role == UserRole.PLATFORM_ADMIN

        if (route.startsWith("group_voting")) {
            return role == UserRole.MEMBER || role == UserRole.GROUP_ADMIN || role == UserRole.PLATFORM_ADMIN
        }

        if (route.startsWith("payment/")) {
            return role == UserRole.MEMBER || role == UserRole.GROUP_ADMIN || role == UserRole.PLATFORM_ADMIN
        }

        if (route.startsWith(Screen.RegisterGroup.route) || route.startsWith("join/")) return true

        return false
    }

    fun shouldRedirectForRoleMismatch(
        isLoggedIn: Boolean,
        role: UserRole,
        currentRoute: String?,
        supportMode: Boolean = false
    ): Boolean {
        return isLoggedIn && !isRoleAuthorizedForRoute(role, currentRoute, supportMode)
    }
}
