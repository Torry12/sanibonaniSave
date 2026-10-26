package com.sanibonani.save.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import com.sanibonani.save.domain.logging.AppLogger

/**
 * Reads a route argument every screen in this package requires. A deep link or a restored
 * back stack can arrive without it, so a missing value logs and pops instead of letting the
 * screen crash on a null id.
 */
@Composable
internal fun NavBackStackEntry.requiredStringArg(
    navController: NavHostController,
    key: String,
    screen: String,
): String? {
    val value = arguments?.getString(key)
    if (value.isNullOrBlank()) {
        AppLogger.e(tag = "NavGraph", message = "[$screen] Missing required $key argument. Popping back.")
        LaunchedEffect(Unit) { navController.popBackStack() }
        return null
    }
    return value
}

/** Logs a routing problem and leaves the destination instead of rendering a screen it cannot serve. */
@Composable
internal fun popBackWithError(navController: NavHostController, message: String) {
    AppLogger.e(tag = "NavGraph", message = message)
    LaunchedEffect(Unit) { navController.popBackStack() }
}
