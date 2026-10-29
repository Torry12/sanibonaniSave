package com.sanibonani.save.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.sanibonani.save.R
import com.sanibonani.save.domain.logging.AppLogger
import com.sanibonani.save.domain.model.Money
import com.sanibonani.save.domain.model.UserRole
import com.sanibonani.save.domain.utils.toUserMessage
import com.sanibonani.save.features.auth.presentation.AuthViewModel
import com.sanibonani.save.features.common.presentation.GlobalErrorViewModel
import com.sanibonani.save.features.common.presentation.GlobalLoadingViewModel
import com.sanibonani.save.features.common.presentation.GlobalSyncViewModel
import com.sanibonani.save.service.UserProfileCacheService
import com.sanibonani.save.ui.components.GlobalErrorBanner
import com.sanibonani.save.ui.components.GlobalSyncBanner
import com.sanibonani.save.ui.components.SoftLoadingOverlay
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.delay

@EntryPoint
@InstallIn(SingletonComponent::class)
interface NavGraphEntryPoint {
    fun userProfileCacheService(): UserProfileCacheService
}

/**
 * Navigation host. Route registration lives in the per-feature `Nav*Routes` files; this file
 * owns the cross-cutting state: auth-driven redirects, the group-creation finish line, and the
 * global loading/sync/error overlays.
 */
@Composable
fun SanibonaniNavGraph(
    navController: NavHostController = rememberNavController(),
    windowSizeClass: WindowSizeClass
) {
    val context = LocalContext.current
    val userProfileCacheService = remember(context) {
        try {
            EntryPointAccessors
                .fromApplication(context.applicationContext, NavGraphEntryPoint::class.java)
                .userProfileCacheService()
        } catch (e: Exception) {
            AppLogger.e("NavGraph", "Hilt Entry Point not found. This might be a test environment without HiltAndroidRule.")
            null
        }
    }
    val authViewModel = hiltViewModel<AuthViewModel>()
    val authState by authViewModel.state.collectAsStateWithLifecycle()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val currentSupportMode = navBackStackEntry?.arguments?.getBoolean("supportMode") ?: false

    val errorViewModel = hiltViewModel<GlobalErrorViewModel>()
    val errorState by errorViewModel.errorState.collectAsStateWithLifecycle()
    val globalLoadingViewModel = hiltViewModel<GlobalLoadingViewModel>()
    val globalSpinnerActive by globalLoadingViewModel.showSpinner.collectAsStateWithLifecycle()
    val syncViewModel = hiltViewModel<GlobalSyncViewModel>()
    val syncStatus by syncViewModel.syncStatus.collectAsStateWithLifecycle()

    val onLogout: () -> Unit = {
        authViewModel.signOut {
            navController.navigateAndClearBackStack(Screen.Landing.route)
        }
    }
    val onNavigatePayment: (type: String, amount: Money, groupId: String) -> Unit = { type, amount, groupId ->
        navController.navigateSingleTop(Screen.Payment.build(type, amount.amount.toPlainString(), groupId))
    }

    // ── Consolidated Navigation Observer ─────────────────────────────────────────────
    LaunchedEffect(authState.isLoggedIn, authState.userRole, authState.isNewRegistration, currentRoute) {
        val route = currentRoute ?: return@LaunchedEffect
        if (!redirectThrottle.canRedirect(route)) return@LaunchedEffect

        val destination = RouteGuard.getRedirect(
            isLoggedIn = authState.isLoggedIn,
            role = authState.userRole,
            currentRoute = route,
            isNewRegistration = authState.isNewRegistration,
            isResettingPassword = authState.isResettingPassword,
            supportMode = currentSupportMode
        )

        if (destination != null) {
            navController.navigateSingleTop(destination) {
                popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
            }
            if (authState.isNewRegistration) {
                authViewModel.clearNewRegistrationFlag()
            }
        }
    }

    // Group creation finishes after the role has been elevated server-side, but the auth state
    // still holds the pre-creation role. Waiting for the refreshed role keeps the creator out of
    // a group-less dashboard that the route guard would immediately bounce them from.
    var createdGroupId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(createdGroupId, authState.userRole) {
        val groupId = createdGroupId ?: return@LaunchedEffect
        if (authState.userRole == UserRole.MEMBER) {
            // A failed or slow role lookup must not strand the user on the success screen;
            // fall through to the member dashboard still scoped to the new group.
            delay(3_000)
        }
        createdGroupId = null
        val destination = NavigationUtils.destinationAfterGroupCreation(authState.userRole, groupId)
        navController.navigateSingleTop(destination, restoreState = false) {
            popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = Screen.Landing.route
        ) {
            authRoutes(
                navController = navController,
                authState = authState,
                userProfileCacheService = userProfileCacheService
            )

            groupRoutes(
                navController = navController,
                authState = authState,
                onGroupCreated = { groupId ->
                    createdGroupId = groupId
                    authViewModel.refreshUserRole()
                }
            )

            memberRoutes(
                navController = navController,
                windowSizeClass = windowSizeClass,
                isPlatformAdmin = authState.userRole == UserRole.PLATFORM_ADMIN,
                onNavigatePayment = onNavigatePayment,
                onLogout = onLogout
            )

            adminRoutes(
                navController = navController,
                windowSizeClass = windowSizeClass,
                onNavigatePayment = onNavigatePayment,
                onLogout = onLogout
            )

            financialRoutes(
                navController = navController,
                onPaymentFailed = { errorViewModel.showError(it.toUserMessage()) }
            )
        }

        SoftLoadingOverlay(
            isLoading = globalSpinnerActive,
            message = stringResource(R.string.ui_loading)
        )

        GlobalSyncBanner(syncStatus)

        errorState.message?.let { msg ->
            Box(modifier = Modifier.align(Alignment.TopCenter)) {
                GlobalErrorBanner(
                    message = msg,
                    onDismiss = { errorViewModel.dismissError() },
                    isCritical = errorState.isCritical
                )
            }
        }
    }
}
