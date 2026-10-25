package com.sanibonani.save.ui.navigation

import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavOptionsBuilder
import com.sanibonani.save.domain.model.UserRole

object RouteGuard {
    fun getRedirect(
        isLoggedIn: Boolean,
        role: UserRole,
        currentRoute: String,
        isNewRegistration: Boolean,
        isResettingPassword: Boolean,
        supportMode: Boolean
    ): String? {
        if (!isLoggedIn) {
            return if (currentRoute.isAuthOrPublicRoute()) null else Screen.Landing.route
        }

        if (isNewRegistration) {
            return if (role == UserRole.PLATFORM_ADMIN) Screen.PlatformAdmin.route else Screen.NewUserOnboarding.build()
        }

        if (role == UserRole.PLATFORM_ADMIN && currentRoute.isAuthEntryRoute() && !isResettingPassword) {
            return Screen.PlatformAdmin.route
        }

        if (!NavigationUtils.isRoleAuthorizedForRoute(role, currentRoute, supportMode)) {
            return NavigationUtils.destinationForUserRole(role)
        }

        // Authenticated user on Entry/Landing but not new registration/resetting password
        val isEntry = currentRoute.isAuthEntryRoute() || currentRoute == Screen.Landing.route
        if (isEntry && !isResettingPassword) {
            return NavigationUtils.destinationForUserRole(role)
        }

        return null
    }
}

object NavigationUtils {
    fun String?.isAuthOrPublicRoute(): Boolean {
        val route = this ?: return false
        // "register" has no arguments, so it must be matched exactly: startsWith() would
        // also classify "register_group?…" (the authenticated group wizard) as public.
        return route.startsWith(Screen.Landing.route) ||
                route.startsWith(Screen.Login.route) ||
                route == Screen.Register.route ||
                route.startsWith(Screen.NewUserOnboarding.baseRoute) ||
                route.startsWith(Screen.PasswordRecovery.route) ||
                route.startsWith(Screen.UpdatePassword.route) ||
                route.startsWith(Screen.BrowseGroups.route) ||
                route.startsWith("group/")
    }

    fun String?.isAuthEntryRoute(): Boolean {
        val route = this ?: return false
        return route.startsWith(Screen.Login.route) ||
                route == Screen.Register.route
    }

    fun destinationForUserRole(role: UserRole, groupId: String? = null): String {
        return when (role) {
            UserRole.PLATFORM_ADMIN -> Screen.PlatformAdmin.route
            UserRole.GROUP_ADMIN    -> Screen.AdminDashboard.withId(groupId)
            UserRole.MEMBER         -> Screen.MemberDashboard.withTab(0, groupId)
        }
    }

    fun destinationForPaymentType(type: String, groupId: String): String {
        return when (type) {
            "registration" -> Screen.AdminDashboard.withId(groupId)
            "joining_fee"  -> Screen.MemberDashboard.withTab(0, groupId)
            else           -> Screen.MemberDashboard.withTab(0, groupId)
        }
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

        if (route.startsWith(Screen.NewUserOnboarding.baseRoute)) {
            return role == UserRole.MEMBER || role == UserRole.GROUP_ADMIN
        }

        if (route.startsWith("member_dashboard")) {
            return role == UserRole.MEMBER || (role == UserRole.PLATFORM_ADMIN && supportMode)
        }

        if (route.startsWith("admin_dashboard")) {
            return role == UserRole.GROUP_ADMIN || (role == UserRole.PLATFORM_ADMIN && supportMode)
        }

        if (route.startsWith("health_score/")) return role == UserRole.GROUP_ADMIN || role == UserRole.PLATFORM_ADMIN
        if (route.startsWith("behavior_tracking/")) return role == UserRole.PLATFORM_ADMIN

        if (route.startsWith("group_voting")) {
            return role == UserRole.MEMBER || role == UserRole.GROUP_ADMIN || role == UserRole.PLATFORM_ADMIN
        }

        if (route.startsWith("payment/")) {
            return role == UserRole.MEMBER || role == UserRole.GROUP_ADMIN || role == UserRole.PLATFORM_ADMIN
        }

        if (route.startsWith(Screen.RegisterGroup.baseRoute) || route.startsWith("join/")) return true

        return false
    }
}

private fun String?.isAuthOrPublicRoute(): Boolean = NavigationUtils.run { this@isAuthOrPublicRoute.isAuthOrPublicRoute() }
private fun String?.isAuthEntryRoute(): Boolean = NavigationUtils.run { this@isAuthEntryRoute.isAuthEntryRoute() }

fun NavHostController.navigateSingleTop(
    route: String,
    restoreState: Boolean = true,
    builder: NavOptionsBuilder.() -> Unit = {}
) {
    if (!navigationThrottle.canNavigate(route)) return

    this.navigate(route) {
        launchSingleTop = true
        this.restoreState = restoreState
        builder()
    }
}

fun NavHostController.navigateAndClearBackStack(route: String) {
    navigateSingleTop(route, restoreState = false) {
        popUpTo(graph.findStartDestination().id) { inclusive = true }
    }
}

private val navigationThrottle = object {
    private val lock = Any()
    private var lastRoute: String? = null
    private var lastTime = 0L

    fun canNavigate(route: String): Boolean = synchronized(lock) {
        val now = System.currentTimeMillis()
        val same = lastRoute == route && (now - lastTime) < 500L
        if (!same) {
            lastRoute = route
            lastTime = now
        }
        !same
    }
}

object redirectThrottle {
    private val lock = Any()
    private var lastRoute: String? = null
    private var lastTime = 0L

    fun canRedirect(route: String): Boolean = synchronized(lock) {
        val now = System.currentTimeMillis()
        val same = lastRoute == route && (now - lastTime) < 1000L
        if (!same) {
            lastRoute = route
            lastTime = now
        }
        !same
    }
}

fun NavHostController.navigateProtected(route: String, isLoggedIn: Boolean) {
    if (isLoggedIn) {
        this.navigateSingleTop(route)
    } else {
        val encodedRoute = java.net.URLEncoder.encode(route, "UTF-8")
        this.navigateSingleTop("${Screen.Login.route}?redirect=$encodedRoute")
    }
}
