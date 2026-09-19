package com.sanibonani.save.ui.navigation

import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavOptionsBuilder

/**
 * Helper function to handle navigation to a protected screen.
 * If the user is not logged in, they are redirected to the Login screen with the target route as a callback.
 */
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

/** Used for logout and post-auth flows — does NOT restore stale state. */
fun NavHostController.navigateAndClearBackStack(route: String) {
    navigateSingleTop(route, restoreState = false) {
        popUpTo(graph.findStartDestination().id) { inclusive = true }
    }
}

val navigationThrottle = object {
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

val redirectThrottle = object {
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
