package com.sanibonani.save.ui.navigation

import androidx.compose.runtime.remember
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.sanibonani.save.domain.logging.AppLogger
import com.sanibonani.save.domain.model.GroupType
import com.sanibonani.save.features.auth.presentation.state.AuthState
import com.sanibonani.save.features.auth.ui.LoginScreen
import com.sanibonani.save.features.auth.ui.NewUserOnboardingScreen
import com.sanibonani.save.features.auth.ui.RegisterScreen
import com.sanibonani.save.features.auth.ui.PasswordRecoveryScreen
import com.sanibonani.save.features.auth.ui.UpdatePasswordScreen
import com.sanibonani.save.features.group.ui.BrowseGroupsScreen
import com.sanibonani.save.features.landing.ui.LandingScreen
import com.sanibonani.save.features.landing.presentation.LandingViewModel
import com.sanibonani.save.service.UserProfileCacheService

fun NavGraphBuilder.authRoutes(
    navController: NavHostController,
    authState: AuthState,
    userProfileCacheService: UserProfileCacheService?,
) {
    composable(Screen.Landing.route) {
        val landingViewModel: LandingViewModel = hiltViewModel()
        LandingScreen(
            onNavigateLogin = { navController.navigateSingleTop(Screen.Login.route) },
            onNavigateRegisterGroup = {
                navController.navigateProtected(Screen.RegisterGroup.build(), authState.isLoggedIn)
            },
            onNavigateBrowseGroups = { navController.navigateSingleTop(Screen.BrowseGroups.route) },
            onNavigateDashboard = {
                val dest = NavigationUtils.destinationForUserRole(authState.userRole)
                navController.navigateSingleTop(dest)
            },
            onNavigateMemberPortal = {
                navController.navigateSingleTop(Screen.MemberDashboard.withTab(0, null))
            },
            viewModel = landingViewModel
        )
    }

    composable(
        route = Screen.Login.route + "?redirect={redirect}",
        arguments = listOf(navArgument("redirect") {
            type = NavType.StringType
            nullable = true
            defaultValue = null
        })
    ) { backStackEntry ->
        val redirectRoute = backStackEntry.arguments?.getString("redirect")
        val decodedRedirect = remember(redirectRoute) {
            redirectRoute?.let { java.net.URLDecoder.decode(it, "UTF-8") }
        }
        LoginScreen(
            onLoginSuccess = { role ->
                if (decodedRedirect != null) {
                    AppLogger.d(tag = "NavGraph", message = "[Login] Applying protected redirect to $decodedRedirect.")
                    navController.navigateSingleTop(decodedRedirect) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                } else {
                    val destination = NavigationUtils.destinationForUserRole(role)
                    AppLogger.d(tag = "NavGraph", message = "[Login] Successful login. Routing to $destination for role $role.")
                    navController.navigateSingleTop(destination) {
                        popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                    }
                }
            },
            // "Register" must open account creation; NewUserOnboarding is the post-sign-up
            // choice screen and the guard keeps it out of reach until a session exists.
            onNavigateRegister = { navController.navigateSingleTop(Screen.Register.route) },
            onForgotPassword = { navController.navigateSingleTop(Screen.PasswordRecovery.route) }
        )
    }

    composable(Screen.PasswordRecovery.route) {
        PasswordRecoveryScreen(
            onBack = { navController.popBackStack() }
        )
    }

    composable(
        route = Screen.UpdatePassword.route,
        deepLinks = listOf(navDeepLink { uriPattern = "sanibonani://reset-password" })
    ) {
        UpdatePasswordScreen(
            onSuccess = { navController.navigateAndClearBackStack(Screen.Login.route) },
            onBack = { navController.popBackStack() }
        )
    }

    composable(Screen.Register.route) {
        RegisterScreen(
            onRegistered = { /* Handled by LaunchedEffect */ },
            onBack = { navController.popBackStack() }
        )
    }

    composable(
        route = Screen.NewUserOnboarding.route,
        arguments = listOf(navArgument("groupType") { type = NavType.StringType; nullable = true })
    ) { backStackEntry ->
        val groupTypeString = backStackEntry.arguments?.getString("groupType")
        // Deep links and restored back stacks can carry a name that is not (or no longer) an
        // enum constant; valueOf() would crash the composable on every recomposition.
        val groupType = GroupType.entries.firstOrNull { it.name == groupTypeString }
        NewUserOnboardingScreen(
            profileCache = userProfileCacheService,
            onRegisterGroup = { selectedType ->
                navController.navigateSingleTop(Screen.RegisterGroup.build(selectedType?.name)) {
                    popUpTo(Screen.NewUserOnboarding.baseRoute) { inclusive = true }
                }
            },
            onBrowseGroups = { navController.navigateSingleTop(Screen.BrowseGroups.route) { popUpTo(Screen.NewUserOnboarding.baseRoute) { inclusive = true } } },
            selectedGroupType = groupType
        )
    }

    composable(Screen.BrowseGroups.route) {
        BrowseGroupsScreen(
            onGroupClick = { id -> navController.navigateSingleTop(Screen.GroupProfile.withId(id)) },
            onRegisterGroup = {
                navController.navigateProtected(Screen.RegisterGroup.build(), authState.isLoggedIn)
            },
            onBack = { navController.popBackStack() }
        )
    }
}
