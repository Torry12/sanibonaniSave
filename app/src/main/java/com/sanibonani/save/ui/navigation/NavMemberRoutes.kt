package com.sanibonani.save.ui.navigation

import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.sanibonani.save.domain.logging.AppLogger
import com.sanibonani.save.domain.model.Money
import com.sanibonani.save.features.member.presentation.MemberOverviewViewModel
import com.sanibonani.save.features.member.ui.MemberDashboardScreen

/** The member portal, including the entry point into support-mode admin view. */
internal fun NavGraphBuilder.memberRoutes(
    navController: NavHostController,
    windowSizeClass: WindowSizeClass,
    isPlatformAdmin: Boolean,
    onNavigatePayment: (type: String, amount: Money, groupId: String) -> Unit,
    onLogout: () -> Unit,
) {
    composable(
        route = Screen.MemberDashboard.route,
        deepLinks = listOf(
            navDeepLink { uriPattern = "sanibonani://member_dashboard?targetTab={targetTab}&groupId={groupId}" }
        ),
        arguments = listOf(
            navArgument("targetTab") { type = NavType.IntType; defaultValue = 0 },
            navArgument("groupId") { type = NavType.StringType; nullable = true; defaultValue = null },
            navArgument("memberId") { type = NavType.StringType; nullable = true; defaultValue = null },
            navArgument("supportMode") { type = NavType.BoolType; defaultValue = false }
        )
    ) { backStackEntry ->
        val targetTab = backStackEntry.arguments?.getInt("targetTab") ?: 0
        val groupId = backStackEntry.arguments?.getString("groupId")
        val memberId = backStackEntry.arguments?.getString("memberId")
        val supportMode = backStackEntry.arguments?.getBoolean("supportMode") ?: false
        val overviewVm = hiltViewModel<MemberOverviewViewModel>()

        val entryId = backStackEntry.id
        LaunchedEffect(entryId, groupId, memberId) {
            overviewVm.setPortalContext(memberId, groupId)
        }

        MemberDashboardScreen(
            targetTab = targetTab,
            windowSizeClass = windowSizeClass,
            onNavigatePayment = onNavigatePayment,
            onNavigateAdmin = {
                val gid = overviewVm.state.value.selectedGroupId
                if (gid.isNullOrBlank()) {
                    AppLogger.e(tag = "NavGraph", message = "[MemberDashboard] No active group to navigate to admin dashboard.")
                    return@MemberDashboardScreen
                }
                navController.navigateSingleTop(
                    Screen.AdminDashboard.withId(gid, supportMode = supportMode || isPlatformAdmin)
                ) {
                    popUpTo(Screen.MemberDashboard.route) { inclusive = true; saveState = true }
                }
            },
            onLogout = onLogout,
            onExitSupport = { navController.popBackStack() },
            overviewVm = overviewVm
        )
    }
}
